-- Clothing-shop domain: brands, product variants (size/color/SKU), inventory with
-- reservations, order snapshots, order numbers, status history, idempotent checkout.

-- === Brands =================================================================
create table brands
(
    id   bigserial primary key,
    name varchar(255) not null,
    slug varchar(255) not null,
    constraint uq_brands_slug unique (slug),
    constraint uq_brands_name unique (name)
);

-- === Product attributes for clothing =======================================
alter table products
    add column slug             varchar(255),
    add column brand_id         bigint references brands (id),
    add column gender           varchar(16) not null default 'UNISEX',
    add column season           varchar(32),
    add column material         varchar(255),
    add column care_instructions text,
    add column meta_title       varchar(255),
    add column meta_description varchar(512),
    add column old_price        numeric(19, 2),
    add column deleted_at       timestamp with time zone;

update products
set slug = trim(both '-' from regexp_replace(lower(title), '[^a-z0-9]+', '-', 'g')) || '-' || id;

alter table products
    alter column slug set not null;
alter table products
    add constraint uq_products_slug unique (slug);

-- === Product variants (SKU = product + size + color) =======================
create table product_variants
(
    id         bigserial primary key,
    product_id bigint       not null references products (id) on delete cascade,
    sku        varchar(64)  not null,
    size       varchar(32)  not null default 'ONE_SIZE',
    color      varchar(64),
    price      numeric(19, 2) not null,
    old_price  numeric(19, 2),
    enabled    boolean      not null default true,
    version    bigint       not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uq_product_variants_sku unique (sku),
    constraint ck_product_variants_price_non_negative check (price >= 0),
    constraint ck_product_variants_old_price_non_negative check (old_price is null or old_price >= 0)
);

create index idx_product_variants_product_id on product_variants (product_id);
create unique index uq_product_variants_product_size_color
    on product_variants (product_id, size, coalesce(color, ''));

-- every existing product becomes a single default variant
insert into product_variants (product_id, sku, size, color, price, enabled)
select id, sku, 'ONE_SIZE', null, price, enabled
from products;

-- === Inventory with reservations ============================================
create table inventory
(
    id         bigserial primary key,
    variant_id bigint  not null references product_variants (id) on delete cascade,
    stock      integer not null default 0,
    reserved   integer not null default 0,
    version    bigint  not null default 0,
    updated_at timestamp with time zone not null default now(),
    constraint uq_inventory_variant unique (variant_id),
    constraint ck_inventory_stock_non_negative check (stock >= 0),
    constraint ck_inventory_reserved_non_negative check (reserved >= 0),
    constraint ck_inventory_reserved_lte_stock check (reserved <= stock)
);

insert into inventory (variant_id, stock)
select pv.id, p.stock
from product_variants pv
         join products p on p.id = pv.product_id;

create table inventory_movements
(
    id            bigserial primary key,
    variant_id    bigint      not null references product_variants (id) on delete cascade,
    movement_type varchar(16) not null,
    quantity      integer     not null,
    reference     varchar(128),
    created_at    timestamp with time zone not null default now()
);

create index idx_inventory_movements_variant_id on inventory_movements (variant_id);

-- === Cart items now reference variants ======================================
alter table cart_items
    add column variant_id bigint references product_variants (id) on delete cascade;

update cart_items ci
set variant_id = pv.id
from product_variants pv
where pv.product_id = ci.product_id;

alter table cart_items
    alter column variant_id set not null;
alter table cart_items
    drop constraint uq_cart_items_cart_product;
alter table cart_items
    drop column product_id;
alter table cart_items
    add constraint uq_cart_items_cart_variant unique (cart_id, variant_id);
create index idx_cart_items_variant_id on cart_items (variant_id);

-- === Order item snapshots ====================================================
alter table order_items
    add column variant_id bigint,
    add column sku        varchar(64),
    add column size       varchar(32),
    add column color      varchar(64),
    add column brand      varchar(255);

create index idx_order_items_product_id on order_items (product_id);

-- === Orders: number, idempotency, richer state machine ======================
alter table orders
    add column order_number    varchar(32),
    add column idempotency_key varchar(80);

update orders
set order_number = 'ORD-' || to_char(created_at, 'YYYYMMDD') || '-' || lpad(id::text, 6, '0');

alter table orders
    alter column order_number set not null;
alter table orders
    add constraint uq_orders_order_number unique (order_number);
create unique index uq_orders_user_idempotency
    on orders (user_id, idempotency_key) where idempotency_key is not null;

update orders set status = 'CREATED' where status = 'NEW';
update orders set status = 'CANCELLED' where status = 'CANCELED';
alter table orders
    alter column status set default 'CREATED';

create index idx_orders_status on orders (status);
create index idx_orders_created_at on orders (created_at);

create table order_status_history
(
    id          bigserial primary key,
    order_id    bigint      not null references orders (id) on delete cascade,
    from_status varchar(32),
    to_status   varchar(32) not null,
    changed_by  varchar(255) not null,
    created_at  timestamp with time zone not null default now()
);

create index idx_order_status_history_order_id on order_status_history (order_id);

-- === Shipment address snapshot (order history immutability) =================
alter table shipments
    add column address_snapshot text;

-- === Products: SKU and stock now live on variants/inventory ==================
alter table products
    drop constraint uq_products_sku;
alter table products
    drop column sku;
alter table products
    drop column stock;

-- === Search/filter indexes ===================================================
create index idx_products_enabled on products (enabled);
create index idx_products_brand_id on products (brand_id);
create index idx_products_gender on products (gender);
