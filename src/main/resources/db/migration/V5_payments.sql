CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,

    -- Связь с заказом
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,

    -- Внешний ID транзакции у платёжного провайдера
    external_id VARCHAR(128),

    -- Имя провайдера (STRIPE, PAYPAL, MOCK, ...)
    provider VARCHAR(50) NOT NULL,

    -- Финансовые параметры
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,

    -- Статус платежа
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',

    -- Ключ идемпотентности (уникальный)
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,

    -- Технические поля
    provider_payload TEXT,
    client_secret VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для быстрого поиска
CREATE INDEX IF NOT EXISTS idx_payment_external_id ON payments(external_id);
CREATE INDEX IF NOT EXISTS idx_payment_provider ON payments(provider);