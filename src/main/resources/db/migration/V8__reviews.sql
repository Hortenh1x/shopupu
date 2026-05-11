create table reviews
(
    id         bigserial primary key,
    user_id    bigint not null references users (id) on delete cascade,
    product_id bigint not null references products (id) on delete cascade,
    order_id   bigint references orders (id) on delete set null,
    rating     integer not null,
    title      varchar(160) not null,
    body       text not null,
    status     varchar(32) not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_reviews_user_product unique (user_id, product_id),
    constraint ck_reviews_rating_range check (rating between 1 and 5),
    constraint ck_reviews_status check (status in ('PUBLISHED', 'HIDDEN', 'DELETED'))
);

create index idx_reviews_product_id on reviews (product_id);
create index idx_reviews_user_id on reviews (user_id);
create index idx_reviews_status on reviews (status);
