import hashlib
import shutil
import sqlite3
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from fastapi.testclient import TestClient

import main


def _setup_env(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "DB_PATH", tmp_path / "test_delete.sqlite3")
    monkeypatch.setattr(main, "USER_FILES_DIR", tmp_path / "user_files")
    monkeypatch.setattr(main, "BOT_SECRET", "t" * 40)
    main.ip_request_counts.clear()
    main.init_db()
    return TestClient(main.app)


def _register_and_get_token(client: TestClient, phone: str, tg_id: int) -> tuple[str, str]:
    client_token = f"client_tok_{tg_id}_abcdefghij"
    created = client.post(
        "/auth/requests",
        json={"phone": phone, "mode": "register", "client_token": client_token},
    )
    assert created.status_code == 200
    req_id = created.json()["request_id"]
    approved = client.post(
        "/internal/auth/approve",
        headers={"X-Bot-Secret": "t" * 40},
        json={
            "request_id": req_id,
            "telegram_user_id": tg_id,
            "contact_user_id": tg_id,
            "contact_phone": phone,
            "decision": "approve",
        },
    )
    assert approved.status_code == 200
    status_resp = client.get(f"/auth/requests/{req_id}")
    assert status_resp.status_code == 200
    assert status_resp.json() == {"status": "approved"}

    sess_resp = client.post(
        "/api/v1/auth/session",
        json={"request_id": req_id, "client_token": client_token},
    )
    assert sess_resp.status_code == 200
    return sess_resp.json()["session_token"], req_id


def test_delete_account_with_files_and_reregister(tmp_path, monkeypatch):
    client = _setup_env(tmp_path, monkeypatch)
    token, _ = _register_and_get_token(client, "+79991112233", 1001)

    user_dir = (tmp_path / "user_files" / hashlib.sha256(b"9991112233").hexdigest())
    user_dir.mkdir(parents=True)
    (user_dir / "receipt.jpg").write_bytes(b"jpeg-bytes")

    resp = client.delete("/api/v1/account", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json() == {"status": "deleted"}
    assert not user_dir.exists()

    with sqlite3.connect(tmp_path / "test_delete.sqlite3") as conn:
        assert conn.execute("SELECT COUNT(*) FROM users WHERE phone = '9991112233'").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM sessions WHERE phone = '9991112233'").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM auth_requests WHERE phone = '9991112233'").fetchone()[0] == 0

    # Old token no longer works
    repeat = client.delete("/api/v1/account", headers={"Authorization": f"Bearer {token}"})
    assert repeat.status_code == 401

    # Re-registration with same phone & telegram_user_id succeeds cleanly
    new_token, _ = _register_and_get_token(client, "+79991112233", 1001)
    assert new_token != token


def test_delete_account_isolation_does_not_affect_other_user(tmp_path, monkeypatch):
    client = _setup_env(tmp_path, monkeypatch)
    token_a, _ = _register_and_get_token(client, "+79991110001", 2001)
    token_b, req_b = _register_and_get_token(client, "+79991110002", 2002)

    dir_b = tmp_path / "user_files" / hashlib.sha256(b"9991110002").hexdigest()
    dir_b.mkdir(parents=True)
    (dir_b / "doc.jpg").write_bytes(b"user-b-data")

    # Even if client sends another user_id/phone in query/body, only token_a's account is deleted
    resp = client.delete(
        "/api/v1/account?user_id=9991110002",
        headers={"Authorization": f"Bearer {token_a}"},
    )
    assert resp.status_code == 200

    # User B's DB rows, session, and files remain completely intact
    assert dir_b.exists() and (dir_b / "doc.jpg").read_bytes() == b"user-b-data"
    with sqlite3.connect(tmp_path / "test_delete.sqlite3") as conn:
        assert conn.execute("SELECT COUNT(*) FROM users WHERE phone = '9991110002'").fetchone()[0] == 1
        assert conn.execute("SELECT COUNT(*) FROM sessions WHERE phone = '9991110002'").fetchone()[0] >= 1
        assert conn.execute("SELECT COUNT(*) FROM auth_requests WHERE request_id = ?", (req_b,)).fetchone()[0] == 1

    # User B can still use their session
    resp_b = client.delete("/api/v1/account", headers={"Authorization": f"Bearer {token_b}"})
    assert resp_b.status_code == 200


