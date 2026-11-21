# ApraxTMail

Custom-domain **temporary email** for Android:

- **Android** — Kotlin, Jetpack Compose, Material 3, Hilt. Dashboard, inbox, custom
  domain management with DNS verification, email generator, HTML/plain message
  viewer, and a theme system (Light / Dark / System / Liquid Glass + accents).
- **Backend** — Cloudflare Worker (Hono + TypeScript) with **D1** (SQLite) for
  data, **R2** for raw emails/attachments, and a **Cron Trigger** that expires
  messages. Inbound mail is delivered by **Emailit** via signed webhooks.
- **No nameserver changes** — domains keep their existing DNS host; you only add
  MX/TXT/CNAME records that Emailit's API tells you to add.

API keys and secrets never reach the Android app.

## Repository layout

```
android/                 # Gradle multi-module app (app, core, feature)
workers/                 # Cloudflare Worker backend
  src/                   # Hono routes, Emailit client, DNS checks, webhook
  migrations/            # D1 SQL migrations
  test/                  # node:test unit tests (51)
  wrangler.toml          # Worker config (D1, R2, cron, vars)
docs/                    # Deployment guide
.github/workflows/       # Android APK builds + Worker CI/deploy
.env.example             # Configuration reference (not read at runtime)
```

## Backend quick start (Cloudflare Worker)

Prereqs: Node 20+, a Cloudflare account, and an Emailit account.

```bash
cd workers
npm ci                 # use npm install --no-bin-links if symlinks are blocked (e.g. sdcard)
npm run typecheck
npm test               # tsc build + node --test
```

One-time Cloudflare setup (see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for details):

```bash
npx wrangler d1 create aprax_tmail          # paste database_id into wrangler.toml
npx wrangler r2 bucket create aprax-tmail-mail
npx wrangler d1 execute aprax_tmail --remote --file=migrations/0001_init.sql
npx wrangler secret put JWT_SECRET          # openssl rand -hex 32
npx wrangler secret put WEBHOOK_SECRET      # whsec_... from Emailit dashboard
npx wrangler secret put EMAILIT_API_KEY     # Emailit dashboard API key
npx wrangler deploy
```

Health check: `GET https://aprax-tmail.<you>.workers.dev/health`

### Emailit wiring

1. Emailit dashboard → **API key** → `wrangler secret put EMAILIT_API_KEY`.
2. Emailit dashboard → **Webhooks** → create endpoint
   `https://<your-worker>/webhooks/emailit` → copy the signing secret →
   `wrangler secret put WEBHOOK_SECRET`.
3. In the app: **Add Domain** — the worker registers the domain with Emailit's
   API and shows the **real** MX/TXT/CNAME records to add at your DNS host
   (never change nameservers).
4. **Verify** runs live DNS checks (DoH) against each record's own host and syncs
   status back to Emailit.

Inbound mail arrives at `<local>@inbound.<domain>` (Emailit inbound MX) or
`<local>@<domain>`; both resolve to your addresses.

## Android quick start

```bash
cd android
sh gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
sh gradlew test                 # unit tests
```

Set the backend URL in **Settings → Backend connection** (defaults to the
deployed Worker URL). CI builds debug APKs on every push touching `android/**`
and publishes release APKs on `v*` tags.

## API summary (Worker)

| Area | Endpoint |
|------|----------|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` |
| Domains | `GET/POST /api/domains`, `GET/DELETE /api/domains/:id`, `POST /api/domains/:id/verify` |
| Addresses | `GET/POST /api/domains/:domainId/addresses`, `DELETE /api/addresses/:id` |
| Messages | `GET /api/messages`, `GET/DELETE /api/messages/:id`, `POST /api/messages/:id/read`, `GET /api/messages/:id/attachments/:attachmentId` |
| Webhook | `POST /webhooks/emailit` (HMAC-signed, Emailit → Worker) |
| Health | `GET /health` |

Auth endpoints return a JWT (HS256, 30 days); send `Authorization: Bearer <token>`.

## Configuration & secrets

Everything is documented in [`.env.example`](.env.example).
Secrets live only in Cloudflare (`wrangler secret put`) and GitHub Actions
(`CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`) — never in source, commits,
or the app.

## CI

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `android-debug.yml` | push/PR touching `android/**` | debug APK artifact |
| `android-release.yml` | tag `v*` | release APK + GitHub Release |
| `worker.yml` | push/PR touching `workers/**` | typecheck + unit tests; on `main` also deploys (or dry-runs if secrets absent) |
