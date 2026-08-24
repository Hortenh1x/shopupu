# Deploying shopupu to shopupu.net via Cloudflare Tunnel

Both apps run on this machine behind **one domain** with path routing on a
single Cloudflare Tunnel:

```
https://shopupu.net/api/*      ->  backend  (localhost:8080, Spring Boot, prod profile)
https://shopupu.net/uploads/*  ->  backend  (localhost:8080, product images)
https://shopupu.net/*          ->  frontend (localhost:3000, next start)
https://www.shopupu.net/*      ->  frontend
```

Same-origin API ⇒ no CORS pain. Anything not matching `/api/*` or `/uploads/*`
(swagger, actuator, …) lands on the frontend and 404s — effectively private.

## Already prepared (in this repo / on this machine)

- `.env`: `JWT_SECRET` + `DEEPSEEK_API_KEY` present, `DB_PASSWORD` synced to the
  compose value. `.env` is gitignored.
- `docker-compose.yml`: new **`app`** service (profile `prod`) — prod Spring
  profile, `network_mode: host` (reaches loopback-bound Ollama :11434 and
  Postgres :5432 as localhost; cloudflared routes to localhost:8080),
  `./uploads` volume, `restart: unless-stopped`. The `db` service is now bound
  to `127.0.0.1:5432` only and restarts automatically.
- Production image built (`docker compose build app`); stack started with
  `docker compose --profile prod up -d app`.
- Existing `product_images.url` rows rewritten `http://localhost:8080/uploads`
  → `https://shopupu.net/uploads` ([scripts/switch-uploads-domain.sql](../scripts/switch-uploads-domain.sql),
  reversible). New uploads use `PUBLIC_UPLOADS_BASE_URL` automatically.
- Frontend: `shopupu-web/.env.production` with
  `NEXT_PUBLIC_API_BASE_URL=https://shopupu.net`; production build done
  (`npm run build`); `next start` serving on :3000.
- systemd unit prepared: [deploy/shopupu-web.service](../deploy/shopupu-web.service).

## Your steps

### 1. Route the domain through your existing tunnel (Cloudflare dashboard)

Zero Trust → Networks → Tunnels → **the tunnel that already runs on this
machine** → Public Hostname → Add, in this order (specific paths first):

| # | Subdomain | Domain | Path | Service |
|---|---|---|---|---|
| 1 | (empty) | shopupu.net | `api/*` | `http://localhost:8080` |
| 2 | (empty) | shopupu.net | `uploads/*` | `http://localhost:8080` |
| 3 | (empty) | shopupu.net | (empty) | `http://localhost:3000` |
| 4 | `www` | shopupu.net | (empty) | `http://localhost:3000` |

No new tunnel/token needed; DNS CNAMEs are created automatically; the running
`cloudflared` picks the config up without a restart. (`shopupu.net` must be an
active zone in the same Cloudflare account.)

If your `cloudflared` runs in Docker rather than natively, use
`http://host.docker.internal:8080` / `:3000` as service URLs instead.

### 2. Make the frontend survive reboots (needs sudo)

```bash
sudo cp "/home/horten/Idea Projects/shopupu/deploy/shopupu-web.service" /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now shopupu-web
```

(Ollama, Docker and cloudflared are already system services; `db`/`app` restart
with Docker via `restart: unless-stopped`.)

### 3. Verify

```bash
curl -s "https://shopupu.net/api/v1/catalog/products?size=1" | head -c 300   # JSON
curl -s "https://shopupu.net/actuator/health"                                # 404 page (private — correct)
```

Open https://shopupu.net — catalog with images, reviews, ✦ Stylist chat
(DeepSeek). Password-reset / verification emails link to https://shopupu.net.

## Day-2 notes

- **Dev vs prod on one machine:** the prod backend occupies :8080 and
  `next start` occupies :3000. To develop: `docker compose --profile prod stop app`,
  `sudo systemctl stop shopupu-web`, then run dev as usual. Bring prod back with
  `docker compose --profile prod up -d app && sudo systemctl start shopupu-web`.
- **Frontend changes:** rebuild + restart —
  `npm run build && sudo systemctl restart shopupu-web`
  (`NEXT_PUBLIC_*` is baked at build time).
- **Backend changes:** `docker compose build app && docker compose --profile prod up -d app`.
- **Image URLs:** dev-uploaded images point at the public domain now; they load
  once the tunnel is live. Rollback SQL is in the script header.
- **Real payments later:** set the provider keys + `PAYMENT_CALLBACK_SECRET`,
  switch `PAYMENTS_DEFAULT_PROVIDER`; the webhook URL
  `https://shopupu.net/api/v1/payments/callback` is already routed and
  HMAC-verified (fail-closed).
- Bootstrap admin: change the seeded admin password if you haven't.
