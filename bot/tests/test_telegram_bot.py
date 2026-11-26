import unittest
import urllib.request
from unittest.mock import Mock

from telegram_bot import ApiError, ServiceBookBot


class BotHandlerTests(unittest.TestCase):
    def setUp(self):
        self.telegram = Mock()
        self.api = Mock()
        self.bot = ServiceBookBot(
            bot_token="test-token",
            api_url="http://127.0.0.1:8000",
            internal_secret="test-secret",
            telegram_request=self.telegram,
            api_request=self.api,
        )

    def message(self, text=None, contact=None, user_id=123, chat_id=123):
        message = {"from": {"id": user_id, "first_name": "Test"}, "chat": {"id": chat_id}}
        if text is not None:
            message["text"] = text
        if contact is not None:
            message["contact"] = contact
        return {"message": message}

    def test_telegram_proxy_does_not_apply_to_local_api_requests(self):
        bot = ServiceBookBot(
            bot_token="test-token",
            api_url="http://127.0.0.1:8000",
            internal_secret="test-secret",
            proxy_url="http://127.0.0.1:10809",
            telegram_request=self.telegram,
            api_request=self.api,
        )
        telegram_proxy = next(
            handler for handler in bot.telegram_opener.handlers
            if isinstance(handler, urllib.request.ProxyHandler)
        )
        api_proxy = next(
            (handler for handler in bot.api_opener.handlers
             if isinstance(handler, urllib.request.ProxyHandler)),
            None,
        )
        self.assertEqual(telegram_proxy.proxies.get("https"), "http://127.0.0.1:10809")
        self.assertIsNone(api_proxy)

    def test_start_registration_requests_own_contact(self):
        self.api.return_value = {
            "request_id": "abcde12345_abcdefghij",
            "phone": "9991234567",
            "mode": "register",
            "known_telegram_user_id": None,
            "expires_at": 9999999999,
        }
        self.bot.process_update(self.message("/start sb_abcde12345_abcdefghij"))
        self.api.assert_called_once_with("GET", "/internal/auth/requests/abcde12345_abcdefghij", None)
        payload = self.telegram.call_args.args[1]
        self.assertTrue(payload["reply_markup"]["keyboard"][0][0]["request_contact"])

    def test_forwarded_contact_is_not_sent_to_server(self):
        self.api.return_value = {
            "request_id": "abcde12345_abcdefghij",
            "phone": "9991234567",
            "mode": "register",
            "known_telegram_user_id": None,
            "expires_at": 9999999999,
        }
        self.bot.process_update(self.message("/start sb_abcde12345_abcdefghij"))
        self.api.reset_mock()
        self.bot.process_update(self.message(contact={
            "phone_number": "+79991234567", "user_id": 456,
        }))
        self.assertFalse(any(call.args[0] == "POST" for call in self.api.call_args_list))

    def test_own_contact_is_verified_by_api(self):
        self.api.return_value = {
            "request_id": "abcde12345_abcdefghij",
            "phone": "9991234567",
            "mode": "register",
            "known_telegram_user_id": None,
            "expires_at": 9999999999,
        }
        self.bot.process_update(self.message("/start sb_abcde12345_abcdefghij"))
        self.api.reset_mock()
        self.api.return_value = {"status": "approved"}
        self.bot.process_update(self.message(contact={
            "phone_number": "+79991234567", "user_id": 123,
        }))
        self.api.assert_called_once()
        self.assertEqual(self.api.call_args.args[0:2], ("POST", "/internal/auth/approve"))
        self.assertEqual(self.api.call_args.args[2]["contact_user_id"], 123)

    def test_login_shows_inline_approval_only_for_bound_telegram_user(self):
        self.api.return_value = {
            "request_id": "abcde12345_abcdefghij",
            "phone": "9991234567",
            "mode": "login",
            "known_telegram_user_id": 123,
            "expires_at": 9999999999,
        }
        self.bot.process_update(self.message("/start sb_abcde12345_abcdefghij"))
        markup = self.telegram.call_args.args[1]["reply_markup"]
        self.assertEqual(markup["inline_keyboard"][0][0]["callback_data"], "approve:abcde12345_abcdefghij")
        self.assertEqual(markup["inline_keyboard"][0][1]["callback_data"], "reject:abcde12345_abcdefghij")

    def test_wrong_telegram_user_cannot_approve_login(self):
        self.api.return_value = {
            "request_id": "abcde12345_abcdefghij",
            "phone": "9991234567",
            "mode": "login",
            "known_telegram_user_id": 123,
            "expires_at": 9999999999,
        }
        self.bot.process_update(self.message("/start sb_abcde12345_abcdefghij", user_id=999))
        self.api.reset_mock()
        self.bot.process_update({
            "callback_query": {
                "id": "callback-1",
                "from": {"id": 999},
                "data": "approve:abcde12345_abcdefghij",
                "message": {"chat": {"id": 999}, "message_id": 10},
            }
        })
        self.assertFalse(any(call.args[0] == "POST" for call in self.api.call_args_list))
        self.assertTrue(any(call.args[0] == "answerCallbackQuery" for call in self.telegram.call_args_list))

    def test_bound_user_can_approve_login(self):
        self.api.return_value = {
            "request_id": "abcde12345_abcdefghij",
            "phone": "9991234567",
            "mode": "login",
            "known_telegram_user_id": 123,
            "expires_at": 9999999999,
        }
        self.bot.process_update(self.message("/start sb_abcde12345_abcdefghij"))
        self.api.reset_mock()
        self.api.side_effect = [
            {
                "request_id": "abcde12345_abcdefghij",
                "phone": "9991234567",
                "mode": "login",
                "known_telegram_user_id": 123,
                "expires_at": 9999999999,
            },
            {"status": "approved"},
        ]
        self.bot.process_update({
            "callback_query": {
                "id": "callback-1",
                "from": {"id": 123},
                "data": "approve:abcde12345_abcdefghij",
                "message": {"chat": {"id": 123}, "message_id": 10},
            }
        })
        approval_calls = [call for call in self.api.call_args_list if call.args[0] == "POST"]
        self.assertEqual(approval_calls[0].args[1], "/internal/auth/approve")
        self.assertEqual(approval_calls[0].args[2]["telegram_user_id"], 123)

    def test_clears_pending_contact_when_request_deleted_404(self):
        self.bot.pending_contacts[123] = "abcde12345_abcdefghij"
        self.api.side_effect = ApiError(404, "Запрос не найден")
        self.bot.process_update({
            "message": {
                "from": {"id": 123},
                "chat": {"id": 123},
                "contact": {"user_id": 123, "phone_number": "+79991234567"},
            }
        })
        self.assertNotIn(123, self.bot.pending_contacts)


if __name__ == "__main__":
    unittest.main()
