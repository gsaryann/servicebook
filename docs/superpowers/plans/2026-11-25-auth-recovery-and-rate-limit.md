# ServiceBook Auth Recovery, Rate Limiting & E2E Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix registration draft loss on app restart, handle login from clean devices without creating fake profiles, add server request rate-limiting (15s cooldown / single active request per phone / stale cleanup), and add end-to-end integration tests.

**Architecture:**
1. **Android Client Persistence:** `ServiceBookRepository` stores a persistent registration draft in `SharedPreferences` (`draft_registration_<phone>`) before the user opens Telegram. If the app process dies while in Telegram, returning to the app restores the draft upon receiving `APPROVED`.
2. **Clean Device Login Handling:** If Telegram login is `APPROVED` but no local account or draft exists for this phone (e.g. clean device or wiped data), Android does not create an empty/fake account; it navigates to `CheckDataScreen` with the verified phone prefilled (read-only), allowing the user to complete their profile safely.
3. **Server Rate Limiting & Cooldown:** `server/main.py` enforces a single active request per phone, deduplicates rapid double-taps within 15 seconds idempotently, marks older pending requests as expired, and purges requests older than 1 hour.
4. **Integration Tests:** Comprehensive automated tests covering full lifecycle (register, login, reject, expire, and draft recovery).

---

### Task 1: Persistent registration draft and recovery across app restart

**Files:**
- Modify: `app/src/main/java/com/servicebook/data/ServiceBookRepository.kt`
- Modify: `app/src/main/java/com/servicebook/ui/screens/AuthScreens.kt`
- Create / Modify: `app/src/test/java/com/servicebook/data/RegistrationDraftTest.kt`

- [ ] **Step 1: Write failing unit tests** for `saveRegistrationDraft`, `getRegistrationDraft`, `clearRegistrationDraft`, and recovery when in-memory profile is cleared.
- [ ] **Step 2: Run tests** and confirm failure.
- [ ] **Step 3: Implement draft persistence** in `ServiceBookRepository.kt` using `SharedPreferences`.
- [ ] **Step 4: Update `TelegramAuthScreen` and `RegisterScreen`** in `AuthScreens.kt` to save draft before launching Telegram and restore from draft on `APPROVED` if in-memory profile is empty.
- [ ] **Step 5: Run tests** and confirm all pass.
- [ ] **Step 6: Commit** as `fix(auth): persist registration draft to survive app process death`.

---

### Task 2: Handle login from new device / missing local profile safely

**Files:**
- Modify: `app/src/main/java/com/servicebook/MainActivity.kt`
- Modify: `app/src/main/java/com/servicebook/ui/screens/AuthScreens.kt`
- Modify: `app/src/main/java/com/servicebook/data/ServiceBookRepository.kt`

- [ ] **Step 1: Write failing test / check** that login with no local account and no draft routes to profile completion instead of empty profile.
- [ ] **Step 2: Implement route check in `MainActivity.kt`**: when `TelegramAuthScreen` succeeds on `mode == "login"`, if `!ServiceBookRepository.isPhoneRegistered(phone)` and no draft exists, set phone in `_userProfile` and navigate to `Screen.CheckData`.
- [ ] **Step 3: Verify existing login with local account** still logs in directly to `MainTab` / `AddCar`.
- [ ] **Step 4: Run Android unit tests** and confirm pass.
- [ ] **Step 5: Commit** as `fix(auth): require profile completion when logging in on a new device`.

---

### Task 3: Server rate limiting, cooldown, and stale request cleanup

**Files:**
- Modify: `server/main.py`
- Modify: `server/tests/test_auth_api.py`

- [ ] **Step 1: Write failing tests** in `server/tests/test_auth_api.py` for:
  - Idempotent return of same request on double-tap within 15 seconds.
  - Invalidation of older pending request on new request after cooldown.
  - Purge of requests older than 1 hour.
- [ ] **Step 2: Run `python -m unittest discover -s server/tests -v`** and confirm tests fail.
- [ ] **Step 3: Implement rate limiting and cleanup** in `server/main.py`.
- [ ] **Step 4: Run server tests** and confirm all pass.
- [ ] **Step 5: Commit** as `feat(server): add 15s request cooldown and stale request cleanup`.

---

### Task 4: Comprehensive integration tests and end-to-end audit

**Files:**
- Modify: `server/tests/test_auth_api.py`
- Modify: `bot/tests/test_telegram_bot.py`

- [ ] **Step 1: Add E2E tests** for complete Telegram registration flow (create request -> bot contact verification -> status check approved -> duplicate register rejected).
- [ ] **Step 2: Add E2E tests** for complete Telegram login flow with approve and reject callbacks.
- [ ] **Step 3: Add E2E tests** for expired requests and restart recovery.
- [ ] **Step 4: Run all Python tests** (`server` and `bot`).
- [ ] **Step 5: Run Android unit tests and assembleDebug**.
- [ ] **Step 6: Commit** as `test(auth): add end-to-end integration tests for Telegram auth lifecycle`.
