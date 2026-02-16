ALTER TABLE purchase_order_items
    ADD COLUMN received_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE purchase_order_items
    ADD CONSTRAINT chk_purchase_order_items_received_quantity
        CHECK (received_quantity >= 0 AND received_quantity <= quantity);

UPDATE purchase_order_items poi
SET received_quantity = poi.quantity
FROM purchase_orders po
WHERE poi.purchase_order_id = po.id
  AND po.status = 'RECEIVED';
