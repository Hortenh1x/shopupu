# AGENTS.md

Guidance for Codex when working in this repository.

## What this is

`shopupu` is a **production-oriented Spring Boot REST backend for an online clothing store**.
API-only (no server-rendered UI); a separate frontend lives at `../shopupu-web`.
The codebase was refactored from a prototype into a hardened modular monolith — see
[REFACTORING_PLAN.md](REFACTORING_PLAN.md) for the full requirement checklist and status.

- **Java 25**, **Spring Boot 4.0.7 (GA)**, Maven (use the wrapper `./mvnw`)
- Spring Security (stateless JWT, deny-by-default), Spring Data JPA / Hibernate, PostgreSQL, Flyway
- Bucket4j (rate limiting), Caffeine (cache), Springdoc OpenAPI, JJWT, Lombok, MapStruct (deps present)
- JUnit 5, Mockito, Testcontainers, JaCoCo, Spotless

## Commands

```bash
# Run locally (dev profile supplies a local-only JWT secret; DB must be up)
docker compose up -d db
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

./mvnw test                 # unit + Testcontainers integration tests (needs Docker)
./mvnw verify               # test + JaCoCo coverage gate + Spotless check — the CI gate
./mvnw spotless:apply       # auto-format before committing (verify FAILS on violations)
./mvnw -Dtest=OrderServiceTest test   # single test class

docker build -t shopupu .   # multi-stage, non-root production image
```

- Swagger UI: `http://localhost:8080/swagger` · OpenAPI JSON: `/v3/api-docs` · Health: `/actuator/health`
- **Outside the `dev` profile the app refuses to start without `JWT_SECRET`.** This is intentional — no secrets in the repo. All env vars are in [`.env.example`](.env).

## Architecture

Modular monolith, **package-by-feature** under `com.example.shopupu`. Each domain module has the
same internal shape: `controller/` · `dto/` · `entity/` · `repository/` · `service/` (+ `mapper/`,
`gateway/`, `model/` where needed).

| Module | Responsibility |
|---|---|
| `auth` | register/login/refresh/logout, password reset & email verification (one-time tokens) |
| `identity` | users, roles, address book, wishlist, consent journal, GDPR export/erasure |
| `catalog` | categories, brands, products, variants (size/color/SKU), images, filtered search |
| `inventory` | stock/reserved per SKU, atomic movements, oversell prevention |
| `cart` | user & guest carts (opaque `X-Cart-Token`), merge on login |
| `orders` | idempotent checkout, 9-status state machine, status history, snapshots |
| `payments` | pluggable gateways (monobank/Fondy/stub), HMAC webhooks, refunds |
| `promo` | promo codes with atomic redemption accounting |
| `shipping` | methods, rates, free-shipping threshold, per-order address snapshot |
| `reviews` | verified-purchase reviews, pre-moderation, HTML sanitization |
| `ai` | semantic search (pgvector), recommendations, review summaries; pluggable embedding/LLM providers with stub fallback |
| `notifications` | domain events → async email (SMTP or logging fallback) |
| `common` | audit trail, exception handling, storage, security helpers, web filters |
| `config` | `@ConfigurationProperties`, security, CORS, caching, OpenAPI, bootstrap admin |
| `security` | JWT provider/filter, rate-limit filter, user details |

## Invariants — do not break these

These encode hard-won correctness/security guarantees. Changing them without care reintroduces bugs
the refactor specifically fixed (blockers are listed in [REFACTORING_PLAN.md](REFACTORING_PLAN.md) §2).

1. **Deny-by-default security.** `SecurityConfig` ends with `.anyRequest().authenticated()`. Public
   endpoints are an explicit whitelist. A **new endpoint is protected automatically** — only add it to
   the whitelist if it is genuinely public, and think about why.
2. **Flyway is the single source of schema truth** (`ddl-auto: validate`). **Never edit an applied
   migration** — add the next `V{N}__*.sql`. Migrations are additive / expand-contract so old images
   stay schema-compatible on rollback.
3. **Inventory is never read-modify-write.** reserve/commitSale/release/restock are single atomic
   `UPDATE`s with the guard baked in (`SET reserved = reserved + q WHERE stock - reserved >= q`).
   0 rows updated = `OutOfStockException`. See [ADR-0002](docs/adr/0002-inventory-reservations.md).
4. **No external HTTP inside a DB transaction.** Payment gateway calls use the
   `TransactionTemplate` prepare → HTTP (with timeouts) → apply pattern so a connection is never held
   across a network call. The **webhook is the source of truth** for payment status, verified by HMAC
   (fail-closed: empty secret ⇒ reject everything), deduped by `externalEventId`, with status-transition
   validation. See [ADR-0003](docs/adr/0003-payment-gateway-boundaries.md).
5. **Map entities → DTOs inside the transaction.** `open-in-view: false` (OSIV is off), so lazy
   associations are dead once the `@Transactional` boundary closes. Controllers must receive DTOs, never
   entities. (This is what the most recent commit enforced.)
6. **Order status only moves through valid transitions.** `CREATED → PENDING_PAYMENT → PAID →
   PROCESSING → SHIPPED → DELIVERED → COMPLETED`, plus `CANCELLED` (pre-payment, releases reservation)
   and `REFUNDED` (post-payment, restocks). Unpaid orders auto-cancel on TTL and release stock.
