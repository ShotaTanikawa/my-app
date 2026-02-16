ALTER TABLE product_categories
    ADD COLUMN sku_prefix VARCHAR(20),
    ADD COLUMN sku_sequence_digits INTEGER NOT NULL DEFAULT 4;

CREATE INDEX idx_product_categories_sku_prefix
    ON product_categories(sku_prefix);
