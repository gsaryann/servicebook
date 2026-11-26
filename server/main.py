"""Local ServiceBook Telegram authentication API for classroom demonstrations."""
from __future__ import annotations

import hashlib
import os
import secrets
import shutil
import sqlite3
import time
from contextlib import asynccontextmanager, contextmanager
from pathlib import Path
from typing import Iterator, Literal

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / ".env")

DB_PATH = Path(os.getenv("DATABASE_PATH", str(BASE_DIR / "servicebook_auth.sqlite3")))
USER_FILES_DIR = Path(os.getenv("USER_FILES_DIR", str(BASE_DIR / "user_files")))
BOT_SECRET = os.getenv("INTERNAL_BOT_SECRET", "").strip()
REQUEST_TTL = max(60, min(int(os.getenv("AUTH_REQUEST_TTL_SECONDS", "300")), 900))
SESSION_TTL = max(300, int(os.getenv("SESSION_TTL_SECONDS", str(30 * 24 * 3600))))
COOLDOWN_SECONDS = 15
STALE_REQUEST_PURGE_SECONDS = 3600
MAX_REQUESTS_PER_MINUTE_PER_IP = 10

ip_request_counts: dict[str, list[int]] = {}


@asynccontextmanager
async def lifespan(_: FastAPI):
    if len(BOT_SECRET) < 32:
        raise RuntimeError(
            "Set INTERNAL_BOT_SECRET to a random value of at least 32 characters in server/.env"
        )
    init_db()
    _purge_trash()
    yield


app = FastAPI(title="ServiceBook Auth API", version="1.1.0", lifespan=lifespan)


