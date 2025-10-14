-- 1) Категории товаров
create table if not exists categories
(
    id          bigserial primary key,      -- surrogate-ключ
    name        varchar(255) not null,      -- имя категории (отображается пользователю)
    slug        varchar(255) not null,      -- человеко-читаемый ключ для URL (например, "electronics")
    description text,                       -- опционально
    parent_id   bigint,                     -- для вложенных категорий (дерево)
    constraint uq_categories_slug unique (slug),
    constraint fk_categories_parent
    foreign key (parent_id) references categories(id)
    );

-- 2) Товары (пока минимум)
create table if not exists products
(
    id          bigserial primary key,
    title       varchar(255) not null,
    description text,
    price       numeric(19,2) not null,     -- MONEY лучше хранить как DECIMAL
    sku         varchar(64) not null,       -- артикул/уникальный код
    stock       int not null default 0,     -- остатки
    enabled     boolean not null default true,
    created_at  timestamp with time zone not null default now(),
    category_id bigint not null,
    constraint uq_products_sku unique (sku),
    constraint fk_products_category
    foreign key (category_id) references categories(id)
    );

-- 3) Изображения товара (сделаем сразу, но можно отложить)
create table if not exists product_images
(
    id         bigserial primary key,
    url        text not null,               -- хранить будем URL (S3, локальный CDN и т.д.)
    alt_text   varchar(255),
    position   int not null default 0,      -- сортировка изображений
    product_id bigint not null,
    constraint fk_product_images_product
    foreign key (product_id) references products(id)
    );
