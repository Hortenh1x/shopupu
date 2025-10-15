-- Корзина и позиции корзины
-- Cart and Cart Items

-- 1) Таблица корзин. У каждого пользователя ровно 1 активная корзина
-- 1) Carts table. Each user has exactly 1 active cart
CREATE TABLE carts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE,  -- одна корзина на пользователя / one cart per user
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 2) Позиции в корзине (товар + количество)
-- 2) Cart items (product + quantity)
CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_item_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT uq_cart_product UNIQUE (cart_id, product_id)  -- один товар один раз в корзине / unique per cart
);
