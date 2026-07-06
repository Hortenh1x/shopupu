-- Guest carts (CART-01/CART-02): a cart belongs to a user OR to an anonymous
-- guest identified by an opaque token; the guest cart merges into the user
-- cart at login. Plus versioned consent storage (USER-06/COMPL-05).

alter table carts
    alter column user_id drop not null,
    add column guest_token varchar(64),
    add constraint uq_carts_guest_token unique (guest_token),
    add constraint ck_carts_owner check (user_id is not null or guest_token is not null);

create table user_consents
(
    id             bigserial primary key,
    user_id        bigint      not null references users (id) on delete cascade,
    consent_type   varchar(32) not null,
    granted        boolean     not null,
    policy_version varchar(32) not null,
    created_at     timestamp with time zone not null default now(),
    constraint ck_user_consents_type check (consent_type in ('MARKETING_EMAIL', 'COOKIES_ANALYTICS', 'DATA_PROCESSING'))
);

create index idx_user_consents_user_id on user_consents (user_id);
