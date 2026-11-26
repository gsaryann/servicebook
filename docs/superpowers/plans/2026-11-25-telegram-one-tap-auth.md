# ServiceBook Telegram One-Tap Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Android-local four-digit Telegram demo code with a server-verified one-tap Telegram approval flow that runs on the developer's PC without paid hosting.

**Architecture:** FastAPI and SQLite run on the PC; the Telegram bot uses long polling and calls the local API with a shared internal secret; Android reaches the PC over the same private Wi-Fi and polls a short-lived request. Existing vehicle and service-history data stay in SharedPreferences.

**Tech Stack:** Python 3.11+, FastAPI, Uvicorn, SQLite, python-dotenv, Telegram Bot API over urllib long polling, Kotlin/Jetpack Compose, HttpURLConnection, Gradle BuildConfig.

**Spec:** docs/superpowers/specs/2026-11-25-telegram-one-tap-auth-design.md

## Global Constraints
- The API binds to 0.0.0.0:8000 for local Wi-Fi demonstration only.
- Authentication requests expire after 300 seconds.
- The bot internal secret must be at least 32 random characters and must never be committed.
- Registration must verify contact.user_id == Telegram message.from.id and the normalized contact phone equals the requested phone.
- Login must verify the Telegram sender ID matches the previously registered phone binding.
- Only one pending-to-approved/rejected/expired transition is allowed per request.
- Keep all changes on security-fix; do not merge to main automatically.
- Do not rewrite Yandex ID or move vehicle/history/document data to a cloud database.
- Android emulator uses http://10.0.2.2:8000; a physical phone uses the PC's private IPv4 address.
- HTTP is permitted only for the isolated local demonstration; do not expose port 8000 to the public internet.

## Review Focus
- A Telegram contact forwarded from another person: reject unless contact.user_id equals the sender ID.
- A callback from a different Telegram account: reject without changing request state.
- An expired, malformed, or already-decided request: reject and never issue an authenticated result.
- Repeated Android status polling or app/network retry: do not consume or lose the only approval result.
- Android cannot reach the configured PC address or the API returns malformed/non-200 responses: show a recoverable error and do not log in.

---

### Task 1: Make the FastAPI authentication state machine correct and testable

**Files:**
- Modify: server/main.py
- Modify: server/tests/test_auth_api.py
- Modify: server/requirements.txt only if test/runtime dependencies are genuinely needed

**Interfaces:**
- Consumes: CreateAuthRequest(phone, mode), BotApprove(request_id, telegram_user_id, contact_user_id, contact_phone, decision).
- Produces: POST /auth/requests -> {request_id, bot_url, expires_in}; GET /auth/requests/{request_id} -> pending|approved|rejected|expired; internal bot endpoints protected by X-Bot-Secret.

- [ ] **Step 1: Write failing API tests** for duplicate registration, first-time login linking for an existing local-only account, own-contact requirement, phone mismatch, Telegram ID mismatch, approval/rejection only once, expiry, and repeat polling of approved status.
- [ ] **Step 2: Run `(cd server && python -m unittest discover -s tests -v)`** and confirm the new tests fail for the intended missing/incorrect behavior.
- [ ] **Step 3: Implement the API state machine** with an injectable test database path, atomic state transitions, expiry checks, and repeatable read-only status polling. Remove the in-memory one-shot token cache; the API returns only the final approval state because app data remains local.
- [ ] **Step 4: Run `python -m unittest discover -s server/tests -v`** and confirm all API tests pass.
- [ ] **Step 5: Commit** as `fix(auth): make local approval state transitions safe`.

### Task 2: Replace the old OTP bot with a server-backed Telegram approval bot

**Files:**
- Modify: bot/telegram_bot.py
- Create: bot/requirements.txt
- Modify: bot/.env.example
- Modify: bot/run.bat
- Create: bot/tests/test_telegram_bot.py

**Interfaces:**
- Consumes: API_URL, INTERNAL_BOT_SECRET, BOT_TOKEN, optional TELEGRAM_PROXY_URL.
- Produces: `/start sb_<request_id>` handling, contact verification for first registration, inline Approve/Reject callbacks for login, and authenticated internal API calls.

- [ ] **Step 1: Write failing bot tests** for parsing the deep link, requiring the sender's own contact, refusing a contact from another user, showing approval controls for a known login, and rejecting callback attempts from the wrong Telegram ID.
- [ ] **Step 2: Run `(cd bot && python -m unittest discover -s tests -v)`** and confirm the expected failures.
- [ ] **Step 3: Implement a small testable bot handler layer**; load bot/.env with python-dotenv; call only the local API for request lookup/approval/rejection; handle Telegram message and callback_query updates; never log OTPs, phone numbers, tokens, or full exception URLs.
- [ ] **Step 4: Add bot dependencies and a Windows launcher** that changes to the bot directory and starts the polling process.
- [ ] **Step 5: Run `python -m unittest discover -s bot/tests -v`** and confirm all bot tests pass.
- [ ] **Step 6: Commit** as `feat(bot): confirm ServiceBook logins through Telegram buttons`.

