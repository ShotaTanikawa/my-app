CREATE TABLE product_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE products
    ADD COLUMN category_id BIGINT REFERENCES product_categories(id);

CREATE INDEX idx_product_categories_active_sort
    ON product_categories(active, sort_order, id);
CREATE INDEX idx_products_category_id
    ON products(category_id);
CREATE INDEX idx_products_updated_at
    ON products(updated_at DESC);
CREATE INDEX idx_products_lower_name
    ON products((LOWER(name)));
CREATE INDEX idx_products_lower_sku
    ON products((LOWER(sku)));

