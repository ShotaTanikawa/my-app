CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    contact_name VARCHAR(100),
    email VARCHAR(150),
    phone VARCHAR(50),
    note VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_suppliers (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    unit_cost NUMERIC(12, 2) NOT NULL,
    lead_time_days INTEGER NOT NULL DEFAULT 0,
    moq INTEGER NOT NULL DEFAULT 1,
    lot_size INTEGER NOT NULL DEFAULT 1,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_suppliers_product_supplier UNIQUE (product_id, supplier_id)
);

ALTER TABLE purchase_orders ADD COLUMN supplier_id BIGINT REFERENCES suppliers(id);

CREATE INDEX idx_suppliers_active_name ON suppliers(active, name);
CREATE INDEX idx_product_suppliers_product_id ON product_suppliers(product_id);
CREATE INDEX idx_product_suppliers_supplier_id ON product_suppliers(supplier_id);
CREATE INDEX idx_product_suppliers_product_primary ON product_suppliers(product_id, is_primary);
CREATE INDEX idx_purchase_orders_supplier_id ON purchase_orders(supplier_id);
