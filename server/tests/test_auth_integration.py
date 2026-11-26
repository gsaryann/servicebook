"""
Интеграционные тесты бэкенда ServiceBook:
Проверяют совместную работу компонентов FastAPI Auth Server, локальной базы данных SQLite
и логики Telegram-бота (ServiceBookBot).

Внешний сетевой протокол Telegram API изолирован моком (fake_telegram_request),
а вызовы Android-клиента осуществляются через TestClient.
Это интеграционные тесты бэкенда (Backend Integration Tests), а не сквозные E2E тесты Android UI.
"""
import json
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

# Configure test environment
os.environ.setdefault("INTERNAL_BOT_SECRET", "test-secret-that-is-long-enough-for-tests-123456")

# Add server and bot directories to Python path
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "server"))
sys.path.insert(0, str(PROJECT_ROOT / "bot"))

from fastapi.testclient import TestClient
import main
from telegram_bot import ServiceBookBot, ApiError


class BackendAuthIntegrationTests(unittest.TestCase):
    def setUp(self):
        main.ip_request_counts.clear()
        self.tmp = tempfile.TemporaryDirectory()
        self.db_path = os.path.join(self.tmp.name, "integration_auth.sqlite3")
        self.path_patcher = patch.object(main, "DB_PATH", main.Path(self.db_path))
        self.path_patcher.start()

        self.client = TestClient(main.app)
        self.client.__enter__()

        self.sent_telegram_messages = []
        self.answered_callbacks = []

        def fake_telegram_request(method: str, payload: dict) -> dict:
            if method == "sendMessage":
                self.sent_telegram_messages.append(payload)
                return {"ok": True, "result": {"message_id": len(self.sent_telegram_messages)}}
            elif method == "answerCallbackQuery":
                self.answered_callbacks.append(payload)
                return {"ok": True, "result": True}
            elif method == "editMessageReplyMarkup":
                return {"ok": True, "result": True}
            return {"ok": True, "result": {}}

        def client_api_request(method: str, path: str, payload: dict | None) -> dict:
            headers = {"X-Bot-Secret": main.BOT_SECRET}
            if method == "GET":
                resp = self.client.get(path, headers=headers)
            else:
                resp = self.client.post(path, headers=headers, json=payload or {})
            if resp.status_code >= 400:
                try:
                    detail = resp.json().get("detail", "Error")
                except Exception:
                    detail = resp.text
                raise ApiError(resp.status_code, detail)
            return resp.json()

        self.bot = ServiceBookBot(
            bot_token="fake:token_123456",
            api_url="http://127.0.0.1:8000",
            internal_secret=main.BOT_SECRET,
            telegram_request=fake_telegram_request,
            api_request=client_api_request,
        )

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.path_patcher.stop()
        self.tmp.cleanup()

    def test_backend_integration_registration_flow(self):
        phone = "+7 (999) 111-22-33"
        telegram_id = 98765

        # 1. Android: пользователь вводит телефон и запрашивает регистрацию
        create_resp = self.client.post("/auth/requests", json={"phone": phone, "mode": "register"})
        self.assertEqual(create_resp.status_code, 200)
        request_id = create_resp.json()["request_id"]

        # Проверяем статус в Android - пока pending
        status_resp = self.client.get(f"/auth/requests/{request_id}")
        self.assertEqual(status_resp.json()["status"], "pending")

        # 2. Telegram: пользователь переходит по ссылке /start sb_<request_id>
        self.bot.process_update({
            "message": {
                "message_id": 1,
                "from": {"id": telegram_id, "first_name": "Иван"},
                "chat": {"id": telegram_id},
                "text": f"/start sb_{request_id}",
            }
        })
        self.assertTrue(any("Подтвердите номер телефона" in m.get("text", "") for m in self.sent_telegram_messages))

        # 3. Telegram: пользователь делится своим контактом
        self.bot.process_update({
            "message": {
                "message_id": 2,
                "from": {"id": telegram_id},
                "chat": {"id": telegram_id},
                "contact": {
                    "user_id": telegram_id,
                    "phone_number": "+79991112233",
                },
            }
        })

        # 4. Android: клиент опрашивает статус и видит APPROVED
        final_status = self.client.get(f"/auth/requests/{request_id}").json()
        self.assertEqual(final_status["status"], "approved")

        # Проверяем, что связка пользователя сохранена в базе
        with main.db() as conn:
            user = conn.execute("SELECT * FROM users WHERE phone = ?", (main.normalize_phone(phone),)).fetchone()
            self.assertIsNotNone(user)
            self.assertEqual(user["telegram_user_id"], telegram_id)

    def test_backend_integration_login_flow_and_rejection(self):
        phone = "+7 (999) 333-22-11"
        telegram_id = 55443

        # Сначала регистрируем пользователя
        with main.db() as conn:
            conn.execute(
                "INSERT INTO users (phone, telegram_user_id, created_at) VALUES (?, ?, ?)",
                (main.normalize_phone(phone), telegram_id, int(time.time())),
            )

        # 1. Android: пользователь выбирает вход
        create_resp = self.client.post("/auth/requests", json={"phone": phone, "mode": "login"})
        self.assertEqual(create_resp.status_code, 200)
        request_id = create_resp.json()["request_id"]

        # 2. Telegram: бот получает /start от привязанного пользователя и показывает кнопки
        self.bot.process_update({
            "message": {
                "message_id": 10,
                "from": {"id": telegram_id},
                "chat": {"id": telegram_id},
                "text": f"/start sb_{request_id}",
            }
        })
        self.assertTrue(any("Запрошен вход" in m.get("text", "") for m in self.sent_telegram_messages))

        # 3. Telegram: пользователь подтверждает вход нажатием кнопки
        self.bot.process_update({
            "callback_query": {
                "id": "cb_approve_1",
                "from": {"id": telegram_id},
                "data": f"approve:{request_id}",
            }
        })

        # 4. Android: статус approved
        status = self.client.get(f"/auth/requests/{request_id}").json()
        self.assertEqual(status["status"], "approved")

        # --- Теперь проверяем сценарий отклонения входа ---
        login2_resp = self.client.post("/auth/requests", json={"phone": phone, "mode": "login"})
        # Так как прошло меньше 15 секунд, вернется тот же запрос (кулдаун), сэмулируем запрос после кулдауна:
        with patch.object(main.time, "time", return_value=int(time.time()) + 20):
            login2_resp = self.client.post("/auth/requests", json={"phone": phone, "mode": "login"})
        login2_id = login2_resp.json()["request_id"]

        # Пользователь отклоняет вход
        self.bot.process_update({
            "callback_query": {
                "id": "cb_reject_1",
                "from": {"id": telegram_id},
                "data": f"reject:{login2_id}",
            }
        })

        # Android видит rejected
        status_rejected = self.client.get(f"/auth/requests/{login2_id}").json()
        self.assertEqual(status_rejected["status"], "rejected")

    def test_backend_integration_expiration_flow(self):
        phone = "+7 (999) 444-55-66"
        create_resp = self.client.post("/auth/requests", json={"phone": phone, "mode": "register"})
        request_id = create_resp.json()["request_id"]

        # Сэмулируем истечение срока (прошло более 300 с)
        with main.db() as conn:
            conn.execute("UPDATE auth_requests SET expires_at = ? WHERE request_id = ?", (int(time.time()) - 10, request_id))

        # Android запрашивает статус - статус expired
        status = self.client.get(f"/auth/requests/{request_id}").json()
        self.assertEqual(status["status"], "expired")

        # Попытка бота подтвердить истёкший запрос приводит к сообщению об истечении
        self.bot.process_update({
            "callback_query": {
                "id": "cb_expired_1",
                "from": {"id": 11111},
                "message": {"chat": {"id": 11111}},
                "data": f"approve:{request_id}",
            }
        })
        self.assertTrue(any("истёк" in a.get("text", "") for a in self.answered_callbacks))

    def test_backend_integration_recovery_after_bot_restart(self):
        phone = "+7 (999) 777-66-55"
        telegram_id = 44556

        # Пользователь создал запрос в Android
        create_resp = self.client.post("/auth/requests", json={"phone": phone, "mode": "register"})
        request_id = create_resp.json()["request_id"]

        # Бот "перезапускается" (создаем абсолютно новый экземпляр бота с чистой памятью)
        restarted_bot = ServiceBookBot(
            bot_token="fake:token_restarted",
            api_url="http://127.0.0.1:8000",
            internal_secret=main.BOT_SECRET,
            telegram_request=self.bot.telegram_request,
            api_request=self.bot.api_request,
        )

        # Пользователь отправляет команду /start перезапущенному боту
        restarted_bot.process_update({
            "message": {
                "message_id": 1,
                "from": {"id": telegram_id},
                "chat": {"id": telegram_id},
                "text": f"/start sb_{request_id}",
            }
        })

        # Перезапущенный бот успешно запросил состояние у API сервера и вывел кнопку отправки контакта
        self.assertEqual(restarted_bot.pending_contacts.get(telegram_id), request_id)

        # Пользователь отправляет контакт перезапущенному боту
        restarted_bot.process_update({
            "message": {
                "message_id": 2,
                "from": {"id": telegram_id},
                "chat": {"id": telegram_id},
                "contact": {
                    "user_id": telegram_id,
                    "phone_number": "+79997776655",
                },
            }
        })

        # Запрос успешно подтверждён на сервере
        final_status = self.client.get(f"/auth/requests/{request_id}").json()
        self.assertEqual(final_status["status"], "approved")

    def test_backend_integration_server_unreachable_error_handling(self):
        def failing_api_request(method: str, path: str, payload: dict | None) -> dict:
            raise ApiError(0, "Не удалось связаться с локальным сервером ServiceBook")

        unreachable_bot = ServiceBookBot(
            bot_token="fake:token_fail",
            api_url="http://127.0.0.1:8000",
            internal_secret=main.BOT_SECRET,
            telegram_request=self.bot.telegram_request,
            api_request=failing_api_request,
        )

        unreachable_bot.process_update({
            "message": {
                "message_id": 1,
                "from": {"id": 12345},
                "chat": {"id": 12345},
                "text": "/start sb_valid_request_id_longer_than_twenty_characters",
            }
        })

        # Бот не упал, а корректно отправил понятное сообщение об ошибке
        self.assertTrue(any("Не удалось связаться с локальным сервером" in m.get("text", "") for m in self.sent_telegram_messages))


if __name__ == "__main__":
    unittest.main()
