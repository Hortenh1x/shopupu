# Cloudflare Tunnel — exposing the dev backend

Gives the locally-running backend a public HTTPS URL. Primary use: letting payment
providers deliver webhooks to `/api/v1/payments/callback`, and letting a remote
frontend reach the API, without deploying.

The tunnel runs as an opt-in `cloudflared` container (compose profile `tunnel`);
the backend itself keeps running on the host via `./mvnw spring-boot:run`.

## Quick tunnel (zero config, ephemeral URL)

```bash
docker compose up -d db                                        # database
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev          # backend on :8080
docker compose --profile tunnel up -d cloudflared              # tunnel
docker compose logs -f cloudflared                             # copy the https://<name>.trycloudflare.com URL
```

The URL changes on every restart — fine for ad-hoc webhook testing.

## Named tunnel (stable, your own hostname)

1. In the Cloudflare Zero Trust dashboard create a tunnel and a public hostname
   routing to `http://host.docker.internal:8080`; copy its token.
2. Put `TUNNEL_TOKEN=...` in `.env` and switch the `cloudflared` service to the
   named-tunnel form (uncomment `environment`/`command` in
   [docker-compose.yml](../docker-compose.yml)).
3. `docker compose --profile tunnel up -d cloudflared`.

## Point the app at the tunnel

Set these (env or `.env`) to the public hostname before starting the backend:

| Variable | Why |
|---|---|
| `SERVER_FORWARD_HEADERS_STRATEGY=framework` | honor `X-Forwarded-Proto/Host/For` — correct `https` URLs and real client IP for the rate limiter |
| `PAYMENT_CALLBACK_URL=https://<host>/api/v1/payments/callback` | the URL the payment gateway calls back |
| `CORS_ALLOWED_ORIGINS=https://<frontend-host>` | allow the browser frontend |

The payment webhook stays fail-closed: it is authenticated by the provider HMAC
signature, not by network origin — the tunnel does not weaken that.

## Notes

- Linux: `extra_hosts: host.docker.internal:host-gateway` (already set) lets the
  container reach the host's `:8080`.
- Not a production deployment — it's a dev/staging convenience. For the real
  shopupu.net deployment see [deploy-cloudflare.md](deploy-cloudflare.md).
