-- Заказы
-- Orders
CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    total_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL DEFAULT 'NEW',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Позиции заказа
-- Order Items
CREATE TABLE order_items (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL,
    product_id    BIGINT NOT NULL,
    title         VARCHAR(255) NOT NULL,         -- зафиксированное имя товара
    price         DECIMAL(10,2) NOT NULL,        -- цена на момент покупки
    quantity      INTEGER NOT NULL CHECK (quantity > 0),
    line_total    DECIMAL(10,2) NOT NULL,

    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);
