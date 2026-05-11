create table shipping_addresses
(
    id          bigserial primary key,
    full_name   varchar(128) not null,
    line1       varchar(128) not null,
    line2       varchar(128),
    city        varchar(64) not null,
    state       varchar(64) not null,
    postal_code varchar(16) not null,
    country     varchar(64) not null
);

create table shipments
(
    id              bigserial primary key,
    order_id        bigint not null references orders (id) on delete cascade,
    address_id      bigint references shipping_addresses (id) on delete set null,
    method          varchar(32) not null,
    status          varchar(32) not null,
    cost            numeric(19, 2) not null,
    currency        varchar(8) not null,
    tracking_number varchar(64),
    created_at      timestamp with time zone not null default now(),
    updated_at      timestamp with time zone not null default now(),
    constraint uq_shipments_order_id unique (order_id),
    constraint ck_shipments_cost_non_negative check (cost >= 0)
);

create index idx_shipments_order_id on shipments (order_id);
create index idx_shipments_address_id on shipments (address_id);
