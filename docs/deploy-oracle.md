# Деплой shopupu + shopupu-web на Oracle Cloud (Ampere A1, Ubuntu 24.04 aarch64)

Та же архитектура, что в [deploy-cloudflare.md](deploy-cloudflare.md) — один сервер,
один домен, path-маршрутизация через Cloudflare Tunnel:

```
https://shopupu.net/api/*      ->  backend  (localhost:8080, Docker, prod profile)
https://shopupu.net/uploads/*  ->  backend  (картинки товаров)
https://shopupu.net/*          ->  frontend (localhost:3000, next start, systemd)
https://www.shopupu.net/*      ->  frontend
```

## Инстанс

- **ARM подходит.** Весь стек multi-arch: `maven:3.9-eclipse-temurin-25` /
  `eclipse-temurin:25-jre`, `pgvector/pgvector:pg18`, Ollama, cloudflared, Node —
  у всех есть arm64. Образ бекенда собирается прямо на сервере (`docker compose
  build`), поэтому несовпадения архитектур нет по построению. **Не пушьте образ
  с x86-машины** без `buildx --platform linux/arm64`.
- **2 OCPU / 12 GB достаточно**: JVM ограничена 3 GB (`mem_limit` в compose),
  Postgres ~0.5 GB, Ollama с `bge-m3` ~1.5 GB, Next.js ~0.5 GB. Если тенанси
  позволяет Always Free (лимит A1 — 4 OCPU / 24 GB суммарно) — поднимите до
  4/24: быстрее сборки Maven/Next и эмбеддинги. Не обязательно.
- **Boot volume 50 GB достаточно** (образы + модель + БД ≈ 10–12 GB).
- **Никаких ingress-портов в OCI открывать не нужно** — туннель ходит наружу
  (443 outbound). Security lists / iptables не трогаем; 8080 и 3000 остаются
  loopback-only. SSH (22) уже открыт по умолчанию.

> **Быстрый путь:** шаги 0–6 автоматизирует
> [deploy/oracle-bootstrap.sh](../deploy/oracle-bootstrap.sh) — на сервере:
> `curl -fsSL https://raw.githubusercontent.com/Hortenh1x/shopupu/main/deploy/oracle-bootstrap.sh | bash`
> (флаги: `--no-ai`, `--restore`). Скрипт можно перезапускать; ниже — те же шаги
> для ручного прохода. Cloudflare Tunnel (§7) в любом случае руками.

## 0. Базовый софт

```bash
sudo apt update && sudo apt install -y git curl
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
# Node 22 (arm64) для фронта
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt install -y nodejs
```

## 1. Ollama (эмбеддинги; пропустите, если ставите AI_ENABLED=false)

```bash
curl -fsSL https://ollama.com/install.sh | sh   # ставит systemd-сервис на 127.0.0.1:11434
ollama pull bge-m3
```

## 2. Код на сервер

```bash
cd ~ && git clone <ваш-remote>/shopupu && git clone <ваш-remote>/shopupu-web
# если репозитории не запушены — с домашней машины:
#   rsync -a --exclude target --exclude node_modules --exclude .next \
#     ~/Idea\ Projects/shopupu ~/Idea\ Projects/shopupu-web ubuntu@<IP>:~/
```

## 3. Боевой `.env` бекенда (`~/shopupu/.env`)

```bash
cd ~/shopupu && cp .env.example .env
```

Минимум для старта (остальное — по [.env.example](../.env.example)):

```env
JWT_SECRET=<openssl rand -hex 48>
DB_PASSWORD=<openssl rand -hex 16>        # bootstrap-суперпользователь БД (initdb, бэкапы, миграции ролей)
DB_RUNTIME_PASSWORD=<openssl rand -hex 24> # роль shopupu_runtime — под ней работает приложение (см. §4)
MFA_ENCRYPTION_KEY=<openssl rand -base64 32> # без него вход ADMIN/MANAGER отвечает 503 MFA_CONFIGURATION_REQUIRED
DEEPSEEK_API_KEY=<ключ>                   # или AI_ENABLED=false
GOOGLE_CLIENT_ID=<web client id>          # пусто = кнопка Google скрыта
NOTIFICATION_PROVIDER=disabled            # smtp | resend — только после проверки отправителя (docs/external-integrations.md)
BOOTSTRAP_ADMIN_ENABLED=true              # одноразово: создать первого админа
BOOTSTRAP_ADMIN_EMAIL=<ваш email>
BOOTSTRAP_ADMIN_PASSWORD=<временный пароль, ≥15 символов>
SERVER_FORWARD_HEADERS_STRATEGY=native    # за Cloudflare Tunnel — см. ниже
SERVER_TOMCAT_REMOTEIP_REMOTE_IP_HEADER=CF-Connecting-IP
```

