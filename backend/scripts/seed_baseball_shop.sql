BEGIN;

-- =========================================================
-- アイスホッケー + フィギュアスケートショップ向けローカルサンプル
-- 再実行しても同じ状態になるように、サンプル対象を一度削除して再投入する
-- =========================================================

-- 旧サンプル（野球/冬競技）の監査ログを削除
DELETE FROM audit_logs
WHERE detail LIKE '[SAMPLE-BASEBALL]%'
   OR detail LIKE '[SAMPLE-WINTER]%';

-- 旧サンプル（野球/冬競技）の受注・仕入発注を削除
DELETE FROM purchase_order_receipt_items pri
USING purchase_order_receipts pr, purchase_orders po
WHERE pri.receipt_id = pr.id
  AND pr.purchase_order_id = po.id
  AND (po.order_number LIKE 'PO-BB-%' OR po.order_number LIKE 'PO-WS-%');

DELETE FROM purchase_order_receipts pr
USING purchase_orders po
WHERE pr.purchase_order_id = po.id
  AND (po.order_number LIKE 'PO-BB-%' OR po.order_number LIKE 'PO-WS-%');

DELETE FROM purchase_order_items poi
USING purchase_orders po
WHERE poi.purchase_order_id = po.id
  AND (po.order_number LIKE 'PO-BB-%' OR po.order_number LIKE 'PO-WS-%');

DELETE FROM purchase_orders
WHERE order_number LIKE 'PO-BB-%'
   OR order_number LIKE 'PO-WS-%';

DELETE FROM sales_order_items soi
USING sales_orders so
WHERE soi.order_id = so.id
  AND (so.order_number LIKE 'SO-BB-%' OR so.order_number LIKE 'SO-WS-%');

DELETE FROM sales_orders
WHERE order_number LIKE 'SO-BB-%'
   OR order_number LIKE 'SO-WS-%';

-- 旧サンプル商品/仕入先を削除
DELETE FROM product_suppliers
WHERE supplier_id IN (
  SELECT id FROM suppliers WHERE code LIKE 'SUP-BB-%' OR code LIKE 'SUP-WS-%'
)
   OR product_id IN (
  SELECT id FROM products WHERE sku LIKE 'BB-%' OR sku LIKE 'IH-%' OR sku LIKE 'FS-%'
);

DELETE FROM inventories
WHERE product_id IN (
  SELECT id FROM products WHERE sku LIKE 'BB-%' OR sku LIKE 'IH-%' OR sku LIKE 'FS-%'
);

DELETE FROM products
WHERE sku LIKE 'BB-%'
   OR sku LIKE 'IH-%'
   OR sku LIKE 'FS-%';

DELETE FROM suppliers
WHERE code LIKE 'SUP-BB-%'
   OR code LIKE 'SUP-WS-%';

DELETE FROM product_categories
WHERE code IN (
  'BAT', 'GLOVE', 'BALL', 'SHOES', 'PROTECTOR', 'APPAREL', 'BAG', 'TRAINING',
  'IH_STICK', 'IH_SKATE', 'IH_PROTECT', 'IH_GOALIE', 'IH_ACCESSORY',
  'FS_SKATE', 'FS_APPAREL', 'FS_ACCESSORY'
);