def test_delete_account_rejects_missing_and_expired_token(tmp_path, monkeypatch):
    client = _setup_env(tmp_path, monkeypatch)
    assert client.delete("/api/v1/account").status_code == 401
    assert client.delete("/api/v1/account", headers={"Authorization": "Bearer short"}).status_code == 401

    token, _ = _register_and_get_token(client, "+79992223344", 3001)
    with sqlite3.connect(tmp_path / "test_delete.sqlite3") as conn:
        conn.execute("UPDATE sessions SET expires_at = ?", (int(time.time()) - 10,))
        conn.commit()

    assert client.delete("/api/v1/account", headers={"Authorization": f"Bearer {token}"}).status_code == 401


def test_delete_account_rolls_back_db_and_keeps_all_files_when_staging_fails(tmp_path, monkeypatch):
    client = _setup_env(tmp_path, monkeypatch)
    token, _ = _register_and_get_token(client, "+79993334455", 4001)

    user_dir = tmp_path / "user_files" / hashlib.sha256(b"9993334455").hexdigest()
    user_dir.mkdir(parents=True)
    (user_dir / "a.jpg").write_bytes(b"file-a")
    (user_dir / "b.jpg").write_bytes(b"file-b")

    def fail_stage(_phone: str):
        raise OSError("disk permission error")

    monkeypatch.setattr(main, "_stage_user_files_for_deletion", fail_stage)
    resp = client.delete("/api/v1/account", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 500

    # Transaction rolled back AND zero files were lost
    assert (user_dir / "a.jpg").read_bytes() == b"file-a"
    assert (user_dir / "b.jpg").read_bytes() == b"file-b"
    with sqlite3.connect(tmp_path / "test_delete.sqlite3") as conn:
        assert conn.execute("SELECT COUNT(*) FROM users WHERE phone = '9993334455'").fetchone()[0] == 1
        assert conn.execute("SELECT COUNT(*) FROM sessions WHERE phone = '9993334455'").fetchone()[0] >= 1


def test_delete_account_partial_trash_purge_and_retry(tmp_path, monkeypatch):
    client = _setup_env(tmp_path, monkeypatch)
    token, _ = _register_and_get_token(client, "+79994445566", 4002)

    user_dir = tmp_path / "user_files" / hashlib.sha256(b"9994445566").hexdigest()
    user_dir.mkdir(parents=True)
    (user_dir / "a.jpg").write_bytes(b"file-a")
    (user_dir / "b.jpg").write_bytes(b"file-b")

    real_rmtree = shutil.rmtree
    calls = {"count": 0}

    def flaky_rmtree(path, *args, **kwargs):
        calls["count"] += 1
        if calls["count"] == 1:
            raise OSError("transient lock on trash purge")
        return real_rmtree(path, *args, **kwargs)

    monkeypatch.setattr(main.shutil, "rmtree", flaky_rmtree)
    resp = client.delete("/api/v1/account", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert not user_dir.exists()

    # Staged files remain isolated in .trash and are cleaned up on next _purge_trash()
    trash_dir = tmp_path / "user_files" / ".trash"
    assert trash_dir.exists() and len(list(trash_dir.iterdir())) == 1
    main._purge_trash()
    assert len(list(trash_dir.iterdir())) == 0


def test_session_exchange_is_one_time_and_thread_safe(tmp_path, monkeypatch):
    client = _setup_env(tmp_path, monkeypatch)
    client_token = "client_tok_once_abcdefghij"
    created = client.post(
        "/auth/requests",
        json={"phone": "+79995556677", "mode": "register", "client_token": client_token},
    )
    assert created.status_code == 200
    req_id = created.json()["request_id"]
    approved = client.post(
        "/internal/auth/approve",
        headers={"X-Bot-Secret": "t" * 40},
        json={
            "request_id": req_id,
            "telegram_user_id": 5001,
            "contact_user_id": 5001,
            "contact_phone": "+79995556677",
            "decision": "approve",
        },
    )
    assert approved.status_code == 200

    # Wrong client_token -> 403
    wrong = client.post(
        "/api/v1/auth/session",
        json={"request_id": req_id, "client_token": "wrong_tok_once_abcdefghij"},
    )
    assert wrong.status_code == 403

    # Concurrent exchanges: exactly one succeeds (200), others get 409
    def do_exchange():
        return client.post(
            "/api/v1/auth/session",
            json={"request_id": req_id, "client_token": client_token},
        ).status_code

    with ThreadPoolExecutor(max_workers=4) as pool:
        statuses = list(pool.map(lambda _: do_exchange(), range(4)))
    assert statuses.count(200) == 1
    assert statuses.count(409) == 3

    # Subsequent replay -> 409
    replay = client.post(
        "/api/v1/auth/session",
        json={"request_id": req_id, "client_token": client_token},
    )
    assert replay.status_code == 409
