# Shopupu — Clothing Shop Backend

Production-oriented Spring Boot REST API for an online clothing store: product
variants (size/color/SKU), inventory with reservations, order state machine,
idempotent checkout, promo codes, moderated reviews and pluggable payment
gateways (monobank for UAH, Fondy for EUR, stub for local dev).

## Tech Stack

- Java 25, Spring Boot 4.0.x (GA)
- Spring Security (stateless JWT, deny-by-default), Bucket4j rate limiting
- Spring Data JPA / Hibernate, PostgreSQL, Flyway
- Caffeine cache, Springdoc OpenAPI
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
variables are documented in [`.env.example`](.env.example).

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

- `POST /api/v1/auth/register|login|refresh|logout|change-password`, `GET /api/v1/auth/me`
- `GET /api/v1/catalog/products` (страницы), `GET /api/v1/catalog/products/search`
  (фильтры: `q, categoryId, brandId, gender, size, color, minPrice, maxPrice, inStock`),
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
- `/api/v1/admin/**` — ADMIN/MANAGER: каталог + варианты + остатки, заказы (+история),
  отзывы (модерация), промокоды, пользователи (только ADMIN)

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

## Migrations

Flyway — единственный источник схемы (`ddl-auto: validate`). `V1`–`V8` —
исходная схема; `V9__clothing_domain.sql` — варианты/инвентарь/номера заказов/
история статусов; `V10` — роль MANAGER + модерация отзывов; `V11` — промокоды;
`V12` — профиль/адресная книга/wishlist + audit trail; `V13` — гостевые
корзины + журнал согласий.

Архитектурные решения и эксплуатация: [docs/adr/](docs/adr),
[docs/er-diagram.md](docs/er-diagram.md), [docs/runbook.md](docs/runbook.md).

## CI

GitHub Actions ([.github/workflows/ci.yml](.github/workflows/ci.yml)):
build → tests (включая Testcontainers) → JaCoCo gate → Spotless; Dependabot
следит за уязвимостями зависимостей.

## Refactoring Status

Полный чек-лист требований и статус выполнения — в
[REFACTORING_PLAN.md](REFACTORING_PLAN.md).
