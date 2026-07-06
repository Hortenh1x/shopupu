-- Promo codes (PROMO-01..03) + order discount fields (ORD-04)

create table promo_codes
(
    id               bigserial primary key,
    code             varchar(64)  not null,
    promo_type       varchar(16)  not null,
    value            numeric(19, 2) not null default 0,
    min_order_amount numeric(19, 2),
    starts_at        timestamp with time zone,
    ends_at          timestamp with time zone,
    max_redemptions  integer,
    per_user_limit   integer      not null default 1,
    redemption_count integer      not null default 0,
    enabled          boolean      not null default true,
    version          bigint       not null default 0,
    created_at       timestamp with time zone not null default now(),
    constraint uq_promo_codes_code unique (code),
    constraint ck_promo_codes_type check (promo_type in ('PERCENT', 'FIXED', 'FREE_SHIPPING')),
    constraint ck_promo_codes_value_non_negative check (value >= 0),
    constraint ck_promo_codes_counts check (redemption_count >= 0)
);

create table promo_redemptions
(
    id         bigserial primary key,
    promo_id   bigint not null references promo_codes (id) on delete cascade,
    user_id    bigint not null references users (id) on delete cascade,
    order_id   bigint references orders (id) on delete set null,
    created_at timestamp with time zone not null default now()
);

create index idx_promo_redemptions_promo_id on promo_redemptions (promo_id);
create index idx_promo_redemptions_user_id on promo_redemptions (user_id);

alter table orders
    add column discount_amount numeric(19, 2) not null default 0,
    add column promo_code varchar(64);

alter table orders
    add constraint ck_orders_discount_non_negative check (discount_amount >= 0);
