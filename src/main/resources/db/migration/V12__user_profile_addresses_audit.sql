-- User profile (USER-01), address book (USER-02), wishlist (USER-03),
-- GDPR anonymization support (USER-05) and security/admin audit trail (SEC-14).

alter table users
    add column first_name     varchar(128),
    add column last_name      varchar(128),
    add column phone          varchar(32),
    add column preferred_size varchar(32),
    add column deleted_at     timestamp with time zone;

create table user_addresses
(
    id          bigserial primary key,
    user_id     bigint       not null references users (id) on delete cascade,
    full_name   varchar(128) not null,
    line1       varchar(128) not null,
    line2       varchar(128),
    city        varchar(64)  not null,
    state       varchar(64),
    postal_code varchar(16)  not null,
    country     varchar(64)  not null,
    is_default  boolean      not null default false,
    created_at  timestamp with time zone not null default now(),
    updated_at  timestamp with time zone not null default now()
);

create index idx_user_addresses_user_id on user_addresses (user_id);
create unique index uq_user_addresses_single_default
    on user_addresses (user_id) where is_default;

create table wishlist_items
(
    id         bigserial primary key,
    user_id    bigint not null references users (id) on delete cascade,
    product_id bigint not null references products (id) on delete cascade,
    created_at timestamp with time zone not null default now(),
    constraint uq_wishlist_user_product unique (user_id, product_id)
);

create index idx_wishlist_items_user_id on wishlist_items (user_id);

create table audit_events
(
    id          bigserial primary key,
    actor       varchar(255),
    event_type  varchar(64) not null,
    target_type varchar(64),
    target_id   varchar(64),
    details     varchar(512),
    created_at  timestamp with time zone not null default now()
);

create index idx_audit_events_created_at on audit_events (created_at);
create index idx_audit_events_actor on audit_events (actor);
create index idx_audit_events_event_type on audit_events (event_type);
