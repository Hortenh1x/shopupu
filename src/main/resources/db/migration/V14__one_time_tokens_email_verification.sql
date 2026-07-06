-- One-time tokens for password reset (AUTH-07) and email verification (AUTH-06).

alter table users
    add column email_verified boolean not null default false;

-- accounts created before verification existed are grandfathered in
update users
set email_verified = true;

create table one_time_tokens
(
    id         bigserial primary key,
    user_id    bigint      not null references users (id) on delete cascade,
    token_hash varchar(64) not null,
    purpose    varchar(32) not null,
    expires_at timestamp with time zone not null,
    used_at    timestamp with time zone,
    created_at timestamp with time zone not null default now(),
    constraint uq_one_time_tokens_hash unique (token_hash),
    constraint ck_one_time_tokens_purpose check (purpose in ('PASSWORD_RESET', 'EMAIL_VERIFICATION'))
);

create index idx_one_time_tokens_user_id on one_time_tokens (user_id);
create index idx_one_time_tokens_expires_at on one_time_tokens (expires_at);
