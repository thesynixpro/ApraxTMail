# Free hosting findings

Honest summary for Aprax TMaii (Ktor + PostgreSQL + optional Docker).

## Backend options

| Host | Free tier reality | Docker? | Fit |
|------|-------------------|---------|-----|
| **Render** | Free web services spin down; free Postgres (90 days, then paid or destroy) | Yes (native or Docker) | Good for demos; cold starts hurt inbox polling |
| **Fly.io** | Free allowances reduced over time; may need card for new apps | Yes | Strong if you can keep a small VM; watch limits |
| **Railway** | Trial credit, not perpetual free | Yes | Fine until credit runs out |
| **Oracle Cloud Always Free** | Always-free ARM VM (if available in your region) | Yes | Best free self-host; you manage updates |
| **Cyclic / Adaptable** | Serverless-ish free tiers with constraints | Partial | Awkward for sticky sessions + Postgres |
| **GitHub Actions + your PC** | Not a host | — | CI only |

**Recommendation:** ship Docker (`docker-compose.yml`) so the same image runs on Render/Fly/Oracle. For production with Tmailor, prefer a **stable outbound IP** (paid VPS or Always Free VM) so the IP allowlist does not break when the platform rotates egress IPs.

## Tmailor outbound IP allowlist (critical)

- Free plan **enforces an IP allowlist** for API calls from your server.
- Serverless platforms (Render free, Vercel-style) often use **shared/rotating egress IPs** → `IpNotAllowed` errors.
- You must copy your host’s outbound IP into the Tmailor dashboard.
- On platforms that rotate IPs, **FreeCustom** remains the reliable provider; Tmailor may only work on a fixed-IP VPS.

## Android app hosting

- The APK is built in GitHub Actions and downloaded as an artifact (or attached to a Release).
- No Play Store account is required for sideloading; for Play you need a developer account and signing keys in secrets.

## What is *not* free

- Stable IPv4 + always-on process (real production)
- Managed Postgres with backups
- Custom domains + TLS (usually free via Caddy/Let’s Encrypt on your own VM)

## Practical free stack (dev/demo)

1. `docker compose up` on Oracle Always Free VM or laptop  
2. Caddy or platform TLS in front  
3. Tmailor allowlist set to that VM’s IP  
4. GitHub Actions for APKs  

See `docs/DEPLOYMENT.md` for step-by-step deploy.
