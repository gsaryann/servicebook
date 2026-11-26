"""Telegram polling bot that delegates all authentication decisions to the local API."""
from __future__ import annotations

import json
import os
import re
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Callable

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / ".env")

BOT_TOKEN = os.getenv("BOT_TOKEN", "").strip()
API_URL = os.getenv("AUTH_API_URL", "http://127.0.0.1:8000").strip().rstrip("/")
INTERNAL_BOT_SECRET = os.getenv("INTERNAL_BOT_SECRET", "").strip()
PROXY_URL = os.getenv("TELEGRAM_PROXY_URL", "").strip()


class ApiError(RuntimeError):
    def __init__(self, status_code: int, detail: str):
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


def _read_json(response: Any) -> dict[str, Any]:
    return json.loads(response.read().decode("utf-8"))


class ServiceBookBot:
    def __init__(
        self,
        bot_token: str,
        api_url: str,
        internal_secret: str,
        telegram_request: Callable[[str, dict[str, Any]], dict[str, Any]] | None = None,
        api_request: Callable[[str, str, dict[str, Any] | None], dict[str, Any]] | None = None,
        proxy_url: str = "",
    ):
        self.bot_token = bot_token.strip()
        self.api_url = api_url.rstrip("/")
        self.internal_secret = internal_secret
        self.pending_contacts: dict[int, str] = {}
        self.telegram_opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({"http": proxy_url, "https": proxy_url})
        ) if proxy_url else urllib.request.build_opener()
        # The bot talks to the local API directly; never send loopback traffic through
        # the optional proxy used only to reach Telegram.
        self.api_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        self.telegram_request = telegram_request or self._telegram_http_request
        self.api_request = api_request or self._api_http_request

    def _telegram_http_request(self, method: str, payload: dict[str, Any]) -> dict[str, Any]:
        if not self.bot_token:
            raise RuntimeError("BOT_TOKEN is not configured")
        url = f"https://api.telegram.org/bot{self.bot_token}/{method}"
        request = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
        )
        with self.telegram_opener.open(request, timeout=35 if method == "getUpdates" else 15) as response:
            result = _read_json(response)
        if not result.get("ok"):
            raise RuntimeError("Telegram API returned an unsuccessful response")
        return result

    def _api_http_request(
        self, method: str, path: str, payload: dict[str, Any] | None
    ) -> dict[str, Any]:
        headers = {
            "Content-Type": "application/json",
            "X-Bot-Secret": self.internal_secret,
        }
        data = json.dumps(payload or {}).encode("utf-8") if method != "GET" else None
        request = urllib.request.Request(
            f"{self.api_url}{path}",
            data=data,
            headers=headers,
            method=method,
        )
        try:
            with self.api_opener.open(request, timeout=8) as response:
                return _read_json(response)
        except urllib.error.HTTPError as error:
            try:
                body = json.loads(error.read().decode("utf-8"))
                detail = str(body.get("detail", "API request rejected"))
            except (ValueError, UnicodeDecodeError):
                detail = "API request rejected"
            raise ApiError(error.code, detail) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise ApiError(0, "Не удалось связаться с локальным сервером ServiceBook") from None

    def _send_message(
        self, chat_id: int, text: str, reply_markup: dict[str, Any] | None = None
    ) -> None:
        payload: dict[str, Any] = {"chat_id": chat_id, "text": text, "parse_mode": "HTML"}
        if reply_markup:
            payload["reply_markup"] = reply_markup
        self.telegram_request("sendMessage", payload)

    @staticmethod
    def _parse_start(text: str) -> str | None:
        match = re.fullmatch(r"/start(?:@[\w_]+)?\s+sb_([A-Za-z0-9_-]{20,64})", text.strip())
        return match.group(1) if match else None

    def process_update(self, update: dict[str, Any]) -> None:
        try:
            if "message" in update:
                self._handle_message(update["message"])
            elif "callback_query" in update:
                self._handle_callback(update["callback_query"])
        except ApiError as error:
            message = update.get("message", {})
            callback = update.get("callback_query", {})
            chat_id = message.get("chat", {}).get("id") or callback.get("message", {}).get("chat", {}).get("id")
            if chat_id:
                self._send_message(chat_id, error.detail)
            if callback.get("id"):
                self.telegram_request(
                    "answerCallbackQuery",
                    {"callback_query_id": callback["id"], "text": error.detail[:180], "show_alert": True},
                )
        except Exception as error:
            # Do not print exception text: it may contain a Telegram bot-token URL.
            print(f"[BOT] Ошибка обработки обновления: {type(error).__name__}")

    def _handle_message(self, message: dict[str, Any]) -> None:
        sender_id = message.get("from", {}).get("id")
        chat_id = message.get("chat", {}).get("id")
        if not sender_id or not chat_id:
            return

        text = message.get("text", "")
        if text.startswith("/start"):
            request_id = self._parse_start(text)
            if not request_id:
                self._send_message(
                    chat_id,
                    "Откройте бота по ссылке из приложения ServiceBook, чтобы подтвердить вход.",
                )
                return
            self.pending_contacts.pop(chat_id, None)
            request = self.api_request("GET", f"/internal/auth/requests/{request_id}", None)
            if request.get("mode") == "register":
                self.pending_contacts[chat_id] = request_id
                keyboard = {
                    "keyboard": [[{"text": "📱 Поделиться своим номером", "request_contact": True}]],
                    "resize_keyboard": True,
                    "one_time_keyboard": True,
                }
                self._send_message(
                    chat_id,
                    "Подтвердите номер телефона для ServiceBook кнопкой ниже. "
                    "Отправьте именно свой контакт Telegram.",
                    keyboard,
                )
                return

            if request.get("mode") != "login":
                self._send_message(chat_id, "Неизвестный тип запроса авторизации.")
                return
            if request.get("known_telegram_user_id") != sender_id:
                self._send_message(
                    chat_id,
                    "⛔ Этот Telegram-аккаунт не привязан к указанному номеру. "
                    "Войдите с привязанного аккаунта.",
                    {"remove_keyboard": True},
                )
                return

            keyboard = {
                "inline_keyboard": [[
                    {"text": "✅ Подтвердить вход", "callback_data": f"approve:{request_id}"},
                    {"text": "❌ Отклонить", "callback_data": f"reject:{request_id}"},
                ]]
            }
            self._send_message(
                chat_id,
                "Запрошен вход в ServiceBook. Если это вы, подтвердите вход кнопкой ниже.",
                keyboard,
            )
            return

        if "contact" in message:
            self._handle_contact(message)
            return

        if text:
            self._send_message(
                chat_id,
                "Для авторизации откройте бота по ссылке из приложения ServiceBook.",
            )

    def _handle_contact(self, message: dict[str, Any]) -> None:
        chat_id = message["chat"]["id"]
        sender_id = message["from"]["id"]
        request_id = self.pending_contacts.get(chat_id)
        if not request_id:
            self._send_message(chat_id, "Сначала откройте ссылку регистрации из приложения ServiceBook.")
            return

        contact = message["contact"]
        if contact.get("user_id") != sender_id:
            self._send_message(
                chat_id,
                "Нужно поделиться собственным контактом через кнопку Telegram, а не пересылать чужой номер.",
            )
            return

        phone = str(contact.get("phone_number", "")).strip()
        if not phone:
            self._send_message(chat_id, "Telegram не передал номер телефона. Попробуйте ещё раз.")
            return

        try:
            result = self.api_request(
                "POST",
                "/internal/auth/approve",
                {
                    "request_id": request_id,
                    "telegram_user_id": sender_id,
                    "contact_user_id": contact.get("user_id"),
                    "contact_phone": phone,
                    "decision": "approve",
                },
            )
        except ApiError as error:
            if error.status_code in (404, 410):
                self.pending_contacts.pop(chat_id, None)
            raise
        if result.get("status") == "approved":
            self.pending_contacts.pop(chat_id, None)
            self._send_message(
                chat_id,
                "✅ Номер подтверждён. Вернитесь в ServiceBook — приложение завершит авторизацию автоматически.",
                {"remove_keyboard": True},
            )

    def _handle_callback(self, callback: dict[str, Any]) -> None:
        callback_id = callback.get("id")
        sender_id = callback.get("from", {}).get("id")
        data = callback.get("data", "")
        match = re.fullmatch(r"(approve|reject):([A-Za-z0-9_-]{20,64})", data)
        if not callback_id or not sender_id or not match:
            return

        decision, request_id = match.groups()
        chat_id = callback.get("message", {}).get("chat", {}).get("id")
        try:
            request = self.api_request("GET", f"/internal/auth/requests/{request_id}", None)
        except ApiError as error:
            if error.status_code in (404, 410) and chat_id:
                self.pending_contacts.pop(chat_id, None)
            raise
        if request.get("mode") != "login" or request.get("known_telegram_user_id") != sender_id:
            self.telegram_request(
                "answerCallbackQuery",
                {"callback_query_id": callback_id, "text": "Этот запрос предназначен другому Telegram-аккаунту.", "show_alert": True},
            )
            return

        result = self.api_request(
            "POST",
            "/internal/auth/approve",
            {
                "request_id": request_id,
                "telegram_user_id": sender_id,
                "decision": decision,
            },
        )
        status = result.get("status")
        if status == "approved":
            answer = "Вход подтверждён. Вернитесь в приложение."
        elif status == "rejected":
            answer = "Запрос на вход отклонён."
        else:
            answer = "Не удалось обработать запрос."
        self.telegram_request(
            "answerCallbackQuery",
            {"callback_query_id": callback_id, "text": answer, "show_alert": True},
        )
        message = callback.get("message", {})
        if message.get("chat", {}).get("id") and message.get("message_id"):
            self.telegram_request(
                "editMessageText",
                {
                    "chat_id": message["chat"]["id"],
                    "message_id": message["message_id"],
                    "text": answer,
                },
            )


def run_bot() -> None:
    if not BOT_TOKEN:
        raise RuntimeError("BOT_TOKEN is not configured in bot/.env")
    if len(INTERNAL_BOT_SECRET) < 32:
        raise RuntimeError("INTERNAL_BOT_SECRET must contain at least 32 random characters in bot/.env")
    if not API_URL:
        raise RuntimeError("AUTH_API_URL is not configured in bot/.env")

    bot = ServiceBookBot(BOT_TOKEN, API_URL, INTERNAL_BOT_SECRET, proxy_url=PROXY_URL)
    print("ServiceBook Telegram bot started. Waiting for Telegram updates...")

    offset = 0
    while True:
        try:
            response = bot.telegram_request(
                "getUpdates",
                {
                    "offset": offset + 1,
                    "timeout": 20,
                    "allowed_updates": ["message", "callback_query"],
                },
            )
            for update in response.get("result", []):
                offset = max(offset, int(update.get("update_id", offset)))
                bot.process_update(update)
        except Exception as error:
            print(f"[BOT] Ошибка получения обновлений: {type(error).__name__}")
            time.sleep(2)


if __name__ == "__main__":
    run_bot()
