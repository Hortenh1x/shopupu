# Generated product reviews (synthetic demo data)

Synthetic customer reviews for every catalog product, produced with the
`shopupu-review-generator` skill and its `references/review-metrix.yaml` matrix.
**This is fake demo content** — every review carries `"source": "synthetic_demo"`.
It is not real customer data and is not tied to real `users` rows.

## Files

- `product_{id}.json` — one file per product (IDs 1–22), each a JSON array of that
  product's reviews. `{id}` is the real `products.id` from the database.
- `all-reviews.json` — all reviews flattened into a single array, sorted by
  `productId` then review sequence.

## Coverage

22 products · **317 reviews** · 12–18 per product (within the 10–20 target).

> Reviews originally carried a `title`, but the platform dropped review titles entirely
> (migration `V19__reviews_drop_title.sql`; the UI shows the author's name in that spot
> instead), so titles were stripped from these artifacts too.

Each product's real variant sizes/colors were pulled from the DB so `sizePurchased`
is always a size that product actually stocks (e.g. jeans `30–36`, sneakers `40–43`,
beanie/scarf `One Size`).

## Schema (per review)

```json
{
  "reviewId": "rev_{productId}_{NNN}",
  "productId": 1,
  "rating": 5,
  "body": "string",
  "customerName": "string",
  "customerProfile": "string",
  "sizePurchased": "M",
  "fitFeedback": "too small | slightly small | true to size | slightly large | too large",
  "verifiedPurchase": true,
  "createdAt": "2026-04-18T14:32:05Z",
  "source": "synthetic_demo"
}
```

## Matrix rules applied

- Rating mix follows the matrix distribution (~50% 5★ / 28% 4★ / 13% 3★ / rare 2★ / few 1★).
- Length spread: micro / short / medium / long, ≥10% micro per batch.
- Persona / tone / language-style varied within every product batch; ≥1 non-native-light
  and ≥1 informal voice per batch, at most one emoji review.
- ≥25% of each batch mentions sizing/fit, ≥20% mentions fabric/material, ≥10% carry a
  small negative detail (even in positive reviews).
- Forbidden generic/AI phrases from the matrix are excluded; `createdAt` spans
  2026-01-15 → 2026-07-09 (UTC); all `reviewId`s and `customerName`s are globally unique.

## Loading into the DB

These reviews are seeded into the database by **`seed-reviews.sql`** (generated from
`all-reviews.json`). It is **idempotent** — re-running inserts nothing new:

```bash
docker compose exec -T db psql -U shopupu -d shopupu -v ON_ERROR_STOP=1 < scripts/generated-reviews/seed-reviews.sql
```

How the JSON maps onto the schema (the `reviews` table has no `customerProfile` /
`verifiedPurchase` / `source` columns, and the public API never exposes `verifiedPurchase`):

- The `reviews` table needs a real `user_id` and enforces `unique (user_id, product_id)`,
  so the loader creates **one synthetic user per review** (`demo-review-{productId}-{seq}@shopupu.local`).
  Its **`username` = `customerName`** — that is exactly what the product page displays
  (`ReviewMapper` exposes `username`, never the email).
- These users are **non-login** accounts: `password_hash` is a placeholder that is not a
  valid BCrypt hash, so `BCryptPasswordEncoder.matches` always returns false. `enabled` and
  `email_verified` are true; no roles are assigned.
- Reviews are inserted with **`status = 'APPROVED'`** — the only status the public endpoint
  `GET /api/v1/catalog/products/{id}/reviews` returns (moderation state `APPROVED`, per the
  `PENDING → APPROVED/REJECTED/DELETED` workflow). `order_id` is null; `created_at` /
  `updated_at` come from the JSON.

To remove the seeded data:

```sql
delete from reviews r using users u
  where r.user_id = u.id and u.email like 'demo-review-%@shopupu.local';
delete from users where email like 'demo-review-%@shopupu.local';
```

> The `productRating` summary is cached (`expireAfterWrite=10m`); the per-review list is
> uncached and shows immediately. If an average looks stale right after seeding, it refreshes
> within 10 minutes (or on app restart).
