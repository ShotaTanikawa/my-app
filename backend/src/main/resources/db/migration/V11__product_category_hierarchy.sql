ALTER TABLE product_categories
    ADD COLUMN parent_id BIGINT REFERENCES product_categories(id),
    ADD CONSTRAINT chk_product_categories_parent_not_self CHECK (parent_id IS NULL OR parent_id <> id);

CREATE INDEX idx_product_categories_parent_id
    ON product_categories(parent_id);
