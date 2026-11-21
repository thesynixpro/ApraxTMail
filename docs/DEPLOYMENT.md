# Deployment — Cloudflare Worker backend

The backend runs entirely on Cloudflare Workers. No servers, no Docker, no
Railway. This guide goes from zero to a live Worker wired to Emailit.

## 0. Prerequisites

- Node.js 20+ and npm
- A [Cloudflare](https://dash.cloudflare.com) account
- An [Emailit](https://emailit.com) account (API key + webhook access)
- This repo cloned locally

All commands run from `workers/` unless noted.

```bash
cd workers
npm ci        # if symlinks are blocked on your filesystem: npm install --no-bin-links
```

## 1. Create D1 (database) and R2 (email storage)

```bash
npx wrangler d1 create aprax_tmail
# → copy the printed database_id into wrangler.toml (replace REPLACE_WITH_D1_DATABASE_ID)

npx wrangler r2 bucket create aprax-tmail-mail
```

Apply the schema:

```bash
npx wrangler d1 execute aprax_tmail --remote --file=migrations/0001_init.sql
```

Verify:

```bash
npx wrangler d1 execute aprax_tmail --remote --command "SELECT name FROM sqlite_master WHERE type='table'"
```

You should see `users`, `domains`, `addresses`, `messages`, `attachments`,
`rate_limits`.

## 2. Secrets

Never commit these. Each command prompts for the value interactively.

```bash
npx wrangler secret put JWT_SECRET         # openssl rand -hex 32
npx wrangler secret put EMAILIT_API_KEY    # Emailit dashboard → API
npx wrangler secret put WEBHOOK_SECRET     # Emailit dashboard → Webhooks (whsec_...)
```

| Secret | Purpose | Until set |
|--------|---------|-----------|
| `JWT_SECRET` | Signs/verifies auth tokens (HS256) | Auth returns 500 |
| `EMAILIT_API_KEY` | Domain register/verify + fetching full emails | Domain add returns 503; webhook fetch fails |
| `WEBHOOK_SECRET` | Verifies `X-Emailit-Signature` on inbound webhooks | Webhook returns 503 |

Non-secret vars live in `wrangler.toml` (`PUBLIC_BASE_URL`,
`EMAILIT_API_URL`, `MESSAGE_TTL_DAYS`).

## 3. Deploy

```bash
npx wrangler deploy
# or: npm run deploy
```

Smoke test:

```bash
curl -fsS https://aprax-tmail.<your-subdomain>.workers.dev/health
# {"status":"ok","service":"aprax-tmail","version":"2.0.0"}
```

Update `PUBLIC_BASE_URL` in `wrangler.toml` to the real URL and redeploy.

## 4. Emailit wiring

### 4.1 API key

Emailit dashboard → **API** → create/copy key → step 2 above.

### 4.2 Webhook

1. Emailit dashboard → **Webhooks** → add endpoint:
   `https://<your-worker>/webhooks/emailit`
2. Copy the signing secret (`whsec_...`) → `wrangler secret put WEBHOOK_SECRET`.
3. Trigger Emailit's test delivery (or use `POST /webhooks/{id}/test`).
   Expected response: `{"received":N,"dropped":M}` — no `failed` key.

**How verification works:** the worker checks `X-Emailit-Signature` =
hex(HMAC-SHA256(`${timestamp}.${rawBody}`)) with a ±300 s window, constant-time
compare. The payload is an **array of envelope events**; for each
`email.received` the worker fetches the full body/attachments from
`GET /emails/{id}`, resolves the recipient against your addresses, and stores
everything in D1 + R2. Any failed event → HTTP 502 so Emailit retries
(deliveries retry for ~3 days).

### 4.3 Domains

In the Android app (or any API client):

1. `POST /api/domains {"name":"example.com"}` — the worker calls Emailit's
   `POST /domains` and returns the **actual** DNS records Emailit requires
   (typically inbound MX `inbound.example.com → inbound.emailitmail.com`,
   SPF TXT, DKIM TXT, optional DMARC/CNAME).
2. Add those records **at your existing DNS host**. Do not change nameservers.
3. `POST /api/domains/:id/verify` — live DoH lookups per record host; updates
   local status and best-effort syncs Emailit's `verification_status`.

## 5. Cron (message expiration)

`wrangler.toml` schedules `17 4 * * *` (daily). The handler deletes expired
messages (`expires_at < now`, default TTL 30 days from `MESSAGE_TTL_DAYS`),
their R2 raw/attachment objects, and stale rate-limit rows.

Check runs:

```bash
npx wrangler tail --format json   # watch logs; cron logs `cron_completed`
```

## 6. CI/CD (GitHub Actions)

`worker.yml` runs typecheck + unit tests on every push/PR touching `workers/**`.
On pushes to `main` it deploys if these repository secrets are set, otherwise it
runs `wrangler deploy --dry-run`:

| GitHub secret | Value |
|---------------|-------|
| `CLOUDFLARE_API_TOKEN` | Custom token: Account → Workers Scripts:Edit, D1:Edit, R2:Edit |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare dashboard → account home |

## 7. Operations

- **Logs:** `npx wrangler tail` (structured events: `webhook_event_failed`,
  `cron_completed`, `unhandled_error`, `emailit_*`).
- **Database:** `npx wrangler d1 execute aprax_tmail --remote --command "SELECT ..."`
- **Backups:** `npx wrangler d1 export aprax_tmail --remote --output backup.sql`
- **Rate limits:** fixed windows in `rate_limits` (register 5/hr/IP,
  login 10/hr/IP, domain add 10/hr/user, verify 30/min/user); purged by cron.

## 8. Teardown

```bash
npx wrangler delete aprax-tmail
npx wrangler d1 delete aprax_tmail        # prompts to confirm
npx wrangler r2 bucket delete aprax-tmail-mail
```
