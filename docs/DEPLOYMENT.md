# Deployment

## 1. Local / single host (Docker)

```bash
cp .env.example .env
# edit .env: JWT_SECRET, TOKEN_ENCRYPTION_KEY, DATABASE_PASSWORD, ADMIN_*, FCE/TMAILOR keys
openssl rand -hex 32   # JWT_SECRET
openssl rand -base64 32 # TOKEN_ENCRYPTION_KEY

docker compose up -d --build
curl -fsS http://localhost:8080/health
```

Admin UI: `http://localhost:8080/admin/`  
Sign in with `ADMIN_EMAIL` / `ADMIN_PASSWORD` from `.env`.

## 2. Tmailor IP allowlist

1. Note your server **outbound** IP (`curl -4 ifconfig.me` on the host).
2. Add it in the Tmailor dashboard allowlist.
3. Without this, mailbox create returns `ip_not_allowed` (mapped from HTTP 403).

FreeCustom has no IP allowlist but enforces ~1 req/s and monthly quota.

## 3. Android app

1. Set backend URL in **Settings → Backend connection** (or ship a build with `DEFAULT_BASE_URL` in `android/core/build.gradle.kts` / `android/app/build.gradle.kts`).
2. Build APK:
   ```bash
   cd android
   ./gradlew assembleDebug    # or assembleRelease with keystore env
   ```
3. CI: `.github/workflows/android-debug.yml` uploads the APK artifact.

## 4. GitHub Actions

| Workflow | Trigger | Output |
|----------|---------|--------|
| `android-debug.yml` | push/PR on `android/**` | debug APK artifact |
| `android-release.yml` | tag `v*` or manual | release APK artifact |
| `backend-test.yml` | push/PR on `backend/**` | JUnit report artifact |
| `backend-deploy.yml` | push to `main` on backend paths | Docker image (+ optional SSH deploy) |

JDK 17 everywhere. No secrets in logs.

## 5. Free hosting

See [FREE_HOSTING.md](FREE_HOSTING.md). Prefer a fixed-IP VM if you rely on Tmailor.

## 6. Health & ops

- `GET /health` — status, DB, providers  
- `GET /` — service index  
- Provider keys only in env — rotate without rebuilding the app  
- Audit log: admin UI → Audit tab  
