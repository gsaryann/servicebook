import os
import tempfile
import unittest
from unittest.mock import patch

os.environ.setdefault("INTERNAL_BOT_SECRET", "test-secret-that-is-long-enough-for-tests-123456")
from fastapi.testclient import TestClient
import main


class AuthApiTests(unittest.TestCase):
    def setUp(self):
        main.ip_request_counts.clear()
        self.tmp = tempfile.TemporaryDirectory()
        self.db_path = os.path.join(self.tmp.name, "auth.sqlite3")
        self.path_patcher = patch.object(main, "DB_PATH", main.Path(self.db_path))
        self.path_patcher.start()
        self.client = TestClient(main.app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.path_patcher.stop()
        self.tmp.cleanup()

    def create(self, phone="9991234567", mode="register", client_token=None):
        payload = {"phone": phone, "mode": mode}
        if client_token is not None:
            payload["client_token"] = client_token
        return self.client.post("/auth/requests", json=payload)

    def bot_approve(self, request_id, telegram_id=123, contact_user_id=123, contact_phone="+7 999 123-45-67", decision="approve"):
        return self.client.post(
            "/internal/auth/approve",
            headers={"X-Bot-Secret": main.BOT_SECRET},
            json={
                "request_id": request_id,
                "telegram_user_id": telegram_id,
                "contact_user_id": contact_user_id,
                "contact_phone": contact_phone,
                "decision": decision,
            },
        )

    def test_register_request_rejects_duplicate_phone(self):
        first = self.create()
        self.assertEqual(first.status_code, 200)
        self.assertEqual(self.bot_approve(first.json()["request_id"]).status_code, 200)
        self.assertEqual(self.create().status_code, 409)

    def test_first_login_links_existing_local_account_to_telegram(self):
        first_login = self.create(mode="login")
        self.assertEqual(first_login.status_code, 200)
        request_id = first_login.json()["request_id"]
        bot_request = self.client.get(
            f"/internal/auth/requests/{request_id}",
            headers={"X-Bot-Secret": main.BOT_SECRET},
        )
        self.assertEqual(bot_request.status_code, 200)
        self.assertEqual(bot_request.json()["mode"], "register")
        self.assertEqual(self.bot_approve(request_id).status_code, 200)

        next_login = self.create(mode="login")
        self.assertEqual(next_login.status_code, 200)
        next_request = self.client.get(
            f"/internal/auth/requests/{next_login.json()['request_id']}",
            headers={"X-Bot-Secret": main.BOT_SECRET},
        )
        self.assertEqual(next_request.json()["mode"], "login")
        self.assertEqual(next_request.json()["known_telegram_user_id"], 123)

    def test_registration_rejects_forwarded_contact(self):
        request_id = self.create().json()["request_id"]
        response = self.bot_approve(request_id, telegram_id=123, contact_user_id=456)
        self.assertEqual(response.status_code, 403)

    def test_registration_rejects_phone_mismatch(self):
        request_id = self.create().json()["request_id"]
        response = self.bot_approve(request_id, contact_phone="+7 999 000-00-00")
        self.assertEqual(response.status_code, 403)

    def test_login_requires_bound_telegram_user(self):
        register_id = self.create().json()["request_id"]
        self.assertEqual(self.bot_approve(register_id).status_code, 200)
        login_id = self.create(mode="login").json()["request_id"]
        response = self.bot_approve(login_id, telegram_id=456, contact_user_id=None, contact_phone=None)
        self.assertEqual(response.status_code, 403)

    def test_approved_status_is_repeatable_and_has_no_one_time_token(self):
        request_id = self.create().json()["request_id"]
        self.assertEqual(self.bot_approve(request_id).status_code, 200)
        first = self.client.get(f"/auth/requests/{request_id}")
        second = self.client.get(f"/auth/requests/{request_id}")
        self.assertEqual(first.status_code, 200)
        self.assertEqual(second.status_code, 200)
        self.assertEqual(first.json(), {"status": "approved"})
        self.assertEqual(second.json(), {"status": "approved"})

    def test_request_cannot_be_approved_twice(self):
        request_id = self.create().json()["request_id"]
        self.assertEqual(self.bot_approve(request_id).status_code, 200)
        self.assertEqual(self.bot_approve(request_id).status_code, 410)

    def test_expired_request_cannot_be_approved(self):
        request_id = self.create().json()["request_id"]
        with main.db() as conn:
            conn.execute("UPDATE auth_requests SET expires_at=0 WHERE request_id=?", (request_id,))
        self.assertEqual(self.bot_approve(request_id).status_code, 410)

    def test_internal_endpoints_require_bot_secret(self):
        request_id = self.create().json()["request_id"]
        self.assertEqual(self.client.get(f"/internal/auth/requests/{request_id}").status_code, 401)

    def test_cooldown_returns_existing_request_idempotently(self):
        phone = "9998887766"
        token = "client_token_123"
        first = self.create(phone=phone, client_token=token)
        self.assertEqual(first.status_code, 200)
        first_data = first.json()

        # Повторный запрос в течение 15 секунд с тем же токеном должен вернуть тот же request_id
        second = self.create(phone=phone, client_token=token)
        self.assertEqual(second.status_code, 200)
        second_data = second.json()
        self.assertEqual(first_data["request_id"], second_data["request_id"])
        self.assertEqual(first_data["bot_url"], second_data["bot_url"])

        # В БД должен быть ровно один pending-запрос для этого номера
        with main.db() as conn:
            pending_count = conn.execute(
                "SELECT COUNT(*) FROM auth_requests WHERE phone = ? AND status = 'pending'",
                (main.normalize_phone(phone),)
            ).fetchone()[0]
            self.assertEqual(pending_count, 1)

    def test_request_after_cooldown_expires_previous_pending_request(self):
        phone = "9997776655"
        normalized = main.normalize_phone(phone)
        first = self.create(phone=phone)
        first_id = first.json()["request_id"]

        # Имитируем прошествие 20 секунд (больше кулдауна в 15 с, но меньше TTL)
        current_time = int(main.time.time())
        with patch.object(main.time, "time", return_value=current_time + 20):
            second = self.create(phone=phone)
            self.assertEqual(second.status_code, 200)
            second_id = second.json()["request_id"]
            self.assertNotEqual(first_id, second_id)

        # Предыдущий запрос должен быть переведён в expired, активен только новый
        with main.db() as conn:
            first_status = conn.execute(
                "SELECT status FROM auth_requests WHERE request_id = ?", (first_id,)
            ).fetchone()["status"]
            self.assertEqual(first_status, "expired")

            second_status = conn.execute(
                "SELECT status FROM auth_requests WHERE request_id = ?", (second_id,)
            ).fetchone()["status"]
            self.assertEqual(second_status, "pending")

            active_count = conn.execute(
                "SELECT COUNT(*) FROM auth_requests WHERE phone = ? AND status = 'pending'",
                (normalized,)
            ).fetchone()[0]
            self.assertEqual(active_count, 1)

    def test_stale_records_are_purged_from_database(self):
        phone = "9995554433"
        normalized = main.normalize_phone(phone)
        stale_id = "stale_request_old_123"
        now = int(main.time.time())
        # Вставляем запись, истёкшую более часа назад (>3600 с)
        with main.db() as conn:
            conn.execute(
                "INSERT INTO auth_requests (request_id, phone, mode, status, created_at, expires_at) "
                "VALUES (?, ?, 'login', 'expired', ?, ?)",
                (stale_id, normalized, now - 4000, now - 3700)
            )

        # Создание нового запроса должно очистить устаревшие записи
        self.create()

        with main.db() as conn:
            row = conn.execute("SELECT 1 FROM auth_requests WHERE request_id = ?", (stale_id,)).fetchone()
            self.assertIsNone(row, "Устаревшая запись должна быть удалена из базы")

    def test_different_client_cannot_get_existing_request_id_within_cooldown(self):
        phone = "9994443322"
        alice_token = "alice_device_secret_token_12345"
        bob_token = "bob_device_secret_token_67890"

        alice_resp = self.create(phone=phone, client_token=alice_token)
        self.assertEqual(alice_resp.status_code, 200)
        alice_request_id = alice_resp.json()["request_id"]

        # Боб пытается получить запрос для номера Алисы в течение кулдауна
        bob_resp = self.create(phone=phone, client_token=bob_token)
        self.assertEqual(bob_resp.status_code, 429)
        self.assertNotIn("request_id", bob_resp.text)

        # Повторный запрос от Алисы с тем же токеном возвращает существующий request_id идемпотентно
        alice_repeat = self.create(phone=phone, client_token=alice_token)
        self.assertEqual(alice_repeat.status_code, 200)
        self.assertEqual(alice_repeat.json()["request_id"], alice_request_id)

    def test_missing_or_null_client_token_cannot_hijack_existing_request(self):
        phone = "9994443399"
        alice_resp = self.create(phone=phone, client_token="alice_secret_token_12345")
        self.assertEqual(alice_resp.status_code, 200)

        # Запрос без client_token (отсутствует) -> 429 без раскрытия request_id
        omitted_resp = self.create(phone=phone, client_token=None)
        self.assertEqual(omitted_resp.status_code, 429)
        self.assertNotIn("request_id", omitted_resp.text)

        # Запрос с явным client_token: null -> 429 без раскрытия request_id
        null_resp = self.client.post("/auth/requests", json={"phone": phone, "mode": "register", "client_token": None})
        self.assertEqual(null_resp.status_code, 429)
        self.assertNotIn("request_id", null_resp.text)

        # Если первый запрос был без client_token, повторный без client_token также получает 429 (fail-closed)
        legacy_phone = "9994443388"
        self.assertEqual(self.create(phone=legacy_phone, client_token=None).status_code, 200)
        legacy_repeat = self.create(phone=legacy_phone, client_token=None)
        self.assertEqual(legacy_repeat.status_code, 429)
        self.assertNotIn("request_id", legacy_repeat.text)

    def test_ip_rate_limiting_blocks_mass_creation_of_different_phones(self):
        # Очищаем трекер лимитов для чистоты теста
        main.ip_request_counts.clear()

        # Отправляем 10 запросов с разными номерами с одного IP
        for i in range(10):
            phone = f"999000{i:04d}"
            resp = self.create(phone=phone)
            self.assertEqual(resp.status_code, 200)

        # 11-й запрос с того же IP должен быть заблокирован по IP rate-limit
        blocked = self.create(phone="9990009999")
        self.assertEqual(blocked.status_code, 429)
        self.assertTrue("Слишком много запросов" in blocked.text)

    def test_invalid_client_token_rejected_with_422(self):
        phone = "9993331122"
        # Создаём активный запрос с валидным токеном
        valid = self.create(phone=phone, client_token="valid_client_token_123456")
        self.assertEqual(valid.status_code, 200)

        invalid_tokens = [
            "кириллица_токен_123456",  # non-ASCII (ранее вызывал TypeError 500)
            "",                       # пустая строка
            "short",                  # слишком короткий (< 16)
            "a" * 129,                # слишком длинный (> 128)
            "bad token with spaces!", # недопустимые символы
        ]
        for bad_token in invalid_tokens:
            resp = self.create(phone=phone, client_token=bad_token)
            self.assertEqual(resp.status_code, 422, f"Expected 422 for token {bad_token!r}, got {resp.status_code}")

    def test_stale_ip_entries_are_cleaned_from_rate_limiter(self):
        now = int(main.time.time())
        main.ip_request_counts["10.0.0.1"] = [now - 120]
        main.ip_request_counts["10.0.0.2"] = [now - 90]
        main.ip_request_counts["10.0.0.3"] = [now - 10]

        resp = self.create(phone="9992221100")
        self.assertEqual(resp.status_code, 200)

        self.assertNotIn("10.0.0.1", main.ip_request_counts)
        self.assertNotIn("10.0.0.2", main.ip_request_counts)
        self.assertIn("10.0.0.3", main.ip_request_counts)

    def test_sqlite_migration_adds_client_token_without_data_loss(self):
        import sqlite3
        legacy_db_path = os.path.join(self.tmp.name, "legacy.sqlite3")
        conn = sqlite3.connect(legacy_db_path)
        conn.executescript(
            """
            CREATE TABLE users (
                phone TEXT PRIMARY KEY,
                telegram_user_id INTEGER NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE auth_requests (
                request_id TEXT PRIMARY KEY,
                phone TEXT NOT NULL,
                mode TEXT NOT NULL CHECK(mode IN ('register', 'login')),
                status TEXT NOT NULL CHECK(status IN ('pending', 'approved', 'rejected', 'expired')),
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                telegram_user_id INTEGER
            );
            INSERT INTO users (phone, telegram_user_id, created_at) VALUES ('9991112233', 777, 1700000000);
            INSERT INTO auth_requests (request_id, phone, mode, status, created_at, expires_at)
                VALUES ('legacy_req_123456789012345', '9991112233', 'login', 'pending', 1700000000, 1700000300);
            """
        )
        conn.commit()
        conn.close()

        with patch.object(main, "DB_PATH", main.Path(legacy_db_path)):
            main.init_db()
            with main.db() as migrated:
                cols = [r["name"] for r in migrated.execute("PRAGMA table_info(auth_requests)").fetchall()]
                self.assertIn("client_token", cols)
                user = migrated.execute("SELECT * FROM users WHERE phone = '9991112233'").fetchone()
                self.assertIsNotNone(user)
                self.assertEqual(user["telegram_user_id"], 777)
                req = migrated.execute("SELECT * FROM auth_requests WHERE request_id = 'legacy_req_123456789012345'").fetchone()
                self.assertIsNotNone(req)
                self.assertIsNone(req["client_token"])


if __name__ == "__main__":
    unittest.main()
