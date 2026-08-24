#!/usr/bin/env bash
# Bootstrap a fresh Oracle Ampere A1 (Ubuntu 24.04 aarch64) for shopupu + shopupu-web.
# Safe to re-run: every step checks its own state first.
#
#   bash oracle-bootstrap.sh            # full setup (with Ollama for AI embeddings)
#   bash oracle-bootstrap.sh --no-ai    # skip Ollama (set AI_ENABLED=false in .env)
#   bash oracle-bootstrap.sh --restore  # also restore ~/shopupu-oracle-transfer/{shopupu.dump,uploads.tar.gz}
#
# See docs/deploy-oracle.md for the full runbook (Cloudflare Tunnel is manual).
set -euo pipefail

REPO_BASE="https://github.com/Hortenh1x"
HOME_DIR="$HOME"
BACKEND_DIR="$HOME_DIR/shopupu"
FRONTEND_DIR="$HOME_DIR/shopupu-web"
TRANSFER_DIR="$HOME_DIR/shopupu-oracle-transfer"
WITH_AI=true
DO_RESTORE=false
for arg in "$@"; do
  case "$arg" in
    --no-ai) WITH_AI=false ;;
    --restore) DO_RESTORE=true ;;
    *) echo "unknown flag: $arg" >&2; exit 1 ;;
  esac
done

step() { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }

step "Base packages"
sudo apt-get update -q
sudo apt-get install -y -q git curl ca-certificates

step "Docker"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"
DOCKER="sudo docker"   # group membership applies after re-login; sudo keeps this run working

step "Node 22 (arm64)"
if ! command -v node >/dev/null || [ "$(node -v | cut -d. -f1 | tr -d v)" -lt 22 ]; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
  sudo apt-get install -y -q nodejs
fi

if $WITH_AI; then
  step "Ollama + bge-m3 (embeddings)"
  command -v ollama >/dev/null || curl -fsSL https://ollama.com/install.sh | sh
  ollama list 2>/dev/null | grep -q bge-m3 || ollama pull bge-m3
fi

step "Clone repos"
[ -d "$BACKEND_DIR/.git" ]  || git clone "$REPO_BASE/shopupu.git"     "$BACKEND_DIR"
[ -d "$FRONTEND_DIR/.git" ] || git clone "$REPO_BASE/shopupu-web.git" "$FRONTEND_DIR"

step "Backend .env"
cd "$BACKEND_DIR"
if [ ! -f .env ]; then
  cp .env.example .env
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -hex 48)|" .env
  sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 16)|" .env
  sed -i "s|^NOTIFICATIONS_FROM_EMAIL=.*|NOTIFICATIONS_FROM_EMAIL=Shopupu <no-reply@shopupu.net>|" .env
  $WITH_AI || printf '\n# set by oracle-bootstrap --no-ai\nAI_ENABLED=false\n' >> .env
  echo "  .env created; JWT_SECRET and DB_PASSWORD generated."
else
  echo "  .env already exists — leaving it untouched."
fi

step "Database"
$DOCKER compose up -d db
until $DOCKER compose exec -T db pg_isready -U shopupu -q; do sleep 2; done

if $DO_RESTORE; then
  step "Restore dump + uploads from $TRANSFER_DIR"
  [ -f "$TRANSFER_DIR/shopupu.dump" ] || { echo "no $TRANSFER_DIR/shopupu.dump" >&2; exit 1; }
  $DOCKER compose exec -T db pg_restore -U shopupu -d shopupu --clean --if-exists < "$TRANSFER_DIR/shopupu.dump"
  [ -f "$TRANSFER_DIR/uploads.tar.gz" ] && tar xzf "$TRANSFER_DIR/uploads.tar.gz" -C "$BACKEND_DIR"
  echo "  restored."
fi

step "Backend image (native arm64 build, first run takes minutes)"
$DOCKER compose --profile prod build app

AI_ON=$(grep -qs '^AI_ENABLED=false' .env && echo false || echo true)
DS_KEY=$(grep -s '^DEEPSEEK_API_KEY=' .env | cut -d= -f2-)
if [ "$AI_ON" = true ] && [ -z "$DS_KEY" ]; then
  echo "  NOT starting app: AI is enabled but DEEPSEEK_API_KEY is empty in .env."
  echo "  Fill it (or set AI_ENABLED=false) and re-run this script."
else
  step "Backend up"
  $DOCKER compose --profile prod up -d app
  sleep 5; curl -sf localhost:8080/actuator/health && echo || echo "  (health not ready yet — check: sudo docker compose logs app)"
fi

step "Frontend build + systemd"
cd "$FRONTEND_DIR"
if [ ! -f .env.production ]; then
  GCID=$(grep -s '^GOOGLE_CLIENT_ID=' "$BACKEND_DIR/.env" | cut -d= -f2-)
  printf 'NEXT_PUBLIC_API_BASE_URL=https://shopupu.net\nNEXT_PUBLIC_GOOGLE_CLIENT_ID=%s\n' "$GCID" > .env.production
fi
npm ci
npm run build
sudo tee /etc/systemd/system/shopupu-web.service >/dev/null <<EOF
[Unit]
Description=shopupu-web (Next.js production server on :3000)
After=network-online.target
Wants=network-online.target

[Service]
User=$USER
WorkingDirectory=$FRONTEND_DIR
ExecStart=$(command -v npm) run start
Restart=always
RestartSec=3
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload
sudo systemctl enable --now shopupu-web

step "Done"
cat <<'EOF'
Осталось руками (см. docs/deploy-oracle.md):
  1. .env: DEEPSEEK_API_KEY / GOOGLE_CLIENT_ID / RESEND_API_KEY / BOOTSTRAP_ADMIN_* — затем re-run скрипта.
  2. Cloudflare Zero Trust: создать туннель, поставить коннектор (команду даст дашборд),
     4 public hostnames: api/* и uploads/* -> localhost:8080, root и www -> localhost:3000.
  3. Google Console: https://shopupu.net в Authorized JavaScript origins.
  4. Resend: верифицировать домен shopupu.net.
EOF
