# Отчёт о подготовке чистой staging-копии ServiceBook

**Дата:** 26.11.2026  
**Исходный проект (не изменён):** `D:\AndroidStudioProjects\ServiceBook` (ветка `security-fix`)  
**Чистая staging-копия:** `D:\AndroidStudioProjects\ServiceBook-clean-staging`

---

## 1. Исходный HEAD

- `fa52f87c6d8aa539452cf9f346889cc102bf0d7e` (`fix(auth): harden Telegram authorization recovery and retries`, ветка `security-fix`).

## 2. Подтверждение сохранности оригинала

- Исходный каталог `D:\AndroidStudioProjects\ServiceBook` не изменялся: `HEAD` остался `fa52f87c6d8aa539452cf9f346889cc102bf0d7e`, команды `git clean`/`reset`/`checkout`/`rebase`/`commit`/`push` не выполнялись.

## 3. Количество скопированных файлов

- Скопировано **90** чистых файлов проекта (включая все незакоммиченные изменения).

## 4. Список исключённых категорий файлов

- `.git/` (старая история с коммитом `94c8978`, содержавшим старый `BOT_TOKEN`)
- `bot/.env`, `server/.env`, `local.properties`
- `java_pid35648.hprof` (`*.hprof`)
- `server/servicebook_auth.sqlite3`, `bot/auth_sessions.json`
- `qa-evidence/`, `qa-evidence-zips/`, `qa-evidence.zip`
- `.idea/`, `.gradle/`, `.kotlin/`, `app/build/`, `bot/.venv/`, `server/.venv/`, `__pycache__/`, `.pytest_cache/`

## 5. Проверка сохранения всех незакоммиченных изменений

Проверено по SHA-256 **13/13** ключевых изменённых/новых файлов из рабочей папки `security-fix` (включая удаление аккаунта, исправления 4 Android-дефектов и новые тесты):
- `MainActivity.kt`, `AuthApiClient.kt`, `ServiceBookRepository.kt`, `AuthScreens.kt`, `DashboardScreen.kt`, `ProfileScreen.kt`, `ValidationUtils.kt`
- `AccountDeletionTest.kt`, `ValidationUtilsTest.kt`
- `bot/telegram_bot.py`, `bot/tests/test_telegram_bot.py`
- `server/main.py`, `server/tests/test_account_deletion.py`
Все 13 файлов побайтово идентичны оригиналу.

## 6. Результат secret scan

- Проверка всех файлов staging-копии (исключая сгенерированные локальные `bot/.env` и `server/.env`) показала **0** совпадений по токенам Telegram, приватным ключам и JWT.
- `app/build.gradle.kts:33` содержит только публичный `YANDEX_CLIENT_ID` для мобильного SDK (без `client_secret`).
- `.env.example` в `bot/` и `server/` содержат только безопасные шаблонные значения.

## 7. Проверка `.gitignore`

- Обновлён `.gitignore` в `ServiceBook-clean-staging` (правила для `.env`, `.env.*`, `!bot/.env.example`, `!server/.env.example`, `local.properties`, `*.hprof`, `*.sqlite`, `*.sqlite3`, `*.db`, `*.apk`, `*.aab`, `*.jks`, `qa-evidence/`, `qa-evidence-zips/`, `qa-evidence.zip`, `server/user_files/`, `.pytest_cache/`, `**/.venv/`, `**/__pycache__/`, `**/build/`).
- Команда `git check-ignore -v` подтвердила игнорирование всех 17 проверенных чувствительных и временных путей.

## 8. Проверка локальных конфигураций

- Через `secrets.token_urlsafe(32)` (32 криптографически случайных байта) сгенерирован новый `INTERNAL_BOT_SECRET` и записан только в локальные игнорируемые файлы `ServiceBook-clean-staging\bot\.env` и `ServiceBook-clean-staging\server\.env`.
- Поле `BOT_TOKEN=` в `bot/.env` оставлено пустым (старый токен не переносился).
- `bot/telegram_bot.py` и `server/main.py` читают `BOT_TOKEN` и `INTERNAL_BOT_SECRET` через `os.getenv(..., "")` без захардкоженных fallback-секретов; CI-воркфлоу `.github/workflows/` не зависят от локальных `.env`.

## 9. Результаты тестов в `ServiceBook-clean-staging`

- **Android unit tests (`.\gradlew.bat testDebugUnitTest`):** **25 passed, 0 failed, 0 errors, 0 skipped** (`AccountDeletionTest` 3/3, `AuthApiClientTest` 4/4, `RegistrationDraftTest` 10/10, `MainActivityRecoveryTest` 3/3, `ValidationUtilsTest` 5/5).
- **Android сборка (`.\gradlew.bat assembleDebug`):** `BUILD SUCCESSFUL` (1 прежнее предупреждение компилятора в `Theme.kt:37`).
- **Backend (`python -m pytest -v`):** **29 passed** (`test_account_deletion.py`, `test_auth_api.py`, `test_auth_integration.py`).
- **Telegram-бот (`python -m unittest discover -s tests -v`):** **8 passed** (`test_telegram_bot.py`). *(Живой запуск бота с внешним Telegram API не выполнялся, так как требуется новый `BOT_TOKEN` от `@BotFather`).*

## 10. Проверка чистой Git-истории

- Выполнен `git init` в `ServiceBook-clean-staging` (без `git add` и без коммитов).
- `git log`: коммитов нет (старая история полностью отсутствует).
- `git remote -v` и `git branch -a`: отсутствуют.
- `git status --short`: показывает только 15 чистых каталогов/файлов исходников; `.env`, `.gradle/`, `app/build/`, `.pytest_cache/` надёжно игнорируются.

## 11. Что ещё необходимо сделать вручную

1. Перевыпустить Telegram Bot API Token через `@BotFather` (и отозвать старый токен) и вписать его в локальный `D:\AndroidStudioProjects\ServiceBook-clean-staging\bot\.env`.
2. При необходимости создать локальный `local.properties` (с `sdk.dir` и `servicebook.authApiUrl`) для запуска на устройстве.
3. После финального security-review выполнить первый чистый коммит и публикацию в новый публичный репозиторий по вашему отдельному разрешению.

---

**Итоговый статус:** **READY FOR FINAL SECURITY REVIEW**