@contextmanager
def db() -> Iterator[sqlite3.Connection]:
    """Open a short-lived SQLite connection and commit only successful operations."""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    try:
        conn.execute("PRAGMA foreign_keys = ON")
        yield conn
    except Exception:
        conn.rollback()
        raise
    else:
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    with db() as conn:
        conn.execute("PRAGMA journal_mode = WAL")
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
                phone TEXT PRIMARY KEY,
                telegram_user_id INTEGER NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS auth_requests (
                request_id TEXT PRIMARY KEY,
                phone TEXT NOT NULL,
                mode TEXT NOT NULL CHECK(mode IN ('register', 'login')),
                status TEXT NOT NULL CHECK(status IN ('pending', 'approved', 'rejected', 'expired')),
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                telegram_user_id INTEGER,
                client_token TEXT,
                session_issued INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_auth_requests_expiry
                ON auth_requests(status, expires_at);
            CREATE TABLE IF NOT EXISTS sessions (
                token_hash TEXT PRIMARY KEY,
                phone TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                FOREIGN KEY(phone) REFERENCES users(phone) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_sessions_phone
                ON sessions(phone);
            """
        )
        columns = [row["name"] for row in conn.execute("PRAGMA table_info(auth_requests)").fetchall()]
        if "client_token" not in columns:
            conn.execute("ALTER TABLE auth_requests ADD COLUMN client_token TEXT")
        if "session_issued" not in columns:
            conn.execute("ALTER TABLE auth_requests ADD COLUMN session_issued INTEGER NOT NULL DEFAULT 0")


def normalize_phone(value: str) -> str:
    digits = "".join(ch for ch in value if ch.isdigit())
    if len(digits) == 11 and digits[0] in "78":
        digits = digits[1:]
    if len(digits) != 10:
        raise HTTPException(status_code=422, detail="Введите корректный номер телефона")
    return digits


def require_bot(x_bot_secret: str | None = Header(default=None)) -> None:
    if not x_bot_secret or not secrets.compare_digest(x_bot_secret, BOT_SECRET):
        raise HTTPException(status_code=401, detail="Unauthorized")


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _extract_bearer_token(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Требуется авторизация")
    token = authorization[7:].strip()
    if len(token) < 16:
        raise HTTPException(status_code=401, detail="Некорректный токен авторизации")
    return token


def _stage_user_files_for_deletion(phone: str) -> Path | None:
    """Atomically move user's server-side directory into .trash staging area."""
    base = USER_FILES_DIR.resolve()
    user_dir = (base / hashlib.sha256(phone.encode("utf-8")).hexdigest()).resolve()
    if user_dir.parent != base:
        raise RuntimeError("Invalid user files path")
    if not user_dir.exists():
        return None
    trash_root = (base / ".trash").resolve()
    trash_root.mkdir(parents=True, exist_ok=True)
    staged_dir = (trash_root / f"{user_dir.name}_{secrets.token_hex(8)}").resolve()
    if staged_dir.parent != trash_root:
        raise RuntimeError("Invalid staged path")
    os.replace(user_dir, staged_dir)
    return staged_dir


def _restore_staged_user_files(phone: str, staged_dir: Path | None) -> None:
    """Restore staged directory back to user_dir if DB transaction fails."""
    if staged_dir is None or not staged_dir.exists():
        return
    base = USER_FILES_DIR.resolve()
    user_dir = (base / hashlib.sha256(phone.encode("utf-8")).hexdigest()).resolve()
    if user_dir.parent != base:
        return
    if not user_dir.exists():
        os.replace(staged_dir, user_dir)


def _purge_trash() -> None:
    """Best-effort cleanup of staged .trash directories after DB commit."""
    trash_root = (USER_FILES_DIR.resolve() / ".trash").resolve()
    if not trash_root.exists():
        return
    for entry in list(trash_root.iterdir()):
        try:
            if entry.is_dir():
                shutil.rmtree(entry)
            else:
                entry.unlink()
        except OSError:
            # Remaining items stay isolated in .trash and will be retried on next purge.
            pass


class CreateAuthRequest(BaseModel):
    phone: str = Field(min_length=10, max_length=24)
    mode: Literal["register", "login"]
    client_token: str | None = Field(
        default=None,
        min_length=16,
        max_length=128,
        pattern=r"^[A-Za-z0-9_-]+$",
    )


class AuthRequestOut(BaseModel):
    request_id: str
    bot_url: str
    expires_in: int


class BotApprove(BaseModel):
    request_id: str = Field(min_length=20, max_length=64)
    telegram_user_id: int
    contact_user_id: int | None = None
    contact_phone: str | None = None
    decision: Literal["approve", "reject"] = "approve"


class BotReject(BaseModel):
    request_id: str = Field(min_length=20, max_length=64)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "servicebook-auth"}


@app.post("/auth/requests", response_model=AuthRequestOut)
def create_auth_request(payload: CreateAuthRequest, request: Request) -> AuthRequestOut:
    client_ip = request.client.host if request.client else "127.0.0.1"
    now = int(time.time())

    stale_ips = [
        ip for ip, ts_list in ip_request_counts.items()
        if not any(now - t < 60 for t in ts_list)
    ]
    for stale_ip in stale_ips:
        del ip_request_counts[stale_ip]

    history = [t for t in ip_request_counts.get(client_ip, []) if now - t < 60]
    if len(history) >= MAX_REQUESTS_PER_MINUTE_PER_IP:
        ip_request_counts[client_ip] = history
        raise HTTPException(
            status_code=429,
            detail="Слишком много запросов. Пожалуйста, подождите минуту перед повторной попыткой."
        )
    history.append(now)
    ip_request_counts[client_ip] = history

    phone = normalize_phone(payload.phone)
    request_id = secrets.token_urlsafe(24)

    with db() as conn:
        conn.execute("BEGIN IMMEDIATE")
        conn.execute(
            "DELETE FROM auth_requests WHERE expires_at < ?",
            (now - STALE_REQUEST_PURGE_SECONDS,),
        )
        conn.execute(
            "UPDATE auth_requests SET status='expired' "
            "WHERE status='pending' AND expires_at <= ?",
            (now,),
        )
        user = conn.execute(
            "SELECT telegram_user_id FROM users WHERE phone = ?", (phone,)
        ).fetchone()

        if payload.mode == "register" and user:
            raise HTTPException(status_code=409, detail="Этот номер уже зарегистрирован. Выберите вход.")

        active_request = conn.execute(
            "SELECT request_id, created_at, expires_at, client_token FROM auth_requests "
            "WHERE phone = ? AND status = 'pending' ORDER BY created_at DESC",
            (phone,),
        ).fetchone()

        if active_request:
            if now - active_request["created_at"] < COOLDOWN_SECONDS:
                is_same_client = bool(
                    payload.client_token
                    and active_request["client_token"]
                    and secrets.compare_digest(
                        payload.client_token.encode("utf-8"),
                        str(active_request["client_token"]).encode("utf-8"),
                    )
                )
                if is_same_client:
                    remaining_ttl = max(1, active_request["expires_at"] - now)
                    return AuthRequestOut(
                        request_id=active_request["request_id"],
                        bot_url=f"https://t.me/authservicebookbot?start=sb_{active_request['request_id']}",
                        expires_in=remaining_ttl,
                    )
                else:
                    raise HTTPException(
                        status_code=429,
                        detail="Запрос для этого номера уже отправлен. Подождите 15 секунд перед повторной попыткой.",
                    )
            else:
                conn.execute(
                    "UPDATE auth_requests SET status = 'expired' "
                    "WHERE phone = ? AND status = 'pending'",
                    (phone,),
                )

        # Existing demo accounts are stored locally on Android and have no server
        # Telegram binding yet. Their first login performs a one-time contact link.
        request_mode = "register" if payload.mode == "login" and not user else payload.mode

        conn.execute(
            """
            INSERT INTO auth_requests
                (request_id, phone, mode, status, created_at, expires_at, client_token)
            VALUES (?, ?, ?, 'pending', ?, ?, ?)
            """,
            (request_id, phone, request_mode, now, now + REQUEST_TTL, payload.client_token),
        )

    return AuthRequestOut(
        request_id=request_id,
        bot_url=f"https://t.me/authservicebookbot?start=sb_{request_id}",
        expires_in=REQUEST_TTL,
    )


@app.get("/auth/requests/{request_id}")
def get_auth_status(request_id: str) -> dict[str, str]:
    with db() as conn:
        conn.execute("BEGIN IMMEDIATE")
        row = conn.execute(
            "SELECT status, expires_at FROM auth_requests WHERE request_id = ?",
            (request_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Запрос не найден")

        status = row["status"]
        if status == "pending" and row["expires_at"] <= int(time.time()):
            conn.execute(
                "UPDATE auth_requests SET status='expired' "
                "WHERE request_id=? AND status='pending'",
                (request_id,),
            )
            status = "expired"

    return {"status": status}


class SessionExchangeRequest(BaseModel):
    request_id: str = Field(min_length=20, max_length=64)
    client_token: str = Field(
        min_length=16,
        max_length=128,
        pattern=r"^[A-Za-z0-9_-]+$",
    )


@app.post("/api/v1/auth/session")
def create_session_from_auth_request(payload: SessionExchangeRequest) -> dict[str, str]:
    now = int(time.time())
    with db() as conn:
        conn.execute("BEGIN IMMEDIATE")
        row = conn.execute(
            "SELECT phone, status, client_token, session_issued FROM auth_requests WHERE request_id = ?",
            (payload.request_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Запрос не найден")
        if row["status"] != "approved":
            raise HTTPException(status_code=409, detail="Запрос не подтверждён")

        stored_ct = row["client_token"]
        if not stored_ct or not secrets.compare_digest(
            payload.client_token.encode("utf-8"),
            str(stored_ct).encode("utf-8"),
        ):
            raise HTTPException(status_code=403, detail="Недействительный токен клиента")

        if row["session_issued"]:
            raise HTTPException(status_code=409, detail="Сессия для этого запроса уже выдана")

        user = conn.execute("SELECT 1 FROM users WHERE phone = ?", (row["phone"],)).fetchone()
        if not user:
            raise HTTPException(status_code=404, detail="Аккаунт не найден")

        changed = conn.execute(
            """
            UPDATE auth_requests
            SET session_issued = 1
            WHERE request_id = ? AND status = 'approved' AND session_issued = 0
            """,
            (payload.request_id,),
        ).rowcount
        if changed != 1:
            raise HTTPException(status_code=409, detail="Сессия для этого запроса уже выдана")

        session_token = secrets.token_urlsafe(32)
        conn.execute(
            "INSERT INTO sessions(token_hash, phone, created_at, expires_at) VALUES (?, ?, ?, ?)",
            (_hash_token(session_token), row["phone"], now, now + SESSION_TTL),
        )
    return {"session_token": session_token}


@app.get("/internal/auth/requests/{request_id}", dependencies=[Depends(require_bot)])
def get_request_for_bot(request_id: str) -> dict[str, int | str | None]:
    with db() as conn:
        conn.execute("BEGIN IMMEDIATE")
        row = conn.execute(
            """
            SELECT request_id, phone, mode, status, expires_at
            FROM auth_requests WHERE request_id = ?
            """,
            (request_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Запрос не найден")
        if row["status"] != "pending" or row["expires_at"] <= int(time.time()):
            if row["status"] == "pending":
                conn.execute(
                    "UPDATE auth_requests SET status='expired' WHERE request_id=?",
                    (request_id,),
                )
            raise HTTPException(status_code=410, detail="Запрос истёк или уже обработан")

        user = conn.execute(
            "SELECT telegram_user_id FROM users WHERE phone = ?", (row["phone"],)
        ).fetchone()
        return {
            "request_id": row["request_id"],
            "phone": row["phone"],
            "mode": row["mode"],
            "known_telegram_user_id": user["telegram_user_id"] if user else None,
            "expires_at": row["expires_at"],
        }


@app.post("/internal/auth/approve", dependencies=[Depends(require_bot)])
def approve_request(payload: BotApprove) -> dict[str, str]:
    now = int(time.time())
    with db() as conn:
        conn.execute("BEGIN IMMEDIATE")
        row = conn.execute(
            "SELECT phone, mode, status, expires_at FROM auth_requests WHERE request_id = ?",
            (payload.request_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Запрос не найден")
        if row["status"] != "pending" or row["expires_at"] <= now:
            if row["status"] == "pending":
                conn.execute(
                    "UPDATE auth_requests SET status='expired' WHERE request_id=?",
                    (payload.request_id,),
                )
            raise HTTPException(status_code=410, detail="Запрос истёк или уже обработан")

        if payload.decision == "reject":
            conn.execute(
                "UPDATE auth_requests SET status='rejected' "
                "WHERE request_id=? AND status='pending'",
                (payload.request_id,),
            )
            return {"status": "rejected"}

        user = conn.execute(
            "SELECT telegram_user_id FROM users WHERE phone = ?", (row["phone"],)
        ).fetchone()

        if row["mode"] == "register":
            if payload.contact_user_id != payload.telegram_user_id or not payload.contact_phone:
                raise HTTPException(
                    status_code=403,
                    detail="Поделитесь собственным контактом Telegram",
                )
            if normalize_phone(payload.contact_phone) != row["phone"]:
                raise HTTPException(
                    status_code=403,
                    detail="Номер Telegram не совпадает с номером регистрации",
                )
            if user:
                raise HTTPException(status_code=409, detail="Номер уже зарегистрирован")
            try:
                conn.execute(
                    "INSERT INTO users(phone, telegram_user_id, created_at) VALUES (?, ?, ?)",
                    (row["phone"], payload.telegram_user_id, now),
                )
            except sqlite3.IntegrityError:
                raise HTTPException(
                    status_code=409,
                    detail="Этот Telegram-аккаунт уже привязан к другому номеру",
                )
        else:
            if not user or user["telegram_user_id"] != payload.telegram_user_id:
                raise HTTPException(
                    status_code=403,
                    detail="Telegram-аккаунт не привязан к этому номеру",
                )

        changed = conn.execute(
            """
            UPDATE auth_requests
            SET status='approved', telegram_user_id=?
            WHERE request_id=? AND status='pending' AND expires_at>?
            """,
            (payload.telegram_user_id, payload.request_id, now),
        ).rowcount
        if changed != 1:
            raise HTTPException(status_code=410, detail="Запрос истёк или уже обработан")

    return {"status": "approved"}


@app.post("/internal/auth/reject", dependencies=[Depends(require_bot)])
def reject_request(payload: BotReject) -> dict[str, str]:
    now = int(time.time())
    with db() as conn:
        conn.execute("BEGIN IMMEDIATE")
        changed = conn.execute(
            """
            UPDATE auth_requests SET status='rejected'
            WHERE request_id=? AND status='pending' AND expires_at>?
            """,
            (payload.request_id, now),
        ).rowcount
        if changed != 1:
            raise HTTPException(status_code=410, detail="Запрос истёк или уже обработан")
    return {"status": "rejected"}


@app.delete("/api/v1/account")
def delete_account(authorization: str | None = Header(default=None)) -> dict[str, str]:
    token = _extract_bearer_token(authorization)
    token_hash = _hash_token(token)
    now = int(time.time())
    phone: str | None = None
    staged_dir: Path | None = None

    try:
        with db() as conn:
            conn.execute("BEGIN IMMEDIATE")
            conn.execute("DELETE FROM sessions WHERE expires_at <= ?", (now,))
            session = conn.execute(
                "SELECT phone, expires_at FROM sessions WHERE token_hash = ?",
                (token_hash,),
            ).fetchone()
            if not session or session["expires_at"] <= now:
                raise HTTPException(status_code=401, detail="Сессия недействительна или истекла")

            phone = session["phone"]
            user = conn.execute(
                "SELECT telegram_user_id FROM users WHERE phone = ?",
                (phone,),
            ).fetchone()
            if not user:
                conn.execute("DELETE FROM sessions WHERE token_hash = ?", (token_hash,))
                raise HTTPException(status_code=401, detail="Аккаунт не найден")

            telegram_user_id = user["telegram_user_id"]
            conn.execute("DELETE FROM sessions WHERE phone = ?", (phone,))
            conn.execute(
                "DELETE FROM auth_requests WHERE phone = ? OR telegram_user_id = ?",
                (phone, telegram_user_id),
            )
            conn.execute("DELETE FROM users WHERE phone = ?", (phone,))

            try:
                staged_dir = _stage_user_files_for_deletion(phone)
            except OSError as exc:
                raise HTTPException(
                    status_code=500,
                    detail="Не удалось подготовить файлы пользователя к удалению. Повторите попытку.",
                ) from exc
    except HTTPException:
        if phone is not None and staged_dir is not None:
            try:
                _restore_staged_user_files(phone, staged_dir)
            except OSError:
                pass
        raise
    except Exception as exc:
        if phone is not None and staged_dir is not None:
            try:
                _restore_staged_user_files(phone, staged_dir)
            except OSError:
                pass
        raise HTTPException(status_code=500, detail="Ошибка транзакции базы данных") from exc

    # DB transaction is committed. Best-effort purge of staged .trash directory.
    _purge_trash()
    return {"status": "deleted"}
