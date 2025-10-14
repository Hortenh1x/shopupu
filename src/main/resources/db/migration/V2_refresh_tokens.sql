create table if not exists refresh_tokens (
    id           bigserial primary key,
    user_id      bigint       not null references users(id) on delete cascade,
    token        varchar(255) not null unique,
    expires_at   timestamp    not null,
    revoked      boolean      not null default false,
    created_at   timestamp    not null default now()
);

create index if not exists idx_refresh_tokens_user on refresh_tokens(user_id);
