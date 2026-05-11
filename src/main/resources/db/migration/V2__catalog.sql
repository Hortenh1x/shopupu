create table categories
(
    id          bigserial primary key,
    name        varchar(255) not null,
    slug        varchar(255) not null,
    description text,
    parent_id   bigint references categories (id),
    constraint uq_categories_slug unique (slug)
);

create table products
(
    id          bigserial primary key,
    title       varchar(255) not null,
    description text,
    price       numeric(19, 2) not null,
    sku         varchar(64) not null,
    stock       integer not null default 0,
    enabled     boolean not null default true,
    created_at  timestamp with time zone not null default now(),
    category_id bigint not null references categories (id),
    constraint uq_products_sku unique (sku),
    constraint ck_products_price_non_negative check (price >= 0),
    constraint ck_products_stock_non_negative check (stock >= 0)
);

create table product_images
(
    id         bigserial primary key,
    url        text not null,
    alt_text   varchar(255),
    position   integer not null default 0,
    product_id bigint not null references products (id) on delete cascade
);

create index idx_products_category_id on products (category_id);
create index idx_product_images_product_id on product_images (product_id);
