 create table users
(
    id            bigserial primary key,
    email         varchar(255) not null,
    password_hash varchar(255) not null,
    username      varchar(255),
    enabled       boolean      not null default true,
    constraint uq_users_email unique (email),
    constraint uq_users_username unique (username)
);

create table roles
(
    id   bigserial primary key,
    name varchar(64) not null,
    constraint uq_roles_name unique (name)
);

create table user_roles
(
    user_id bigint not null references users (id) on delete cascade,
    role_id bigint not null references roles (id) on delete cascade,
    constraint pk_user_roles primary key (user_id, role_id)
);

create table refresh_tokens
(
    id         bigserial primary key,
    user_id    bigint       not null references users (id) on delete cascade,
    token      varchar(255) not null,
    expires_at timestamp with time zone not null,
    revoked    boolean      not null default false,
    created_at timestamp with time zone not null default now(),
    constraint uq_refresh_tokens_token unique (token)
);

create index idx_refresh_tokens_user_id on refresh_tokens (user_id);

insert into roles (name)
values ('CUSTOMER'),
       ('ADMIN')
on conflict (name) do nothing;
