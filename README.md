# Shopupu — Clothing Shop Backend

Production-oriented Spring Boot REST API for an online clothing store: product
variants (size/color/SKU), inventory with reservations, an order state machine,
idempotent checkout, promo codes, moderated reviews and pluggable payment
gateways (monobank for UAH, Fondy for EUR, stub for local dev).

API-only backend — the web frontend is a separate project (`../shopupu-web`).

## Tech Stack

- Java 25, Spring Boot 4.0.x (GA)
- Spring Security (stateless JWT, deny-by-default), Bucket4j rate limiting
- Spring Data JPA / Hibernate, PostgreSQL (+pgvector), Flyway
- Caffeine cache, Springdoc OpenAPI, JJWT, Lombok, MapStruct
- AI (опционально): эмбеддинги Ollama `bge-m3` / Voyage + LLM DeepSeek `deepseek-v4-flash` (OpenAI-совместимый, thinking off), stub-провайдеры по умолчанию
- JUnit 5, Mockito, Testcontainers, JaCoCo, Spotless

## Quick Start (local)

```bash
# 1. PostgreSQL
docker compose up -d db

# 2. Run with the dev profile (provides a dev-only JWT secret)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger UI: <http://localhost:8080/swagger> · OpenAPI: <http://localhost:8080/v3/api-docs>
Health: <http://localhost:8080/actuator/health>

Without the `dev` profile the application **refuses to start unless `JWT_SECRET`
is set** — this is intentional (no secrets ship in the repo). All environment
variables are documented in [`.env.example`](.env).

Run tests (unit + Testcontainers integration; needs Docker):

```bash
./mvnw test          # tests only
./mvnw verify        # tests + JaCoCo coverage gate + Spotless check
```

Build a production image:

```bash
docker build -t shopupu .
```

## Profiles

| Profile | Purpose |
|---|---|
| (default) | strict: no JWT secret fallback, prod-like settings |
| `dev` | local development: dev-only JWT secret, verbose logging |
| `prod` | production hardening: restricted actuator, INFO logs, no auto-baseline |
| `test` | used by the test suite (Testcontainers PostgreSQL) |

## Architecture

Modular monolith, **package-by-feature** under `com.example.shopupu`. Every
domain module has the same internal shape (`controller` · `dto` · `entity` ·
`repository` · `service`, plus `mapper` / `gateway` / `model` where needed).

| Module | Responsibility |
|---|---|
| `auth` | register / login / refresh / logout, password reset & email verification |
| `identity` | users, roles, address book, wishlist, consent journal, GDPR |
| `catalog` | categories, brands, products, variants, images, filtered search |
| `inventory` | stock / reserved per SKU, atomic movements, oversell prevention |
| `cart` | user & guest carts (`X-Cart-Token`), merge on login |
| `orders` | idempotent checkout, order state machine, status history, snapshots |
| `payments` | monobank / Fondy / stub gateways, HMAC webhooks, refunds |
| `promo` | promo codes with atomic redemption accounting |
| `shipping` | methods, rates, free-shipping threshold, address snapshot |
| `reviews` | verified-purchase reviews, pre-moderation, sanitization |
| `ai` | semantic search (pgvector), «похожие»/«с этим покупают», саммари отзывов |
| `notifications` | domain events → async email (SMTP or logging fallback) |
| `common` · `config` · `security` | cross-cutting: audit, errors, storage, JWT, rate limiting, properties |

```
src/main/java/com/example/shopupu/
  <module>/{controller,dto,entity,repository,service,mapper,gateway,model}
  ShopupuApplication.java
src/main/resources/
  application{,-dev,-prod}.yml      # env-driven config, fail-fast
  db/migration/V1..V14__*.sql       # Flyway — the single source of schema truth
  messages*.properties              # i18n (en / uk)
src/test/java/...                    # *Test (unit) + *IT (Testcontainers)
docs/                                # ADRs, ER diagram, runbook
```

## Domain Model

```
Category (иерархия, slug)          Brand
        \                         /
         Product (модель: slug, gender, season, material, SEO, soft delete)
          ├── ProductImage (порядок, alt)
          └── ProductVariant (SKU, size, color, price, old_price, @Version)
                └── Inventory (stock, reserved, @Version) + InventoryMovement (журнал)

Cart → CartItem(variant)
Order (order_number, идемпотентный чекаут, state machine)
  ├── OrderItem (снапшот: sku/size/color/brand/title/price)
  └── OrderStatusHistory (кто/когда сменил статус)