`PAYMENTS_DEFAULT_PROVIDER` остаётся `stub` (локальная симуляция), пока не подключён
Stripe **test mode** (`STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET`, инструкция в
[external-integrations.md](external-integrations.md)); live-провайдеры приложение отвергает.
Клиентский IP за cloudflared: origin слушает только loopback, а Cloudflare всегда перезаписывает
`CF-Connecting-IP`, поэтому `native` + этот заголовок дают rate-limiter'у настоящий адрес, а
`X-Forwarded-For` (его первое значение задаёт клиент) для этого не годится. Без такой настройки
все посетители делят один bucket на 127.0.0.1.

Миграции схемы приложение в проде **не выполняет** (`FLYWAY_ENABLED=false`); их запускает
отдельная роль-мигратор из файла `~/shopupu/.migrator.env` (`chmod 600`, в git не попадает):

```env
FLYWAY_URL=jdbc:postgresql://localhost:5432/shopupu
FLYWAY_USER=shopupu_migrator
FLYWAY_PASSWORD=<openssl rand -hex 24>
```

## 4. Поднять бекенд

```bash
docker compose up -d db
# роли: shopupu_migrator владеет схемой и мигрирует, shopupu_runtime — только DML
docker exec -i shopupu-db-1 psql -X -v ON_ERROR_STOP=1 -U shopupu -d shopupu < ops/bootstrap-db.sql
docker exec -i shopupu-db-1 psql -X -U shopupu -d shopupu <<'SQL'
ALTER ROLE shopupu_migrator LOGIN PASSWORD '<FLYWAY_PASSWORD из .migrator.env>';
ALTER ROLE shopupu_runtime  LOGIN PASSWORD '<DB_RUNTIME_PASSWORD из .env>';
ALTER DEFAULT PRIVILEGES FOR ROLE shopupu_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO shopupu_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE shopupu_migrator IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO shopupu_runtime;
SQL
# миграции — Maven в контейнере (JDK/Maven на хосте не нужны), под ролью-мигратором
docker run --rm --network host --env-file .migrator.env -v "$PWD:/w" -w /w -v shopupu-m2:/root/.m2 \
  maven:3.9-eclipse-temurin-25 mvn -B -q flyway:migrate flyway:validate
docker exec -i shopupu-db-1 psql -X -v ON_ERROR_STOP=1 -U shopupu -d shopupu < ops/grant-runtime.sql
VCS_REF=$(git rev-parse HEAD) docker compose build app   # первая сборка на ARM ~5–10 мин; VCS_REF попадает в label образа
docker compose --profile prod up -d app
curl -s localhost:8080/actuator/health    # {"status":"UP"}
```

Приложение при старте проверяет роль (`ProductionDatabaseGuard`): суперпользователь или
владелец схемы как `DB_RUNTIME_USERNAME` — отказ старта. Существующую БД, где приложение
работало от bootstrap-аккаунта, переводите по процедуре в
[database-recovery.md](database-recovery.md) (перенос владения объектов на мигратора, затем
гранты) — пустой `bootstrap-db.sql` на ней не запускать.

После первого входа админом (первый вход требует TOTP-энролмент — сохраните recovery-коды):
выключите `BOOTSTRAP_ADMIN_ENABLED`, смените пароль.

## 5. Данные

