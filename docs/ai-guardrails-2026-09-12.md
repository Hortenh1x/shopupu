# AI guardrails — T5 / Q-09, 12 September 2026

Status: **IMPLEMENTED / UNVERIFIED**. Source and regression tests are prepared. No Maven, Docker,
browser, application, model process or live provider call was started for this task. Compilation,
formatting, unit/integration results and live quality/latency remain **UNVERIFIED**; the main agent
will run checks sequentially within the desktop's resource limits. This document does not record a PASS.

## Boundaries

- `AiWebConfiguration` registers an MVC interceptor for every handler in `ai.controller`, including
  stylist POST, semantic/NL GET, recommendations, summaries and admin generation triggers. Security
  runs first. Before controller work, independent request counters apply to the socket peer and,
  when present, the authenticated principal. Supplied forwarding headers and account IDs do not
  choose a counter. Exceeding either counter returns RFC 9457 `429`, `code=AI_RATE_LIMITED`,
  `requestId` from the common request context, and `Retry-After` in seconds.
- Counters use fixed UTC minute/day windows. The map holds at most `max-tracked-clients` IP/account
  keys; when full, expired day counters can be reclaimed. New identities otherwise fail closed.
  Active counters are not evicted to make room for an attacker cycling identities.
- One `AiUsageGuard` protects DeepSeek, Ollama, the local embedding sidecar and Voyage, including
  scheduled/admin generation. It checks `ai.enabled`, input bounds and the absence of a database
  transaction, then atomically reserves concurrency, a call and conservative token units. There is
  no waiting queue. A permit releases concurrency exactly once; call/token reservations remain
  charged after failure, timeout or rejection by the provider.
- Token units are UTF-8 input bytes + 512 framing units + 64 per message/input + the requested maximum
  output tokens. This deliberately over-reserves normal text for the fixed providers. It is an
  application resource envelope, **not a currency limit or a claim about actual tokenization**.
  Provider-side spend limits remain necessary; live evaluation must compare observed usage with
  this reservation before changing providers/tokenizers or exposing multiple instances.
- HTTP clients retain finite connect/read timeouts. DeepSeek retains `deepseek-v4-flash` and sends
  `thinking:{type:"disabled"}` plus `max_tokens`. Every HTTP response is read with an independent byte
  ceiling before JSON parsing. Embedding dimensions and finite values are checked. Error paths log
  safe operation identifiers and return a generic unavailable result without raw provider messages,
  credentials, endpoints, response bodies or prompts. A provider rejection does not retry externally.
- `QueryEmbeddingService` and `NlQueryParser` own bounded Caffeine caches with TTL. The enable flag
  is checked before cache lookup. Free-text queries remain in process memory for this TTL; these
  caches contain public-catalog query computations, not account or order data.

These guards assume **one running application instance**. Counters reset on restart and are not
shared between replicas. Before scaling/repeated deployment during one budget period, move request
and provider reservations to an atomic shared store and configure a provider-side spending limit.
Existing proxy trust settings must correctly establish `request.getRemoteAddr()` at deployment.
No changes to `SecurityConfig`, the existing `RateLimitFilter`, deployment configuration or credentials
were made in T5.

## Disabled and degraded behavior

`ai.enabled=false` prevents external calls at both service and provider boundaries. Semantic/NL
search uses the database; embeddings and summary generation skip external work. Already stored
summaries/recommendations remain ordinary catalog reads. Stylist uses the deterministic keyword
planner and SQL only when AI is disabled, the LLM provider is `stub`, its reservation is rejected,
or its response fails/has an invalid shape. It returns `degraded=true` and an explicit fallback
message. An embedding failure after a valid LLM plan falls back to SQL through the semantic service.
Admin background jobs are still subject to the same provider budget and cannot exceed it.

`degraded` describes the stylist plan source; it is not a declaration that a live model's answer is
accurate. No matching eligible products yields `slots=[]` and a message that no matching products are
available. A partial selection does not claim that the missing garments are in stock.

## API contract and hard constraints

Existing `{ "message": "...", "history": [...] }` requests continue to work. The optional fields are:

```typescript
type StylistChatRequest = {
  message: string; // 1..500 characters
  history?: { role: "user" | "assistant"; content: string }[]; // <=10; each <=1000
  gender?: "MEN" | "WOMEN" | "UNISEX" | "KIDS";
  maxTotalPrice?: number; // 0..999999.99; total of all returned outfit items
};
// Response retains: reply, slots, unavailable, degraded.
```

