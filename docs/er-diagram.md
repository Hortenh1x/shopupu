# Доменная модель / ER-диаграмма

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : grants
    users ||--o{ refresh_tokens : owns
    users ||--o{ user_addresses : "address book"
    users ||--o{ wishlist_items : wants
    users ||--o{ user_consents : "consent journal"
    users ||--o| carts : "one cart"
    users ||--o{ orders : places
    users ||--o{ reviews : writes
    users ||--o{ promo_redemptions : redeems

    categories ||--o{ categories : "parent/child"
    categories ||--o{ products : contains
    brands ||--o{ products : brands
    products ||--o{ product_images : shows
    products ||--o{ product_variants : "size/color SKUs"
    products ||--o{ wishlist_items : listed
    products ||--o{ reviews : reviewed
    product_variants ||--|| inventory : "stock/reserved"
    product_variants ||--o{ inventory_movements : journalled
    product_variants ||--o{ cart_items : in

    carts ||--o{ cart_items : holds
    orders ||--o{ order_items : "snapshotted lines"
    orders ||--o{ order_status_history : tracked
    orders ||--o{ payments : "payment attempts"
    orders ||--o| shipments : ships
    payments ||--o{ payment_events : logged
    shipments }o--o| shipping_addresses : "per-order address"
    promo_codes ||--o{ promo_redemptions : counted

    products {
        bigint id PK
        varchar title
        varchar slug UK
        numeric price "base price"
        numeric old_price
        varchar gender
        varchar season
        varchar material
        timestamptz deleted_at "soft delete"
    }
    product_variants {
        bigint id PK
        varchar sku UK
        varchar size
        varchar color
        numeric price
        bigint version "optimistic lock"
    }
    inventory {
        bigint variant_id UK
        int stock
        int reserved
        bigint version
    }
    carts {
        bigint user_id "nullable"
        varchar guest_token UK "nullable"
    }
    orders {
        varchar order_number UK
        varchar idempotency_key "unique per user"
        varchar status "state machine"
        numeric subtotal_amount
        numeric shipping_amount
        numeric discount_amount
        numeric payment_amount
        varchar promo_code
    }
    order_items {
        varchar sku "snapshot"
        varchar size "snapshot"
        varchar color "snapshot"
        varchar brand "snapshot"
        numeric price "snapshot"
    }
    audit_events {
        varchar actor
        varchar event_type
        varchar target_type
        varchar target_id
    }
```

Замечания:

- `order_items` — неизменяемый снапшот (ORD-02): изменение каталога не меняет
  историю заказов; `audit_events` не связана FK — переживает удаление сущностей.
- `inventory.reserved` держат только неоплаченные заказы; доступно к продаже
  `stock - reserved`.
- Статусы заказа: CREATED → PENDING_PAYMENT → PAID → PROCESSING → SHIPPED →
  DELIVERED → COMPLETED, ветки CANCELLED (до оплаты) и REFUNDED (после).
```
