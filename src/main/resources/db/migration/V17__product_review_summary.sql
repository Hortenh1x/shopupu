-- LLM-generated "what customers say" summary per product, built only from
-- APPROVED (already sanitized) reviews (docs/ai-features-plan.md).
create table product_review_summary
(
    product_id       bigint primary key references products (id) on delete cascade,
    tldr             text        not null,
    pros             jsonb       not null,
    cons             jsonb       not null,
    sentiment        varchar(16) not null,
    based_on_reviews integer     not null,
    model            varchar(64) not null,
    generated_at     timestamp with time zone not null default now()
);
