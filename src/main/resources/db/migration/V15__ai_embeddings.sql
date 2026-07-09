-- AI semantic search: one pgvector embedding per product (docs/ai-features-plan.md).
-- Requires the pgvector extension binaries in the PostgreSQL image
-- (docker-compose and Testcontainers use pgvector/pgvector:pg18).
create extension if not exists vector;

create table product_embeddings
(
    product_id bigint primary key references products (id) on delete cascade,
    model      varchar(64)  not null,
    dim        integer      not null,
    embedding  vector(1024) not null,
    updated_at timestamp with time zone not null default now()
);

-- approximate KNN; cosine distance suits normalized sentence embeddings
create index idx_product_embeddings_hnsw
    on product_embeddings using hnsw (embedding vector_cosine_ops);
