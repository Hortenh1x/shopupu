-- Precomputed "bought together" pairs from paid-order co-occurrence, rebuilt
-- by RecommendationComputationJob (docs/ai-features-plan.md). No LLM involved.
create table product_recommendations
(
    product_id             bigint      not null references products (id) on delete cascade,
    recommended_product_id bigint      not null references products (id) on delete cascade,
    kind                   varchar(24) not null,
    score                  integer     not null,
    computed_at            timestamp with time zone not null default now(),
    primary key (product_id, recommended_product_id, kind)
);

create index idx_product_recommendations_lookup
    on product_recommendations (product_id, kind, score desc);
