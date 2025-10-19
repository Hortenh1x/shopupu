-- Shipping addresses and shipments

CREATE TABLE IF NOT EXISTS shipping_addresses (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(128) NOT NULL,
    line1 VARCHAR(128) NOT NULL,
    line2 VARCHAR(128),
    city VARCHAR(64) NOT NULL,
    state VARCHAR(64) NOT NULL,
    postal_code VARCHAR(16) NOT NULL,
    country VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS shipments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    address_id BIGINT REFERENCES shipping_addresses(id) ON DELETE SET NULL,
    method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    cost NUMERIC(10,2) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    tracking_number VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shipments_order_id ON shipments(order_id);