7. **Errors are RFC 9457 Problem Details** with a `code` and `requestId`. Throw the domain exceptions
   in `common/exception` and let `GlobalExceptionHandler` render them — don't hand-write error bodies.
   Every response carries `X-Request-Id` (also in the MDC).
8. **Config is validated & fail-fast.** All settings go through `@ConfigurationProperties` + `@Validated`
   in `config/`; the app fails to start on invalid config rather than misbehaving at runtime.

## Conventions

- **Mappers are hand-written `@Component` classes** (`toXxxResponse`) with null-guards. MapStruct
  dependencies are wired but mapping is still manual (ARCH-03 pending) — follow the existing manual style
  unless you are deliberately migrating a whole mapper.
- **DTOs are Java records**; request DTOs carry Bean Validation annotations; controllers validate.
- **Services own transactions** (`@Transactional`, `readOnly = true` for queries). Controllers are thin.
- **Money is `BigDecimal`**, never `double`. Timestamps are UTC.
- **Tests:** unit tests are `*Test` (JUnit 5 + Mockito); integration tests are `*IT` and extend
  `support/PostgresContainerSupport` (Testcontainers PostgreSQL). Concurrency/security invariants have
  dedicated ITs (`CheckoutConcurrencyIT`, `SecurityAccessIT`) — keep them green.
- **API is versioned under `/api/v1`.** Admin/manager surface is `/api/v1/admin/**`
  (`/admin/users/**` and `/admin/orders/**` are ADMIN-only, the rest ADMIN or MANAGER).
- **Auditable actions** (logins, password change, GDPR erasure, admin status change, refund, review
  moderation) go through `common/audit/AuditService`.

## Profiles & config

| Profile | Purpose |
|---|---|
| (default) | strict: no JWT secret fallback, prod-like |
| `dev` | local dev: dev-only JWT secret, verbose logging |
| `prod` | hardened: restricted actuator, JSON/ECS logs, no auto-baseline |
| `test` | test suite (Testcontainers PostgreSQL) |

Runtime config is env-driven (`application.yml` reads `${VAR:default}`); see `.env.example`.
Payment provider is selected by `PAYMENTS_DEFAULT_PROVIDER` (`stub` for local dev).

## Gotchas

- **Docker is required** for `./mvnw test`/`verify` (Testcontainers spins up PostgreSQL).
- **PostgreSQL image is `pgvector/pgvector:pg18`** (compose + Testcontainers): the V15 migration
  runs `CREATE EXTENSION vector` for AI semantic search. Plain `postgres` images will fail it.
- **AI is off by default** (`ai.enabled=false`) and the default providers are **stubs** —
  deterministic, offline, no keys. AI HTTP calls follow the ADR-0003 prepare→HTTP→apply shape
  (never inside a DB transaction); every AI failure degrades (keyword search / empty recs / 404
  summary) — see [docs/ai-features-plan.md](docs/ai-features-plan.md). Live providers:
  **embeddings** = Ollama `bge-m3` (`ai.embedding-provider=ollama`, dev default) or Voyage;
  **LLM** (summaries, NL parse) = DeepSeek `deepseek-v4-flash` with thinking disabled
  (`ai.llm-provider=deepseek`, OpenAI-compatible; the client sends `thinking:{type:disabled}`
  since V4 defaults to thinking on). Single summarize/extract calls — keep thinking off,
  don't switch to a reasoning/frontier model.
- **pgvector/jsonb tables are deliberately not JPA entities** (`product_embeddings`,
  `product_recommendations`, `product_review_summary`) — access goes through JdbcClient
  repositories in `ai/repository`, keeping vector types out of Hibernate's `validate`.
- `verify` fails on Spotless violations and on the JaCoCo coverage floor — run `spotless:apply` first.
- Image uploads are validated by **magic bytes**, not the client `Content-Type`; filenames are generated.
- Refresh tokens are stored **hashed** with rotation + reuse-detection (reuse revokes the whole chain).
- The `controllers/`, `dtos/`, `entities/`, `repositories/`, `services/` dirs at the repo root are empty
  legacy stubs — the real code is under `src/main/java/com/example/shopupu/`.
- `*.out.log` / `*.err.log` at the root are old run logs, not part of the app.

## Docs

- [README.md](README.md) — overview, quick start, API surface
- [REFACTORING_PLAN.md](REFACTORING_PLAN.md) — requirement checklist, phase status, known gaps
- [docs/adr/](docs/adr) — architecture decisions (inventory reservations, payment boundaries)
- [docs/er-diagram.md](docs/er-diagram.md) — entity relationships (mermaid)
- [docs/runbook.md](docs/runbook.md) — operational incident procedures
- [docs/ai-features-plan.md](docs/ai-features-plan.md) — AI features design & as-built notes
- [docs/cloudflare-tunnel.md](docs/cloudflare-tunnel.md) — expose the dev backend for webhooks
- `graphify-out/graph.html` — interactive knowledge graph of this codebase (run `/graphify query "..."` to ask it questions)