Payment (+ PaymentEvent), Shipment (+ снапшот адреса)
PromoCode (+ атомарные PromoRedemption)
Review (PENDING → APPROVED/REJECTED, verified purchase, санитизация)
```

Order lifecycle: `CREATED → PENDING_PAYMENT → PAID → PROCESSING → SHIPPED →
DELIVERED → COMPLETED`, plus `CANCELLED` (до оплаты, резерв освобождается) and
`REFUNDED` (после оплаты, склад пополняется). Только валидные переходы;
неоплаченные заказы автоматически отменяются по TTL и освобождают резерв.

Inventory correctness: резервирование/списание — атомарные UPDATE-запросы
(`stock - reserved >= qty`), конкурентный oversell невозможен (покрыто
интеграционным тестом `CheckoutConcurrencyIT`).

## API Overview

Точная спецификация — в Swagger. Ключевые ручки:

- `POST /api/v1/auth/register|login|refresh|logout|change-password`, `GET /api/v1/auth/me`;
  `forgot-password`/`reset-password` и `verify-email`/`resend-verification` —
  одноразовые токены по почте (без раскрытия существования аккаунта)
- `GET /api/v1/catalog/products` (страницы), `GET /api/v1/catalog/products/search`
  (фильтры: `q, categoryId, brandId, gender, variantSize, color, minPrice, maxPrice, inStock`),
  `GET /api/v1/catalog/products/{id}` (с вариантами и остатками), `GET /api/v1/catalog/brands`
- `GET|POST|PUT|DELETE /api/v1/cart/items/{variantId}` — корзина по вариантам;
  работает и для гостей: первый ответ выдаёт `X-Cart-Token`, при login/register
  передайте его заголовком — корзина сольётся с пользовательской
- `POST /api/v1/orders/checkout` — заголовок `Idempotency-Key`, опционально `{"promoCode": "..."}`
- `POST /api/v1/promo/validate` — предварительная проверка кода по текущей корзине
- `POST /api/v1/payments` → редирект на платёжную страницу; `POST /api/v1/payments/callback`
  (подпись обязательна, fail-closed); `POST /api/v1/admin/payments/{id}/refund`
- `/api/v1/users/me/**` — профиль, адресная книга (default-адрес), wishlist,
  GDPR: `GET /export` (выгрузка данных) и `DELETE /api/v1/users/me` (анонимизация)
- `/api/v1/admin/**` — ADMIN/MANAGER: каталог + варианты + остатки, отзывы (модерация),
  промокоды; заказы (+история) и пользователи — только ADMIN
- **AI** (выключено по умолчанию, `AI_ENABLED`; со stub-провайдерами работает офлайн):
  `GET /api/v1/catalog/products/semantic-search?q=…` (векторный поиск, pgvector KNN,
  fallback на keyword), `GET …/nl-search?q=тёплая куртка до 100` (LLM-разбор запроса
  в фильтры), `GET …/{id}/similar`, `GET …/{id}/bought-together` (co-occurrence по
  оплаченным заказам), `GET …/{id}/review-summary` («что говорят покупатели»);
  админ-триггеры: `POST /api/v1/admin/ai/embeddings/backfill | recommendations/recompute |
  review-summaries/refresh` (202, аудит)

**AI в проде (решение для v1): включено.** Прод-профиль compose поднимает AI с
живыми провайдерами: эмбеддинги — Ollama `bge-m3` на самом сервере (loopback
:11434, наружу не торчит, ключей не нужно), LLM — DeepSeek `deepseek-v4-flash`
с выключенным thinking (`DEEPSEEK_API_KEY` в `.env`; дёшево, единичные
summarize/extract-вызовы). После первого деплоя один раз:
`POST /api/v1/admin/ai/embeddings/backfill`. Откат безопасен и не требует
деплоя: `AI_ENABLED=false` — все AI-ручки штатно деградируют (keyword-поиск,
пустые рекомендации, 404 у саммари); так же деградирует и каждый сбой провайдера
в рантайме. Детали — в [docs/ai-features-plan.md](docs/ai-features-plan.md).

Ошибки — RFC 9457 Problem Details c `code` и `requestId` (сквозной
`X-Request-Id` в ответах).

## Security Highlights

- Deny-by-default: публичные ручки перечислены явно в `SecurityConfig`
- BCrypt(12); refresh-токены хранятся хэшированными, ротация + reuse-detection
  с отзывом всей цепочки; logout и смена пароля инвалидируют сессии
- JWT: `iss`/`aud` проверяются; секрет только из окружения
- Rate limiting на auth/checkout/callback (429 + `Retry-After`)
- Платёжные callbacks: HMAC-подпись, идемпотентность по `externalEventId`,
  валидация переходов статусов; при пустом секрете верификатор отклоняет всё
- Загрузка изображений: проверка magic bytes, генерируемые имена, лимиты размера
- Отзывы: только после покупки, санитизация HTML, премодерация

## Testing

- Unit-тесты (`*Test`) — JUnit 5 + Mockito, без БД.
- Интеграционные (`*IT`) — Testcontainers PostgreSQL через
  `support/PostgresContainerSupport` (нужен Docker). Инварианты корректности
  закреплены тестами: `CheckoutConcurrencyIT` (нет oversell под конкуренцией),
  `SecurityAccessIT` (deny-by-default, роли, IDOR).
- `./mvnw verify` — единый гейт: тесты + порог покрытия JaCoCo + Spotless.

## Migrations

Flyway — единственный источник схемы (`ddl-auto: validate`). `V1`–`V8` —
исходная схема; `V9__clothing_domain.sql` — варианты/инвентарь/номера заказов/
история статусов; `V10` — роль MANAGER + модерация отзывов; `V11` — промокоды;
`V12` — профиль/адресная книга/wishlist + audit trail; `V13` — гостевые
корзины + журнал согласий; `V14` — одноразовые токены (сброс пароля,
верификация email); `V15` — pgvector + эмбеддинги товаров (нужен образ
`pgvector/pgvector:pg18`); `V16` — рекомендации «с этим покупают»; `V17` —
саммари отзывов. Миграции только аддитивные (expand/contract) — применённую
миграцию не редактируют, добавляют новую.

Архитектурные решения и эксплуатация: [docs/adr/](docs/adr),
[docs/er-diagram.md](docs/er-diagram.md), [docs/runbook.md](docs/runbook.md).

## CI

GitHub Actions ([.github/workflows/ci.yml](.github/workflows/ci.yml)):
build → tests (включая Testcontainers) → JaCoCo gate → Spotless; Dependabot
следит за уязвимостями зависимостей.

## Refactoring Status

Полный чек-лист требований и статус выполнения — в
[REFACTORING_PLAN.md](REFACTORING_PLAN.md).
