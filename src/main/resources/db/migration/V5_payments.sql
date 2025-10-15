-- RU: Платеж, связанный с заказом. Денежные суммы в minor units/BigDecimal.
-- EN: Payment linked to an order. Amounts with BigDecimal.

CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id),
    amount          NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    status          VARCHAR(32) NOT NULL,       -- NEW, REQUIRES_ACTION, AUTHORIZED, CAPTURED, CANCELED, FAILED, REFUNDED
    provider        VARCHAR(64) NOT NULL,       -- e.g. "FAKE", "STRIPE"
    provider_pid    VARCHAR(128),               -- provider payment id / intent id
    client_secret   VARCHAR(128),               -- для клиентских SDK (если нужно)
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order ON payments(order_id);
CREATE UNIQUE INDEX uq_payments_provider_pid ON payments(provider_pid) WHERE provider_pid IS NOT NULL;
