CREATE TABLE orders (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT         NOT NULL REFERENCES users (id),
    status     VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    total      NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    created_at TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id BIGINT         NOT NULL REFERENCES products (id),
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);