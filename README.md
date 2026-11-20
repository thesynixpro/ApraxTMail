# Aprax TMaii

Production-oriented **temporary email** platform:

- **Android** — Kotlin, Jetpack Compose, Material 3, Hilt, Room, Ktor Client  
- **Backend** — Kotlin Ktor, PostgreSQL, JWT + guest sessions, provider adapters  
- **Providers** — FreeCustom.Email + Tmailor behind one `EmailProvider` API  
- **Admin** — static dashboard served at `/admin/` (stats, providers, usage, users, audit)

Provider API keys **never** leave the backend.

## Repository layout

```
android/     # app, core, feature modules
backend/     # Ktor REST API + adapters + tests
admin/       # frontend static files (served by backend)
docs/        # deployment, secrets, hosting
.github/workflows/
docker-compose.yml
.env.example
```

## Quick start (backend)

```bash
cp .env.example .env
# set JWT_SECRET, TOKEN_ENCRYPTION_KEY, DATABASE_PASSWORD, ADMIN_*, provider keys
docker compose up -d --build
curl http://localhost:8080/health
```

Or without Docker:

```bash
cd backend
# need PostgreSQL + .env exported
./gradlew run
```

Admin: `http://localhost:8080/admin/`

## Quick start (Android)

```bash
cd android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

In the app: **Settings → Backend connection** → your backend base URL → **Test connection**.

## REST API (summary)

| Area | Base |
|------|------|
| Auth | `POST /api/v1/auth/guest\|register\|login\|logout`, `GET /api/v1/auth/me` |
| Providers | `GET /api/v1/providers`, `GET /api/v1/providers/quotas` |
| Mailboxes | `POST/GET /api/v1/mailboxes`, messages, OTP, delete |
| Admin | `POST /api/v1/admin/login`, stats, providers, usage, users, audit |
| Health | `GET /health` |

Full request/response shapes: `backend/src/main/kotlin/.../models/ApiModels.kt` and routes.

## Environment

See `.env.example` and [docs/SECRETS.md](docs/SECRETS.md).  
Hosting notes (incl. **Tmailor IP allowlist**): [docs/FREE_HOSTING.md](docs/FREE_HOSTING.md).  
Deploy steps: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Tests

```bash
cd backend && ./gradlew test   # mock HTTP — no live provider quota burned
```

## License

All rights reserved unless you add a license file.
