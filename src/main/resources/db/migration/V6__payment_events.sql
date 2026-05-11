create table payment_events
(
    id                bigserial primary key,
    payment_id        bigint not null references payments (id) on delete cascade,
    external_event_id varchar(128),
    new_status        varchar(32) not null,
    source            varchar(50) not null,
    details           text,
    created_at        timestamp with time zone not null default now(),
    constraint uq_payment_events_external_event_id unique (external_event_id)
);

create index idx_payment_events_payment_id on payment_events (payment_id);
