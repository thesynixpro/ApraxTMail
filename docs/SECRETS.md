# Secrets & environment variables

**Rule:** provider API keys, JWT secret, and encryption keys live **only** on the backend (env / Docker secrets / host env). The Android app never receives them.

## Backend environment (`.env`)

Copy `.env.example` → `.env` (gitignored).

| Variable | Purpose | Notes |
|----------|---------|--------|
| `PORT` | HTTP port | default `8080` |
| `APP_ENV` | `production` / `development` | |
| `PUBLIC_BASE_URL` | Public URL for links | no trailing slash |
| `DATABASE_URL` | JDBC URL | `jdbc:postgresql://host:5432/aprax_tmaii` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | DB auth | |
| `DATABASE_MAX_POOL_SIZE` | Hikari pool | |
| `JWT_SECRET` | HMAC for JWTs | **≥ 32 chars**; `openssl rand -hex 32` |
| `TOKEN_ENCRYPTION_KEY` | AES-GCM for mailbox tokens at rest | `openssl rand -base64 32` |
| `GUEST_SESSION_HOURS` / `ACCOUNT_SESSION_HOURS` | Session TTL | |
| `FCE_API_KEY` | FreeCustom key (`fce_…`) | blank → provider disabled |
| `TMAILOR_API_KEY` | Tmailor key (`tmk_…`) | blank → provider disabled |
| `FCE_ENABLED` / `TMAILOR_ENABLED` | Explicit toggles | |
| `TMAILOR_EXPECTED_IP` | Optional expected egress IP | diagnostics |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | First admin bootstrap | password **≥ 12** chars |
| `RATE_LIMIT_RPS` | Global per-IP RPS | |
| `GUEST_MAILBOX_LIMIT` / `ACCOUNT_MAILBOX_LIMIT` | Mailboxes per hour | |
| `CORS_ORIGINS` | Comma-separated origins | admin UI origin |

## Rotating keys (no app rebuild)

1. Update `.env` on the server (or platform secret store).
2. Restart the backend container/process.
3. Android app keeps working — it only talks to your REST API.

## GitHub Actions secrets

| Secret | Used by | Purpose |
|--------|---------|---------|
| `RELEASE_KEYSTORE_BASE64` | android-release | base64 of upload keystore |
| `RELEASE_KEYSTORE_PASSWORD` | android-release | keystore password |
| `RELEASE_KEY_ALIAS` | android-release | key alias (default `aprax`) |
| `RELEASE_KEY_PASSWORD` | android-release | key password |
| `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` | backend-deploy | push image (optional; falls back to GHCR) |
| `DEPLOY_HOST` / `DEPLOY_USER` / `DEPLOY_SSH_KEY` | backend-deploy | optional SSH deploy |

Never print secrets in workflow logs. Prefer GitHub environment secrets for production.

## What must never be committed

- `.env`
- Keystores (`*.jks`, `*.keystore`) except intentional debug keystore
- Real `FCE_API_KEY` / `TMAILOR_API_KEY`
- Private keys for SSH deploy
