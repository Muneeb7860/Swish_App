# Swish — Closed-Beta on the Mac ("Mac as server")

One-command, restart-resilient demo/beta. Backend runs native (arm64); infra + frontend are containers.

## Start / Stop
```bash
bash demo/start.sh     # keep-awake + colima + infra + frontend + backend + tunnel
bash demo/stop.sh      # stop everything (Postgres data persists in the named volume)
```
- **Local:** http://localhost:3000  — login **beta / m8FdHvyLDP**
- **Public (remote stakeholders):** printed by start.sh (`*.trycloudflare.com`), same basic-auth gate.

## What runs
| Piece | How | Port |
|---|---|---|
| Postgres (timescale, **persistent**) | container | 5433 |
| Redis / Mongo / Kafka | containers | 6379 / 27017 / 9092 |
| Backend (Spring Boot, 35 migrations) | native `spring-boot:run` | 8083 |
| Frontend (5 MFEs, same-origin, basic-auth) | container nginx | 3000 |

## Prereqs (one-time)
- `brew install cloudflared`  (public URL; otherwise local-only)
- JDK 17 at `microsoft-17.jdk`; colima installed.

## ⚠️ Keep it up for a beta
- The Mac **must not sleep** — `caffeinate` (start.sh handles it while running) **and** set System Settings → Energy → "prevent sleeping" / `sudo pmset -a disablesleep 1`.
- colima containers are `restart: unless-stopped` (survive Docker restarts); the **native backend does NOT auto-restart** — for true 24/7, wrap it in `launchd` or re-run start.sh after a reboot.

## Change the beta password
```bash
printf "beta:%s\n" "$(openssl passwd -apr1 'NEWPASS')" > demo/.htpasswd   # no rebuild; just restart frontend
docker compose -f docker-compose.demo.yml restart frontend
```

## Security caveats (closed beta only — not public production)
- Exposes a dev Mac via tunnel → keep invite-only (basic-auth), share the URL privately.
- Not HA; single machine; data only in the local Postgres volume (back it up if the beta matters).