-- 商品カテゴリ
INSERT INTO product_categories (code, name, active, sort_order, created_at, updated_at)
VALUES
  ('IH_STICK', 'アイスホッケースティック', TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('IH_SKATE', 'アイスホッケースケート', TRUE, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('IH_PROTECT', 'ホッケー防具', TRUE, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('IH_GOALIE', 'ゴーリー用品', TRUE, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('IH_ACCESSORY', 'ホッケーアクセサリ', TRUE, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('FS_SKATE', 'フィギュアスケート靴・ブレード', TRUE, 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('FS_APPAREL', 'フィギュアウェア', TRUE, 70, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('FS_ACCESSORY', 'フィギュアアクセサリ', TRUE, 80, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 商品マスタ
INSERT INTO products (sku, name, description, unit_price, reorder_point, reorder_quantity, created_at, updated_at)
VALUES
  ('IH-STICK-001', 'カーボンホッケースティック SR', '軽量カーボンシャフト。競技者向け。', 24800, 8, 16, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('IH-STICK-002', 'ジュニアホッケースティック 50"', 'ジュニア向けフレックスモデル。', 12800, 12, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('IH-SKATE-001', 'アイスホッケースケート Senior 8.0', '足首サポート重視の競技用。', 39800, 5, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('IH-HELMET-001', 'フルフェイスヘルメット M', '衝撃吸収ライナー搭載。', 16800, 10, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('IH-GLOVE-001', 'ホッケーグローブ Pro', '高耐久パーム・高可動モデル。', 14900, 10, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('IH-PUCK-001', '公式パック 12個セット', '公式規格パックの練習向けセット。', 6200, 20, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('FS-SKATE-001', 'フィギュアスケート靴 Ladies 245', '中上級者向けブーツ。', 45800, 4, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('FS-BLADE-001', '競技用ブレード セット', 'ダブル/トリプル対応の競技用ブレード。', 29800, 6, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('FS-DRESS-001', '練習用ドレス ネイビー', 'ストレッチ素材の練習用ドレス。', 13800, 8, 16, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('FS-TIGHTS-001', 'スケートタイツ 2足組', '高耐久マイクロファイバー。', 4200, 18, 36, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('FS-BAG-001', 'スケートバッグ 45L', 'ブレードカバー収納付き。', 9800, 8, 16, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 商品カテゴリ紐付け
UPDATE products p
SET category_id = c.id
FROM product_categories c
WHERE p.sku IN ('IH-STICK-001', 'IH-STICK-002')
  AND c.code = 'IH_STICK';

UPDATE products p
SET category_id = c.id
FROM product_categories c
WHERE p.sku = 'IH-SKATE-001'
  AND c.code = 'IH_SKATE';

UPDATE products p
SET category_id = c.id
FROM product_categories c
WHERE p.sku IN ('IH-HELMET-001', 'IH-GLOVE-001')
  AND c.code = 'IH_PROTECT';

UPDATE products p
SET category_id = c.id
FROM product_categories c
WHERE p.sku = 'IH-PUCK-001'
  AND c.code = 'IH_ACCESSORY';

UPDATE products p
SET category_id = c.id
FROM product_categories c
WHERE p.sku IN ('FS-SKATE-001', 'FS-BLADE-001')
  AND c.code = 'FS_SKATE';

UPDATE products p
SET category_id = c.id
FROM product_categories c
WHERE p.sku = 'FS-DRESS-001'
  AND c.code = 'FS_APPAREL';

UPDATE products p
SET category_id = c.id
FROM product_categories c
WHERE p.sku IN ('FS-TIGHTS-001', 'FS-BAG-001')
  AND c.code = 'FS_ACCESSORY';

-- 在庫
INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 12, 3, 0 FROM products WHERE sku = 'IH-STICK-001';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 18, 2, 0 FROM products WHERE sku = 'IH-STICK-002';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 4, 1, 0 FROM products WHERE sku = 'IH-SKATE-001';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 9, 2, 0 FROM products WHERE sku = 'IH-HELMET-001';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 7, 2, 0 FROM products WHERE sku = 'IH-GLOVE-001';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 28, 6, 0 FROM products WHERE sku = 'IH-PUCK-001';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 3, 1, 0 FROM products WHERE sku = 'FS-SKATE-001';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 5, 1, 0 FROM products WHERE sku = 'FS-BLADE-001';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 11, 2, 0 FROM products WHERE sku = 'FS-DRESS-001';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 24, 4, 0 FROM products WHERE sku = 'FS-TIGHTS-001';

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 10, 1, 0 FROM products WHERE sku = 'FS-BAG-001';

-- 仕入先マスタ
INSERT INTO suppliers (code, name, contact_name, email, phone, note, active, created_at, updated_at)
VALUES
  ('SUP-WS-01', 'North Ice Sports', 'Mika Sato', 'north-ice@example.com', '03-7000-1001', 'ホッケー用品の定番在庫が豊富。', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('SUP-WS-02', 'Rink Pro Trading', 'Ken Ito', 'rink-pro@example.com', '06-7000-1002', '防具・消耗品を短納期で供給。', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('SUP-WS-03', 'Glide Atelier', 'Aya Nakamura', 'glide-atelier@example.com', '052-7000-1003', 'フィギュア用品に特化。', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 商品 × 仕入先契約
INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 17800, 5, 6, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'IH-STICK-001' AND s.code = 'SUP-WS-01';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 9200, 4, 10, 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'IH-STICK-002' AND s.code = 'SUP-WS-01';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 31000, 7, 4, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'IH-SKATE-001' AND s.code = 'SUP-WS-01';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 11800, 4, 8, 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'IH-HELMET-001' AND s.code = 'SUP-WS-02';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 10200, 4, 8, 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'IH-GLOVE-001' AND s.code = 'SUP-WS-02';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 4400, 3, 24, 12, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'IH-PUCK-001' AND s.code = 'SUP-WS-02';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 36800, 8, 2, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'FS-SKATE-001' AND s.code = 'SUP-WS-03';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 22800, 8, 4, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'FS-BLADE-001' AND s.code = 'SUP-WS-03';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 9800, 5, 6, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'FS-DRESS-001' AND s.code = 'SUP-WS-03';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 2600, 3, 12, 6, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'FS-TIGHTS-001' AND s.code = 'SUP-WS-03';

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 6800, 5, 6, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'FS-BAG-001' AND s.code = 'SUP-WS-03';

-- 受注ヘッダ
INSERT INTO sales_orders (order_number, customer_name, status, created_at, updated_at)
VALUES
  ('SO-WS-2026-0001', '札幌アイスホークス', 'RESERVED', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'),
  ('SO-WS-2026-0002', '横浜リンクスターズ', 'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
  ('SO-WS-2026-0003', '名古屋フィギュアクラブ', 'CANCELLED', CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '4 days'),
  ('SO-WS-2026-0004', '福岡ジュニアリンク', 'RESERVED', CURRENT_TIMESTAMP - INTERVAL '10 hours', CURRENT_TIMESTAMP - INTERVAL '10 hours');

-- 受注明細
INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 2, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-WS-2026-0001' AND p.sku = 'IH-STICK-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 6, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-WS-2026-0001' AND p.sku = 'IH-PUCK-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 3, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-WS-2026-0002' AND p.sku = 'IH-HELMET-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 2, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-WS-2026-0002' AND p.sku = 'IH-GLOVE-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 1, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-WS-2026-0003' AND p.sku = 'FS-SKATE-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 2, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-WS-2026-0003' AND p.sku = 'FS-DRESS-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 4, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-WS-2026-0004' AND p.sku = 'FS-TIGHTS-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 1, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-WS-2026-0004' AND p.sku = 'FS-BAG-001';

-- 仕入発注ヘッダ
INSERT INTO purchase_orders (order_number, supplier_id, supplier_name, note, status, created_at, updated_at, received_at)
SELECT 'PO-WS-2026-0001', s.id, s.name, 'ホッケースティックとパックの追加仕入', 'ORDERED',
       CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL
FROM suppliers s
WHERE s.code = 'SUP-WS-01';

INSERT INTO purchase_orders (order_number, supplier_id, supplier_name, note, status, created_at, updated_at, received_at)
SELECT 'PO-WS-2026-0002', s.id, s.name, '防具の補充（分納中）', 'PARTIALLY_RECEIVED',
       CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL
FROM suppliers s
WHERE s.code = 'SUP-WS-02';

INSERT INTO purchase_orders (order_number, supplier_id, supplier_name, note, status, created_at, updated_at, received_at)
SELECT 'PO-WS-2026-0003', s.id, s.name, 'フィギュア用品の定期補充（完了）', 'RECEIVED',
       CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM suppliers s
WHERE s.code = 'SUP-WS-03';

-- 仕入発注明細
INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 20, 0, 9200
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-WS-2026-0001' AND p.sku = 'IH-STICK-002';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 48, 0, 4400
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-WS-2026-0001' AND p.sku = 'IH-PUCK-001';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 24, 10, 11800
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-WS-2026-0002' AND p.sku = 'IH-HELMET-001';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 20, 8, 10200
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-WS-2026-0002' AND p.sku = 'IH-GLOVE-001';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 10, 10, 22800
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-WS-2026-0003' AND p.sku = 'FS-BLADE-001';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 16, 16, 9800
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-WS-2026-0003' AND p.sku = 'FS-DRESS-001';

-- 入荷履歴（分納中）
WITH receipt AS (
  INSERT INTO purchase_order_receipts (purchase_order_id, received_by, received_at)
  SELECT po.id, 'operator', CURRENT_TIMESTAMP - INTERVAL '1 day'
  FROM purchase_orders po
  WHERE po.order_number = 'PO-WS-2026-0002'
  RETURNING id
)
INSERT INTO purchase_order_receipt_items (receipt_id, product_id, quantity)
SELECT r.id, p.id, v.qty
FROM receipt r
JOIN (VALUES ('IH-HELMET-001', 10), ('IH-GLOVE-001', 8)) AS v(sku, qty) ON TRUE
JOIN products p ON p.sku = v.sku;

-- 入荷履歴（入荷完了: 1回目）
WITH receipt AS (
  INSERT INTO purchase_order_receipts (purchase_order_id, received_by, received_at)
  SELECT po.id, 'operator', CURRENT_TIMESTAMP - INTERVAL '3 days'
  FROM purchase_orders po
  WHERE po.order_number = 'PO-WS-2026-0003'
  RETURNING id
)
INSERT INTO purchase_order_receipt_items (receipt_id, product_id, quantity)
SELECT r.id, p.id, v.qty
FROM receipt r
JOIN (VALUES ('FS-BLADE-001', 6), ('FS-DRESS-001', 9)) AS v(sku, qty) ON TRUE
JOIN products p ON p.sku = v.sku;

-- 入荷履歴（入荷完了: 2回目）
WITH receipt AS (
  INSERT INTO purchase_order_receipts (purchase_order_id, received_by, received_at)
  SELECT po.id, 'admin', CURRENT_TIMESTAMP - INTERVAL '2 days'
  FROM purchase_orders po
  WHERE po.order_number = 'PO-WS-2026-0003'
  RETURNING id
)
INSERT INTO purchase_order_receipt_items (receipt_id, product_id, quantity)
SELECT r.id, p.id, v.qty
FROM receipt r
JOIN (VALUES ('FS-BLADE-001', 4), ('FS-DRESS-001', 7)) AS v(sku, qty) ON TRUE
JOIN products p ON p.sku = v.sku;

-- 監査ログ（サンプル）
INSERT INTO audit_logs (actor_username, actor_role, action, target_type, target_id, detail, created_at)
VALUES
  ('admin', 'ADMIN', 'PRODUCT_CREATE', 'PRODUCT', 'IH-STICK-001', '[SAMPLE-WINTER] 商品マスタを初期投入', CURRENT_TIMESTAMP - INTERVAL '2 days'),
  ('operator', 'OPERATOR', 'ORDER_CREATE', 'SALES_ORDER', 'SO-WS-2026-0001', '[SAMPLE-WINTER] 受注サンプルを登録', CURRENT_TIMESTAMP - INTERVAL '1 day'),
  ('operator', 'OPERATOR', 'PURCHASE_ORDER_RECEIVE', 'PURCHASE_ORDER', 'PO-WS-2026-0002', '[SAMPLE-WINTER] 分納入荷を登録', CURRENT_TIMESTAMP - INTERVAL '1 day');

COMMIT;
