CREATE TABLE purchase_order_receipts (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    received_by VARCHAR(100) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE purchase_order_receipt_items (
    id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES purchase_order_receipts(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL,
    CONSTRAINT chk_purchase_order_receipt_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_purchase_order_receipts_purchase_order_id
    ON purchase_order_receipts(purchase_order_id);
CREATE INDEX idx_purchase_order_receipts_received_at
    ON purchase_order_receipts(received_at DESC);
CREATE INDEX idx_purchase_order_receipt_items_receipt_id
    ON purchase_order_receipt_items(receipt_id);
