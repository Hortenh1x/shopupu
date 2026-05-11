create table orders
(
    id              bigserial primary key,
    user_id         bigint not null references users (id) on delete cascade,
    subtotal_amount numeric(19, 2) not null default 0,
    shipping_amount numeric(19, 2) not null default 0,
    payment_amount  numeric(19, 2) not null default 0,
    status          varchar(32) not null default 'NEW',
    created_at      timestamp with time zone not null default now(),
    updated_at      timestamp with time zone not null default now(),
    constraint ck_orders_subtotal_non_negative check (subtotal_amount >= 0),
    constraint ck_orders_shipping_non_negative check (shipping_amount >= 0),
    constraint ck_orders_payment_non_negative check (payment_amount >= 0)
);

create table order_items
(
    id         bigserial primary key,
    order_id   bigint not null references orders (id) on delete cascade,
    product_id bigint not null,
    title      varchar(255) not null,
    price      numeric(19, 2) not null,
    quantity   integer not null,
    line_total numeric(19, 2) not null,
    constraint ck_order_items_price_non_negative check (price >= 0),
    constraint ck_order_items_quantity_positive check (quantity > 0),
    constraint ck_order_items_line_total_non_negative check (line_total >= 0)
);

create index idx_orders_user_id on orders (user_id);
create index idx_order_items_order_id on order_items (order_id);
