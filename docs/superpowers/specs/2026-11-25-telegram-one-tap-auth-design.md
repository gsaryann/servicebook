# ServiceBook Telegram One-Tap Authentication — Design

## Intent
Replace the Android-generated four-digit Telegram demo code with a server-verified, one-tap Telegram approval flow that can be demonstrated locally on the developer's Windows PC without paid hosting.

## Goals
- Android creates a short-lived authentication request through a local FastAPI API.
- The app opens the Telegram bot using an opaque, unguessable request ID.
- On first registration or the first login of an existing local-only account, the bot asks the user to share their own Telegram contact; the server verifies the sender Telegram ID and contact user ID/phone, then binds the Telegram ID to that phone.
- On later login, the bot shows Approve and Reject inline buttons; the server checks that the sender Telegram ID is already bound to the requested phone.
- The Android app polls the API and completes login only after the server returns a valid one-time result.
- Requests expire after five minutes and cannot be approved/rejected twice.
- Bot token and internal bot/server secret remain environment-only; no credentials or personal data are printed to logs.
- Provide Windows launch instructions for starting API and bot locally.

## Non-goals
- Moving vehicle/service history data from Android SharedPreferences into a cloud database.
- Public internet deployment, paid hosting, TLS termination, production-grade multi-device sessions, password reset, or Yandex OAuth redesign.
- Exposing the local API to the public internet.

## Approaches
1. **Local FastAPI + Telegram long polling (chosen):** Android reaches the PC over the same Wi-Fi; bot polls Telegram and calls the local API using a shared random internal secret. No public webhook or hosting is required. This matches the requested in-person demonstration.
2. Hosted API + webhook: supports users on any network, but requires public HTTPS hosting and is unnecessary for the current demo.
3. Android-only OTP: simplest, but cannot provide trusted server-side Telegram verification and is explicitly rejected.

## Architecture
- server/: FastAPI, SQLite, one-time request state, Telegram identity binding, session issuance and status polling.
- bot/: Telegram long-polling client. It never decides authentication itself; it forwards sender identity, contact identity, and explicit callback decisions to the server using X-Bot-Secret.
- app/: Kotlin client calls the local API, opens the bot deep link, polls status, and persists only a server-issued session marker locally. Existing vehicle, maintenance, and document data remain local.
- The local API binds to 0.0.0.0:8000; the phone uses the PC's private IPv4 address. Android emulator uses 10.0.2.2. Windows Firewall allows TCP 8000 only on Private networks.

## Security and behavior
- Request IDs and session tokens are generated with secrets, never four-digit random values.
- Request state is stored in SQLite, with a five-minute expiry and atomic pending-to-final transition.
- The bot authenticates to internal endpoints with a random secret of at least 32 characters and constant-time comparison.
- Registration requires a Telegram contact whose user_id equals the sender and whose normalized phone equals the requested phone.
- After the initial contact-link step, login requires the Telegram sender ID to match the account binding.
- Status polling is read-only and repeatable; an approved request remains visibly approved until its expiry. The demo does not issue a bearer API session because vehicle and service data remain local to Android.
- Local demo HTTP is limited to a trusted private Wi-Fi network; it is not production transport security.

## Constraints and decisions
- Keep the current security-fix branch; do not merge to main automatically.
- Do not put real bot tokens or internal secrets in GitHub. Keep .env ignored and commit only .env.example.
- Do not remove existing Yandex sign-in or rewrite unrelated app features.
- Keep the existing app's local account/profile persistence, but stop treating the locally generated Telegram code as proof of authentication.
- The flow is for a local demonstration. The PC and phone must be on the same Wi-Fi network (or Android emulator must use 10.0.2.2).
