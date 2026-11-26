# Hardening Plan: Auth Recovery, Security & Clean Device Login

> **Required methodology:** Superpowers TDD (test-driven development), systematic debugging, and verification before completion.

**Branch:** `security-fix` (READ-ONLY to main, no pushes without explicit permission)

---

### Objectives & Defects to Fix:

1. **Defect 1: Clean Device Login Deadlock & UI Blocker**
   - In `AuthScreens.kt`, `LoginScreen` currently blocks login with `!ServiceBookRepository.isPhoneRegistered(phoneDigits)` before sending request to server.
   - Fix: Remove this local check so that existing accounts can log in on a clean device. Server is the source of truth. Prefill phone on `CheckDataScreen` with proper label and description.

2. **Defect 2: Session Binding & Third-Party `request_id` Leakage**
   - In `server/main.py`, requesting auth with an existing phone returns the active `request_id` to any client within 15 seconds.
   - Fix: Require client token / secret (`client_token`). Idempotent return is allowed ONLY if `client_token` matches. If an untrusted third party sends a request for the same phone within cooldown, respond with HTTP 429 Too Many Requests without leaking `request_id`.

3. **Defect 3: Empty Profile Bypass in `completeRegistrationAndLogin()`**
   - In `ServiceBookRepository.kt`, `completeRegistrationAndLogin()` marks `_isLoggedIn.value = true` even if `_userProfile.value.firstName.isBlank()` and draft is missing.
   - Fix: If profile is blank and draft is missing/corrupted, reject completion (return `false`). In `TelegramAuthScreen`, route to `onNeedProfile()` so the user fills their profile on `CheckDataScreen`.

4. **Defect 4: TelegramAuth UI Lost on Process Death**
   - In `MainActivity.kt`, `pendingAuthRequest` is stored in Compose `remember { mutableStateOf(null) }`. Process death resets it to null, dropping the user to Login/Onboarding.
   - Fix: Persist pending auth (`pending_auth_request_id`, `pending_auth_phone`, `pending_auth_mode`, `pending_auth_deadline`) in `SharedPreferences`. On startup/re-creation, restore state and resume `TelegramAuthScreen` polling if not expired. Clear upon completion or back navigation.

5. **Defect 5: IP Rate Limiting for Mass Creation Protection**
   - In `server/main.py`, cooldown is only per-phone. Mass requests with random phone numbers can flood SQLite.
   - Fix: Add per-IP rate limiter (e.g., max 10 requests per minute per IP) and global rate limiter in `server/main.py`.

6. **Defect 6: Clarify Integration Tests**
   - Update `server/tests/test_auth_integration.py` naming and docstrings to accurately describe Backend Integration Tests rather than full E2E tests.

---

### Execution Phases (TDD):

- **Phase 1: `completeRegistrationAndLogin()` Empty Profile Guard (Android Unit Tests)**
  - Write test in `RegistrationDraftTest.kt` verifying that missing draft + blank profile fails and does NOT log in.
  - Implement fix in `ServiceBookRepository.kt` and `TelegramAuthScreen`.
  - Verify test passes.

- **Phase 2: Client Token Binding & Cooldown Protection (Server Python Tests)**
  - Write tests in `server/tests/test_auth_api.py` verifying third-party requests within 15s receive HTTP 429 without `request_id`, while matching `client_token` receives idempotent response.
  - Implement `client_token` column in `auth_requests` and validation logic in `server/main.py`.
  - Update Android `AuthApiClient.kt` to generate and send `client_token`.
  - Verify tests pass.

- **Phase 3: Clean Device Login Deadlock Removal (Android UI & Tests)**
  - Update `LoginScreen` in `AuthScreens.kt` to allow login requests regardless of local `isPhoneRegistered()`.
  - Fix copy on `CheckDataScreen`.
  - Add test in `RegistrationDraftTest.kt`.

- **Phase 4: TelegramAuth State Restoration Across Process Restart (Android)**
  - Write tests for `savePendingAuth`, `getPendingAuth`, `clearPendingAuth`.
  - Implement restoration in `MainActivity.kt`.
  - Verify tests pass.

- **Phase 5: Server IP Rate Limiter (Python Tests)**
  - Write tests in `server/tests/test_auth_api.py` for IP rate limiting.
  - Implement IP rate limiting in `server/main.py`.
  - Verify tests pass.

- **Phase 6: Integration Test Suite Documentation & Regression Verification**
  - Clarify integration tests in `server/tests/test_auth_integration.py`.
  - Run all Android tests (`gradlew.bat testDebugUnitTest assembleDebug`).
  - Run all server and bot tests.
  - Verify zero regressions.
