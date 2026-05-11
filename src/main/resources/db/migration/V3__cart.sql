create table carts
(
    id         bigserial primary key,
    user_id    bigint not null references users (id) on delete cascade,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_carts_user_id unique (user_id)
);

create table cart_items
(
    id         bigserial primary key,
    cart_id    bigint not null references carts (id) on delete cascade,
    product_id bigint not null references products (id) on delete cascade,
    quantity   integer not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_cart_items_cart_product unique (cart_id, product_id),
    constraint ck_cart_items_quantity_positive check (quantity > 0)
);

create index idx_cart_items_cart_id on cart_items (cart_id);
create index idx_cart_items_product_id on cart_items (product_id);
