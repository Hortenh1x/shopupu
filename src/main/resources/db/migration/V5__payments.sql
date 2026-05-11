create table payments
(
    id              bigserial primary key,
    order_id        bigint not null references orders (id) on delete cascade,
    provider        varchar(32) not null,
    external_id     varchar(128),
    client_secret   varchar(255),
    payment_url     varchar(512),
    client_token    varchar(255),
    amount          numeric(19, 2) not null,
    currency        varchar(8) not null,
    status          varchar(32) not null default 'CREATED',
    idempotency_key varchar(64) not null,
    created_at      timestamp with time zone not null default now(),
    updated_at      timestamp with time zone not null default now(),
    constraint uq_payments_external_id unique (external_id),
    constraint uq_payments_idempotency_key unique (idempotency_key),
    constraint ck_payments_amount_non_negative check (amount >= 0)
);

create index idx_payments_order_id on payments (order_id);
create index idx_payments_external_id on payments (external_id);