Structured constraints take precedence over free-text extraction. Without them, a conservative
deterministic EN/DE/RU parser recognizes explicit gender and budget phrases, including zero and
decimal amounts. Earlier user turns supply constraints when the latest message does not change
them; assistant turns never supply constraints. The parser does not understand every linguistic
form, negation, currency conversion or a requested budget reset. Structured fields should be exposed
when exact constraints matter. Prices use the catalog's currency; text mentioning another currency
does not perform an exchange-rate conversion.

The server enforces the total remaining budget across selected products with `BigDecimal`. LLM
per-item ceilings can make selection stricter but cannot increase the shopper's total budget.
MEN/WOMEN may include UNISEX; explicit UNISEX includes only UNISEX; KIDS includes only KIDS.
Unknown prices, disabled products and gender mismatches are excluded. Stylist retrieval requires
available inventory and applies the existing relevance threshold before selection. An empty filtered
list stays empty: the original rejected candidates are never restored. NL search also reapplies
deterministically extracted price/gender constraints after the LLM parse and in disabled fallback.

This is a conservative greedy selection from at most six candidates per slot and four slots. It does
not promise to find every valid outfit or the cheapest optimal combination. Availability and catalog
prices can change after the response; checkout retains its authoritative inventory/price validation.

## Review summary privacy race — T5/T6 follow-up, UNVERIFIED

Generation now captures an immutable, ordered input snapshot under the product row lock in a short
transaction: product title, approved count, and the selected review IDs, rating, body and `updatedAt`.
Ordering is `createdAt DESC, id DESC` to resolve timestamp ties. The LLM runs after that transaction
ends. Apply starts a new transaction, obtains the same product row lock, rereads the exact snapshot,
and upserts only if it is unchanged. Deletion, status changes, same-count edits and review replacement
therefore invalidate an in-flight generation. A below-threshold deletion also happens under the lock.

`ReviewSummaryInvalidationListener` processes `ProductReviewsChangedEvent` synchronously at
`BEFORE_COMMIT`, even with AI disabled: first flush review changes, then lock the product and delete
its stored summary. This preserves the review-row → product-row order used by GDPR. Multi-product
writers must flush review mutations first and acquire/publish distinct product IDs in ascending order;
GDPR follows this protocol. The ordinary review edit/delete/moderation paths already publish the
event for approved-set changes. A rollback rolls back invalidation with the review mutation.

If generation applies first, a pending writer waits for its product lock and deletes the new summary
before its own mutation commits. If the writer commits first, generation's snapshot comparison
rejects the old result. Summary reads no longer use `@Cacheable`, avoiding late cache repopulation of
erased text; legacy entries are evicted after commit. Direct maintenance SQL must follow the same
locking/invalidation protocol. HTML sanitization and moderation do not prove a review contains no
personal data; the old claim that approved texts are automatically PII-free has been removed.

Prepared, not run: `ReviewSummaryServiceTest` adds late-erasure/same-count-edit/lock-order checks;
`ReviewSummaryInvalidationListenerTest` checks flush → product lock → delete;
`ReviewSummaryPrivacyConcurrencyIT` exercises both lock orderings in PostgreSQL, an LLM barrier outside
transactions, disabled-AI cleanup and resistance to an old cache entry. The IT uses synthetic reviews
and a mocked LLM, never a live provider. All compile/test outcomes remain **UNVERIFIED**.

## Configuration for the main agent to integrate

Defaults already exist as validated `AiProperties` fields. The main agent can add these lines to the
existing `ai:` mapping in `application.yml` to expose explicit conventional environment names:

```yaml
ai:
  max-concurrent-calls: ${AI_MAX_CONCURRENT_CALLS:2}
  external-calls-per-minute: ${AI_EXTERNAL_CALLS_PER_MINUTE:30}
  external-calls-per-day: ${AI_EXTERNAL_CALLS_PER_DAY:500}
  external-tokens-per-minute: ${AI_EXTERNAL_TOKENS_PER_MINUTE:60000}
  external-tokens-per-day: ${AI_EXTERNAL_TOKENS_PER_DAY:500000}
  max-input-bytes: ${AI_MAX_INPUT_BYTES:24000}
  max-output-tokens: ${AI_MAX_OUTPUT_TOKENS:800}
  max-response-bytes: ${AI_MAX_RESPONSE_BYTES:262144}
  requests-per-minute: ${AI_REQUESTS_PER_MINUTE:20}
  requests-per-day: ${AI_REQUESTS_PER_DAY:200}
  max-tracked-clients: ${AI_MAX_TRACKED_CLIENTS:10000}
  query-cache-entries: ${AI_QUERY_CACHE_ENTRIES:256}
  query-cache-minutes: ${AI_QUERY_CACHE_MINUTES:10}
```