- **Перенос с домашней машины:** там — `docker compose exec db pg_dump -U shopupu
  -Fc shopupu > shopupu.dump` и скопировать `uploads/`; на сервере —
  `docker compose exec -T db pg_restore -U shopupu -d shopupu --clean < shopupu.dump`
  и положить `uploads/` рядом с compose (том `./uploads:/app/uploads`).
  Дамп и uploads переносятся **вместе** (URL картинок указывают на файлы).
- **Или с нуля:** сиды `scripts/seed-generated-clothing-catalog.mjs` +
  `scripts/generated-reviews/`, затем `POST /api/v1/admin/ai/embeddings/backfill`.

## 6. Фронтенд

```bash
cd ~/shopupu-web
printf 'NEXT_PUBLIC_API_BASE_URL=https://shopupu.net\nNEXT_PUBLIC_GOOGLE_CLIENT_ID=<тот же client id>\n' > .env.production
npm ci && npm run build
sudo tee /etc/systemd/system/shopupu-web.service >/dev/null <<'EOF'
[Unit]
Description=shopupu-web (Next.js production server on :3000)
After=network-online.target
Wants=network-online.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/shopupu-web
ExecStart=/usr/bin/npm run start
Restart=always
RestartSec=3
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload && sudo systemctl enable --now shopupu-web
```

(Это адаптация [deploy/shopupu-web.service](../deploy/shopupu-web.service) под
пользователя/пути сервера.)

## 7. Cloudflare Tunnel

1. Zero Trust → Networks → Tunnels → **Create a tunnel** (`shopupu-oracle`),
   выберите Debian/arm64 — дашборд даст готовую команду установки коннектора
   (systemd-сервис на сервере).
2. **Если хостнеймы shopupu.net ещё привязаны к старому туннелю на домашней
   машине — сначала удалите их там** (public hostname живёт ровно на одном туннеле).
3. Public Hostname нового туннеля, в этом порядке (специфичные пути первыми):

| # | Subdomain | Domain | Path | Service |
|---|---|---|---|---|
| 1 | — | shopupu.net | `api/*` | `http://localhost:8080` |
| 2 | — | shopupu.net | `uploads/*` | `http://localhost:8080` |
| 3 | — | shopupu.net | — | `http://localhost:3000` |
| 4 | `www` | shopupu.net | — | `http://localhost:3000` |

DNS-CNAME создаются автоматически (зона shopupu.net должна быть активна в этом
же аккаунте Cloudflare).

## 8. Смоук (план §3)

```bash
curl -s "https://shopupu.net/api/v1/catalog/products?size=1" | head -c 300  # JSON
curl -s "https://shopupu.net/actuator/health"                               # 404 (приватно — верно)
```

Затем руками: регистрация (+письмо верификации) → Google-логин → каталог →
корзина → checkout (повтор с тем же Idempotency-Key = тот же заказ) → оплата
(stub) → статусы → refund админом. Для Google-логина добавьте
`https://shopupu.net` в **Authorized JavaScript origins** клиента в Google
Console; для писем — верифицируйте домен в Resend.

## Day-2

- Обновление бекенда: `git pull && VCS_REF=$(git rev-parse HEAD) docker compose build app`, затем миграции под мигратором
  (команда `docker run … mvn flyway:migrate flyway:validate` из §4; V-файлы аддитивны, но
  `ProductionDatabaseGuard`/Hibernate `validate` не пустят приложение на несмигрированную схему),
  `docker compose --profile prod up -d app`, `curl localhost:8080/actuator/health`.
- Обновление фронта: `git pull && npm ci && npm run build && sudo systemctl restart shopupu-web`.
- Бекапы: `~/.local/share/shopupu-ops/nightly-backup.sh` по cron (03:40 UTC) кладёт
  `pg_dump -Fc` + `uploads.tar` + `SHA256SUMS` в `~/backups/shopupu/<ts>/` (14 копий);
  off-host копию забирайте `scp` на доверенную машину. Восстановление и репетиция —
  [database-recovery.md](database-recovery.md).
- `docker system prune -f` изредка (build-кэш на 50 GB диске).
- Реальные платежи: см. «Real payments later» в [deploy-cloudflare.md](deploy-cloudflare.md).
- Инциденты: [runbook.md](runbook.md) (там же процедура reconciliation-mismatch).
