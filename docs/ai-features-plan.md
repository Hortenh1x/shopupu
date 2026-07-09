# AI Features — Implementation Plan

Three organic AI capabilities for `shopupu`, designed to slot into the existing
modular monolith without breaking its [invariants](../CLAUDE.md#invariants--do-not-break-these):

1. **Semantic catalog search** + natural-language query parsing
2. **Recommendations** — "similar" (content) and "bought together" (collaborative)
3. **Review summaries** — "what customers say" (pros/cons/TL;DR)

Guiding principle: reuse the patterns the codebase already trusts — pluggable
providers (`payments/gateway`), graceful fallback (`NotificationService`),
HTTP-outside-transaction ([ADR-0003](adr/0003-payment-gateway-boundaries.md)),
async `AFTER_COMMIT` events, `@ConfigurationProperties` + `@Validated`, Flyway
expand/contract, DTO-mapping-inside-transaction (OSIV off).

---

## Phase 0 — Shared `ai` foundation

New package-by-feature module `com.example.shopupu.ai`, same internal shape as the
other modules. Everything AI-facing goes through two provider abstractions so the
rest of the app never talks to a vendor directly — and so **the app runs with no
keys** (dev/test/CI) exactly like SMTP degrades to logging today.

### Provider abstractions (mirror `payments/gateway`)

```java
// ai/gateway/EmbeddingClient.java
public interface EmbeddingClient {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
    int dimensions();
}

// ai/gateway/LlmClient.java  — generative calls (summaries, NL query parsing)
public interface LlmClient {
    <T> T complete(String system, String user, Class<T> schema); // structured output
    String complete(String system, String user);
}
```

Selection by config property, exactly like the gateway clients:

| Impl | Condition | Behaviour |
|---|---|---|
| `StubEmbeddingClient` | `ai.embedding-provider=stub` (`matchIfMissing = true`) | deterministic vector from a hash of the text — no network, makes tests reproducible |
| `OllamaEmbeddingClient` | `ai.embedding-provider=ollama` | **dev/prod default**: local Ollama daemon (`POST /api/embed`, `http://localhost:11434`) running **bge-m3** (1024-dim, multilingual); no key, data stays local |
| `LocalEmbeddingClient` | `ai.embedding-provider=local` | alternative: HuggingFace TEI sidecar (`POST /embed`) for the same class of model |
| `VoyageEmbeddingClient` | `ai.embedding-provider=voyage` | same client shape → Voyage `/v1/embeddings` (`voyage-3`, 1024-dim) when a managed model is wanted |
| `StubLlmClient` | `ai.llm-provider=stub` (`matchIfMissing = true`) | canned/templated output (e.g. summary = first sentences) so the feature renders in dev |
| `DeepSeekLlmClient` | `ai.llm-provider=deepseek` | DeepSeek `deepseek-v4-flash` (thinking disabled) via its OpenAI-compatible `/chat/completions` in JSON mode (`RestClient`, no SDK; base-url configurable) |

> **Embeddings provider (decided).** Two hand-rolled clients behind `EmbeddingClient`,
> chosen by config: **`local`** (prod default) — a small self-hosted embedding model
> (~≤2B params, **multilingual** for the UA/RU catalog) served as a sidecar (HuggingFace
> TEI or a tiny FastAPI service) and called over `RestClient` with timeouts; **`voyage`**
> — Voyage AI (`voyage-3`, 1024-dim) when a larger / managed model is wanted. Anthropic
> has **no native embeddings endpoint**, so Claude is used only for the generative work
> (summaries, NL-query → filter parsing). `stub` is the default so nothing external is
> required to boot.
>
> **One active embedding dimension.** Standardise on **1024 dims** (e.g. local `bge-m3`,
> multilingual, ~568M — same dim as `voyage-3`) so a `local ↔ voyage` swap needs no
> re-migration. The `product_embeddings` column dim must equal `ai.embedding-dim`;
> changing it later is an expand/contract migration + a full re-embed (the backfill path
> already supports that).

> **Which LLM.** Every in-scope LLM task is a single bounded call
> (summarize / extract), so the default is **DeepSeek `deepseek-v4-flash` with thinking
> mode disabled** — cheap and fast. V4 turns thinking on by default; for these tasks the
> reasoning only wastes tokens and muddies the JSON, so the client always sends
> `thinking:{type:"disabled"}`. (The old `deepseek-chat`/`deepseek-reasoner` names are
> deprecated 2026-07-24.) `deepseek-v4-pro` is the higher-quality step-up. Model and
> endpoint stay config knobs (`ai.llm-model`, `ai.llm-base-url`).

### AI calls never run inside a transaction (ADR-0003)

Reuse the `TransactionTemplate` prepare → call → apply shape from `PaymentService`:
gather inputs in a `readOnly` TX (mapping entities to plain data **inside** the TX,
OSIV is off), close it, make the AI HTTP call with a timeout, then persist the
result in a separate short TX. A dedicated executor mirrors `AsyncConfig`:

```java
@Bean("aiExecutor")
ThreadPoolTaskExecutor aiExecutor() { /* core 2, max 4, queue 500, prefix "ai-" */ }
```

### Config — `AiProperties` (mirror `PaymentProperties`)

```java
@Data @Validated @Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private boolean enabled = false;                    // master switch

    @Pattern(regexp = "stub|local|voyage") private String embeddingProvider = "stub"; // "local" in prod
    @Pattern(regexp = "stub|anthropic")    private String llmProvider = "stub";

    private String embeddingModel = "bge-m3";     // 1024-dim multilingual local; "voyage-3" for Voyage
    private String embeddingBaseUrl;              // local sidecar (TEI/FastAPI) or Voyage base URL
    @Min(64) @Max(4096) private int embeddingDim = 1024; // MUST match the pgvector column
    private String llmModel = "claude-haiku-4-5";        // all in-scope LLM tasks are summarize/extract/classify

    private String anthropicApiKey;  // from ANTHROPIC_API_KEY
    private String voyageApiKey;     // from VOYAGE_API_KEY

    @Min(1) @Max(60) private int requestTimeoutSeconds = 15;

    @Min(3) private int reviewSummaryMinReviews = 3;     // don't summarize < 3
    @Min(1) private int semanticSearchCapacity = 30;     // per-IP bucket
    @Min(1) private int semanticSearchRefillPerMinute = 30;
}
```

`.env.example` gains `AI_ENABLED`, `AI_EMBEDDING_PROVIDER`, `AI_LLM_PROVIDER`,
`ANTHROPIC_API_KEY`, `VOYAGE_API_KEY`, model/dim/timeout keys. Fail-fast on invalid
config, like every other properties class.

### Vector storage — pgvector in the existing PostgreSQL

- **Flyway `V15__ai_embeddings.sql`** (additive): `CREATE EXTENSION IF NOT EXISTS vector;`
  plus a dedicated table (keeps schema decoupled from the model / dim, expand-contract):
  ```sql
  CREATE TABLE product_embeddings (
      product_id  BIGINT PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
      model       VARCHAR(64)  NOT NULL,
      dim         INT          NOT NULL,
      embedding   vector(1024) NOT NULL,
      updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
  );
  CREATE INDEX idx_product_embeddings_hnsw
      ON product_embeddings USING hnsw (embedding vector_cosine_ops);
  ```
- **Hibernate mapping**: add `org.hibernate.orm:hibernate-vector` so the column maps
  to `float[]`; KNN runs as a **native query** (bound vector param, portable):
  ```sql
  SELECT product_id FROM product_embeddings
  ORDER BY embedding <=> CAST(:queryVec AS vector) LIMIT :k
  ```
- **Testcontainers**: point `PostgresContainerSupport` at the `pgvector/pgvector:pg16`
  image so KNN integration tests run for real.

### pom.xml additions

```xml
<dependency><groupId>com.anthropic</groupId><artifactId>anthropic-java</artifactId><version>2.34.0</version></dependency>
<dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-vector</artifactId></dependency>
<!-- Voyage embeddings use the existing spring-web RestClient — no extra dep -->
```

### Observability & security (all three features)

- Metrics mirror `shopupu.orders`/`shopupu.payments`:
  `meterRegistry.counter("shopupu.ai", "op", "embed|summarize|recommend", "result", "...")`.
- **Deny-by-default respected**: public read endpoints live under `/api/v1/catalog/**`
  (already whitelisted for GET); admin/regeneration endpoints under `/api/v1/admin/**`
  (ADMIN/MANAGER). Nothing new is added to the public whitelist by hand.
- Add a `SEMANTIC` zone to `RateLimitFilter` (each query hits an external embedding
  API) + cache query embeddings (below).

---

## Feature 1 — Semantic catalog search

**Where it plugs in:** `catalog` (`ProductQueryService`, `CatalogQueryController`,
`ProductFilter`). Today search is attribute-based (`ProductSpecifications.build`).

### Indexing (write path) — async, event-driven

- Compose embedding text from `Product`: `title + description + brand.name +
  material + season + gender + metaTitle + metaDescription`.
- Publish a `ProductChangedEvent(productId)` from `CatalogService` on
  create/update/delete (the app already uses this exact event shape for orders).
- `@Async("aiExecutor") @TransactionalEventListener(AFTER_COMMIT)` listener embeds
  the text (HTTP, outside TX) and upserts `product_embeddings`. Failure is logged,
  never breaks the catalog write (same discipline as `OrderNotificationListener`).
- **Backfill** existing rows: a one-shot admin endpoint /
  `@Scheduled` guard that embeds products with no/stale embedding (batch via Voyage's
  batch endpoint, or a throttled loop).

### Query (read path)

New endpoint `GET /api/v1/catalog/products/semantic-search?q=…` (+ the same optional
structural filters as `/search`). `ProductQueryService.semanticSearch(...)`:

1. Embed the query — **cached** (`@Cacheable("aiQueryEmbedding", key=normalized q)`),
   outside any TX, with timeout.
2. pgvector KNN → ordered candidate `productId`s, filtered to `enabled = true`
   (and any structural filters via an `EXISTS`/join, reusing `ProductSpecifications`).
3. Load products by id **preserving KNN order**, map to `ProductListItem` **inside the
   TX** (OSIV off), return a `Page`.
4. **Graceful fallback**: if embeddings are unavailable (stub / no key / timeout),
   transparently fall back to the existing keyword `findProducts(...)`.

### Optional: natural-language query → filters

`GET /api/v1/catalog/products/nl-search?q=тёплая куртка на осень до 100€` →
`LlmClient.complete(system, q, ProductFilter.class)` (Claude **structured outputs**,
Haiku) → feed the parsed `ProductFilter` into the existing search. Cheap, isolated,
and fully behind `ai.enabled`.

**Deliverables:** `V15`, `ProductEmbedding` entity + repo (native KNN),
`ProductChangedEvent` + listener, backfill job, 2 controller methods, `SEMANTIC`
rate-limit zone, ITs (pgvector container) + `SecurityAccessIT` cases (public GET).

---

## Feature 2 — Recommendations

Two kinds, two mechanisms, both served fast (precomputed / vector), both public reads
under `/api/v1/catalog/products/{id}/…`.

### "Similar" — content-based (reuses Feature 1's vectors)

`GET /api/v1/catalog/products/{id}/similar` → KNN on `product_embeddings` excluding
self, `enabled = true` only. No new storage; `ProductQueryService.findSimilar(id, k)`.

### "Bought together" — collaborative, from `order_items`

- **Flyway `V16__product_recommendations.sql`**:
  ```sql
  CREATE TABLE product_recommendations (
      product_id             BIGINT      NOT NULL REFERENCES products(id) ON DELETE CASCADE,
      recommended_product_id BIGINT      NOT NULL REFERENCES products(id) ON DELETE CASCADE,
      kind                   VARCHAR(24) NOT NULL,   -- 'BOUGHT_TOGETHER'
      score                  INT         NOT NULL,
      computed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (product_id, recommended_product_id, kind)
  );
  CREATE INDEX idx_prod_recs_lookup ON product_recommendations (product_id, kind, score DESC);
  ```
- **`RecommendationComputationJob`** (`@Scheduled`, nightly — mirror
  `OrderExpirationJob`) computes co-occurrence from paid orders and upserts top-N:
  ```sql
  INSERT INTO product_recommendations (product_id, recommended_product_id, kind, score, computed_at)
  SELECT a.product_id, b.product_id, 'BOUGHT_TOGETHER', COUNT(*), now()
  FROM order_items a
  JOIN order_items b ON a.order_id = b.order_id AND a.product_id <> b.product_id
  JOIN orders o      ON o.id = a.order_id
  WHERE o.status IN ('PAID','PROCESSING','SHIPPED','DELIVERED','COMPLETED')
  GROUP BY a.product_id, b.product_id
  HAVING COUNT(*) >= 2
  ON CONFLICT (product_id, recommended_product_id, kind)
  DO UPDATE SET score = EXCLUDED.score, computed_at = EXCLUDED.computed_at;
  -- keep top-N per product_id (window function / follow-up delete)
  ```
  (`order_items` already carries `product_id`; the pattern extends
  `existsByUserAndStatusInAndItems_ProductId` in `OrderRepository`.)
- **Serve** `GET /api/v1/catalog/products/{id}/bought-together` from the table,
  `@Cacheable("recommendations")`, **fallback** to same-category popular when empty.

> This "bought together" path uses **no LLM at all** — pure SQL + cache. Only
> "similar" uses the embeddings. Both stay well within read-latency budgets.

**Deliverables:** `V16`, `ProductRecommendation` entity + repo, co-occurrence job,
2 controller methods, cache entries, ITs.

---

## Feature 3 — Review summaries

**Where it plugs in:** `reviews` (`ReviewService`, `ReviewRepository`,
`ProductRatingSummaryResponse` — already `@Cacheable("productRating")`).

- **Flyway `V17__product_review_summary.sql`**:
  ```sql
  CREATE TABLE product_review_summary (
      product_id         BIGINT PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
      tldr               TEXT        NOT NULL,
      pros               JSONB       NOT NULL,
      cons               JSONB       NOT NULL,
      sentiment          VARCHAR(16) NOT NULL,   -- POSITIVE/MIXED/NEGATIVE
      based_on_reviews   INT         NOT NULL,
      model              VARCHAR(64) NOT NULL,   -- provenance
      generated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  ```
- **`ReviewSummaryService`**:
  1. `readOnly` TX: load **APPROVED** review bodies (already HTML-sanitized by Jsoup,
     no PII — `displayName`/email are never exposed), map to `List<String>` **inside
     the TX**, cap at the most-recent/most-helpful N.
  2. Close TX → `LlmClient.complete(system, reviewsText, ReviewSummary.class)` (Claude
     **structured outputs** → `{ tldr, pros[], cons[], sentiment }`, Haiku model).
  3. Separate TX: upsert `product_review_summary` (store `model` + `generated_at`).
  - Guard: skip when `< ai.review-summary-min-reviews` (default 3).
- **Trigger**: on review moderation `→ APPROVED` (`ReviewService.updateStatus`), publish
  `ReviewApprovedEvent(productId)` → `@Async AFTER_COMMIT` regenerate when the approved
  count crosses a threshold or the summary is stale. Plus an admin
  `POST /api/v1/admin/reviews/summaries/refresh` (audited via `AuditService`) that uses
  the **Anthropic Batch API** (`client.messages().batches()`) for cheap bulk backfill —
  with a stable instruction prefix so **prompt caching** kicks in across products.
- **Serve** `GET /api/v1/catalog/products/{id}/review-summary` (public),
  `@Cacheable("reviewSummary", key=productId)`, evicted on regeneration (extend the
  existing `@CacheEvict` calls in `ReviewService`). Returns empty below the threshold.

**Deliverables:** `V17`, `ProductReviewSummary` entity + repo, `ReviewSummary` record,
`ReviewSummaryService`, `ReviewApprovedEvent` + listener, public + admin endpoints,
cache entries, ITs (stub LLM) + audit + `SecurityAccessIT` (admin-only regenerate).

---

## Sequencing & effort

| Phase | Scope | Depends on |
|---|---|---|
| **0. Foundation** | `ai` module, `EmbeddingClient`/`LlmClient` + stubs, `AiProperties`, pgvector `V15`, pom deps, metrics, executor | — |
| **1. Semantic search** | embeddings write/read path, NL parsing, rate-limit zone | Phase 0 |
| **2. Recommendations** | "similar" (reuses vectors) + `V16` co-occurrence job | Phase 0–1 |
| **3. Review summaries** | `V17`, summary service, triggers, batch backfill | Phase 0 |

Each phase ships behind `ai.enabled=false` + `stub` providers, so `main`/CI stay green
with no external calls, and production turns features on per-provider.

## Invariants preserved

- **AI HTTP is always outside DB transactions** (ADR-0003 pattern).
- **Entities → DTOs inside the transaction** (OSIV off).
- **Flyway additive** — `V15`, `V16`, `V17`, no edits to applied migrations.
- **Deny-by-default** — public reads under `/api/v1/catalog/**`, admin under `/api/v1/admin/**`.
- **Config validated & fail-fast**; **RFC 9457** errors via `GlobalExceptionHandler`.
- **Graceful degradation** — no key ⇒ `stub` ⇒ keyword search / hidden summaries / popular recs.
- `./mvnw verify` stays green: stub clients keep unit tests deterministic; pgvector
  Testcontainer covers KNN; `CheckoutConcurrencyIT` / `SecurityAccessIT` untouched and extended.

## Implementation notes (as built, 2026-07-08)

The plan above is implemented (all phases). Deliberate deviations, chosen for
simplicity/risk:

- **Providers wired for this machine (2026-07-09):** embeddings default to **Ollama +
  bge-m3** (`OllamaEmbeddingClient`, dev profile), and the LLM is **DeepSeek
  `deepseek-v4-flash`** (thinking disabled; `DeepSeekLlmClient`, OpenAI-compatible) —
  replacing the earlier Claude Haiku client. The
  `anthropic-java` dependency was removed. `stub` remains the default outside dev/prod and in
  tests. Anthropic Batch-API backfill (below) is moot — the summary refresh is a throttled loop.

- **JdbcClient repositories instead of JPA entities** for `product_embeddings`,
  `product_recommendations`, `product_review_summary` (`ai/repository/*`): keeps the
  `vector`/`jsonb` column types out of the Hibernate model entirely — no
  `hibernate-vector` dependency, no `ddl-auto: validate` risk. KNN/upserts are native SQL.
- **`LlmClient` exposes task methods** (`summarizeReviews`, `parseCatalogQuery`) instead of a
  generic `complete(schema)` — mirrors `NotificationService`, lets the stub produce sensible
  canned output per task. Claude structured outputs still enforce the record schemas.
- **Semantic rate-limit knobs live in `RateLimitProperties`** (`app.rate-limit.semantic-*`),
  not `AiProperties` — it's a rate-limiting concern; new `SEMANTIC` zone covers
  `semantic-search` + `nl-search` (GET).
- **Backfill embeds sequentially with keyset pagination** (a failing product can't stall the
  loop); Voyage/TEI batch endpoints are wired but used with single-item lists for now.
  **Anthropic Batch API for summary backfill is deferred** — `refreshAllAsync` is a throttled
  loop; with Haiku pricing and catalog sizes this is cheap enough until proven otherwise.
- **Summaries regenerate on every approved-set change** (`ProductReviewsChangedEvent` from
  ReviewService on transitions into/out of APPROVED) rather than threshold-crossing detection —
  simpler and still cheap; below the min-review threshold the stale summary is deleted.
- **Events**: `ProductChangedEvent` (create/update/delete product + variant mutations) and
  `ProductReviewsChangedEvent`, both consumed in the `ai` module via `@Async("aiExecutor")`
  `@TransactionalEventListener(AFTER_COMMIT)`.
- **PostgreSQL 18**: compose + Testcontainers moved `postgres:18` → `pgvector/pgvector:pg18`
  (same PG major, adds the extension binaries; existing dev volumes stay compatible).
- **Semantic search returns a top-K `List`** (not `Page`) — KNN totals are meaningless;
  `nl-search` keeps the `Page` shape since it delegates to the existing filtered search.

## Decisions

1. **Integration approach** — ✅ **hand-rolled provider clients** mirroring `payments/gateway`
   (matches the repo's explicit, low-magic style; no Spring AI, no Boot-4 compat risk).
2. **Embeddings provider** — ✅ **local self-hosted small model by default** (~≤2B, multilingual,
   1024-dim), **Voyage AI** when a larger model is needed. Both hand-rolled behind
   `EmbeddingClient`, switched by `ai.embedding-provider`.
3. **Vector store** — ✅ **pgvector** in the existing PostgreSQL.
4. **Summary trigger** — event-driven regen on approval + threshold, plus an audited admin
   batch refresh *(recommended default; open to nightly-batch-only if preferred)*.