These defaults are development ceilings, not an approved live operating budget. Keep the existing
enable/provider/model/timeout settings; T5 does not replace the configured model, enable thinking,
provide keys, or enable a live provider. Interceptor registration is automatic and needs no new
security whitelist entry. EN/DE translation of the fallback UI is part of the later localization task.

## Prepared offline evidence — all UNVERIFIED

| Test | Intended evidence | Status |
|---|---|---|
| `StylistSafetyEvalTest` | Synthetic budget/gender matrix, strict UNISEX/KIDS, inclusive/zero budget, total outfit ceiling, empty catalog, model relaxation, history ownership, malformed output, exception privacy, EN/DE/RU explicit parsing | UNVERIFIED |
| `StylistServiceTest` | Existing relevance, dedupe, unavailable garment and label behavior, with explicit enabled provider fixtures and database fallback | UNVERIFIED |
| `SemanticSearchServiceTest` | Disabled fallback constraints, model cannot relax budget/gender, available-inventory filter, existing semantic behavior | UNVERIFIED |
| `AiUsageGuardTest` | Disable, oversized input, token reservation, minute/day rollover, held permits, concurrent reservations, transaction boundary | UNVERIFIED |
| `AiRequestLimiterTest` / `AiWebConfigurationTest` | Independent IP/account budgets, day persistence, bounded map fail-closed, stylist 429 before work, untrusted forwarding, Problem Details | UNVERIFIED |
| `DeepSeekLlmClientTest` | Mock HTTP only: no call when disabled, fixed model/thinking/output cap, transport failure, oversized response, valid provider metadata | UNVERIFIED |
| `EmbeddingProviderGuardTest` | Disabled guard in all embedding providers; invalid vectors rejected | UNVERIFIED |
| `AiQueryCacheTest` | Repeated-query cache reuse and disable flag bypassing cached results | UNVERIFIED |

The fixture products are synthetic (`Synthetic ...`, `Fictional`) and do not represent real stock,
sales, customers or reviews. Mocked retrieval tests prove the selection contract after execution;
they do not estimate semantic precision, recall, outfit quality, multilingual model accuracy or live
latency. No model-quality percentage or live PASS is claimed.

The main agent should first run only the targeted unit tests, then the required full checks once,
sequentially with the approved memory/process limits. Provider tests use mock transport and do not
require a key. They must remain separate from owner-operated live evaluation.

## Owner-operated live evaluation, not executed

1. Set provider-side spend limits first; choose an isolated single-instance environment with synthetic
   catalog/review data. Supply keys through the deployment secret mechanism without printing them.
   Record the approved call/token/day budget and latency target before enabling a live provider.
2. Keep `deepseek-v4-flash` with thinking disabled. Record provider/model, catalog revision, embedding
   model/dimensions and effective configuration. Start with lower call limits and one concurrent call.
3. Run a fixed small EN/DE corpus covering winter/business/casual requests, explicit gender/total
   budgets, absent garments, no eligible stock, injection attempts and follow-up constraints. Include
   direct structured fields and ordinary text. Collect public product IDs, prices, constraint checks,
   degradation status, latency and provider usage; do not retain personal text or credentials.
4. Require zero products violating explicit hard constraints, zero prohibited external calls when
   disabled/limited/in a transaction, and no provider details in error responses. Exercise timeout,
   malformed JSON, exhausted budgets and concurrent requests with a controlled mock before live use.
5. Compare live token usage with conservative reservations and observed tail latency with the
   predeclared target. Separately review garment relevance and reply truthfulness against the fixed
   corpus. Any missing live/provider evidence stays UNKNOWN/UNVERIFIED; adjust neither thresholds nor
   quality claims from a few attractive examples. Disable the provider if any hard gate fails.