### Task 3: Connect Android login and registration to the local API

**Files:**
- Create: app/src/main/java/com/servicebook/data/AuthApiClient.kt
- Create: app/src/test/java/com/servicebook/data/AuthApiClientTest.kt
- Modify: app/src/main/java/com/servicebook/ui/screens/AuthScreens.kt
- Modify: app/src/main/java/com/servicebook/MainActivity.kt
- Modify: app/src/main/java/com/servicebook/data/ServiceBookRepository.kt
- Modify: app/build.gradle.kts
- Create: app/src/debug/AndroidManifest.xml

**Interfaces:**
- AuthApiClient.createRequest(baseUrl, phone, mode) returns requestId, botUrl, expiresIn.
- AuthApiClient.getStatus(baseUrl, requestId) returns Pending, Approved, Rejected, or Expired.
- ServiceBookRepository remains the owner of local profile/vehicle/history data; successful Telegram confirmation calls its existing local completion methods.

- [ ] **Step 1: Write failing AuthApiClient tests** using a local JVM HTTP test server for request JSON, response parsing, URL encoding, non-success HTTP status, and malformed JSON.
- [ ] **Step 2: Run the targeted Gradle unit test** and confirm it fails because the client is not implemented.
- [ ] **Step 3: Implement AuthApiClient** using HttpURLConnection on Dispatchers.IO, explicit connect/read timeouts, bounded response reads, and no credential logging.
- [ ] **Step 4: Configure BuildConfig.AUTH_API_BASE_URL** from a root local.properties property servicebook.authApiUrl with an emulator default of http://10.0.2.2:8000; document how to set the PC LAN IP for a physical phone. Enable cleartext only for this demo build and document that it must be disabled for production.
- [ ] **Step 5: Replace LoginScreen's locally generated OTP with a server request**; replace RegisterScreen's auth-code deep link with the same request flow; pass request ID and bot URL to TelegramAuthScreen.
- [ ] **Step 6: Replace the four-digit input with approval status UI** that opens the returned bot URL, polls every 1.5 seconds, stops on approved/rejected/expired or a bounded timeout, and only invokes onSuccess after approved.
- [ ] **Step 7: Preserve local account behavior**: registration completes local profile creation only after server approval; login loads the existing local account only after server approval; remove generateNewAuthCode/verifyAuthCode usage from the Telegram path.
- [ ] **Step 8: Run targeted unit tests and `gradlew.bat :app:assembleDebug`** (or `./gradlew :app:assembleDebug` on non-Windows); report environment limitations if Android SDK/Gradle dependencies are unavailable.
- [ ] **Step 9: Commit** as `feat(android): use Telegram server approval for sign-in`.

### Task 4: Make the local demonstration reproducible and review the complete change

**Files:**
- Modify: server/README.md
- Modify: README.md
- Modify: .gitignore if needed
- Create: run_local_demo.bat at repository root (optional if two clear launchers are simpler)

**Interfaces:**
- Developer configures server/.env and bot/.env locally; no secret values are committed.
- Server and bot run on the same Windows PC; phone connects over private Wi-Fi.

- [ ] **Step 1: Add a documentation test/check** that the documented environment variables and launch commands match the actual files.
- [ ] **Step 2: Document setup**: Python installation, secret generation, copy .env.example to .env, install dependencies, start API, start bot, find PC IPv4, allow private-network firewall access, configure Android API URL, and run the demo.
- [ ] **Step 3: Verify secret hygiene**: confirm .env, SQLite DB, session files, and Python caches are ignored; ensure no real token or secret appears in tracked files.
- [ ] **Step 4: Run the complete Python test suites, Python compilation checks, Android unit tests, and Android debug build through the security-fix CI workflow. Inspect the full branch diff and compare against the design.
- [ ] **Step 5: Request a fresh code review** of the completed branch; resolve all critical/important findings.
- [ ] **Step 6: Commit** as `docs: document local ServiceBook auth demo` and leave the branch unmerged.

## Manual acceptance walkthrough
1. Start FastAPI and Telegram bot on the PC.
2. Start the Android app on a phone connected to the same private Wi-Fi.
3. Register a new phone number; open the bot and share the user's own Telegram contact.
4. Confirm registration completes only after the server accepts the Telegram contact.
5. Log out and log in with the same phone; approve the request using the Telegram inline button.
6. Try a wrong Telegram account, a foreign contact, a rejected request, an expired request, and a repeated approval; all must fail safely.
7. Stop the API or use a wrong LAN IP; the app must show an error and must not log in.

## Known demonstration limitation
The server verifies the Telegram identity and the Android app keeps its existing local data. This does not synchronize ServiceBook accounts or service records across devices. HTTP on private Wi-Fi is for classroom demonstration only, not a production security configuration.
