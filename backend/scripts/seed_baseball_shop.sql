BEGIN;

-- =========================================================
-- 野球ショップ運用を想定したローカル用サンプルデータ
-- 再実行しても同じ状態になるようにUPSERT/再作成を使用
-- =========================================================

-- 商品マスタ
INSERT INTO products (sku, name, description, unit_price, reorder_point, reorder_quantity, created_at, updated_at)
VALUES
  ('BB-BAT-001', '硬式木製バット 84cm', '高校野球対応の硬式木製バット。', 9800, 12, 24, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-BAT-002', '軟式複合バット 83cm', '反発性能を重視した軟式用複合バット。', 42800, 5, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-GLOVE-001', '硬式内野手グローブ', '硬式内野手向け。ポケット深め。', 29800, 8, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-GLOVE-002', '軟式オールラウンドグローブ', '中学生向け軟式オールラウンドモデル。', 16800, 10, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-BALL-001', '硬式試合球 1ダース', '硬式公式規格の試合球。1ダース単位。', 7800, 15, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-BALL-002', '軟式J号球 1ダース', '少年軟式J号規格。1ダース単位。', 6200, 20, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-CLEAT-001', 'ポイントスパイク 26.5cm', '軽量モデル。交換用インソール付き。', 12800, 6, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-HELMET-001', '打者用ヘルメット M', 'フェイスガード取付対応。', 8900, 8, 16, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-PROTECT-001', 'キャッチャー防具セット', 'マスク・プロテクター・レガースのセット。', 35800, 3, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-UNIFORM-001', '練習用ユニフォーム上下 L', '吸汗速乾素材の練習用セット。', 11800, 10, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-BAG-001', '遠征バッグ 65L', 'チーム遠征向けの大容量バッグ。', 14800, 5, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('BB-NET-001', 'バッティングネット 2m', '自主練習用の折りたたみネット。', 24800, 4, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (sku) DO UPDATE
SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  unit_price = EXCLUDED.unit_price,
  reorder_point = EXCLUDED.reorder_point,
  reorder_quantity = EXCLUDED.reorder_quantity,
  updated_at = CURRENT_TIMESTAMP;

-- 在庫
INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 18, 4, 0 FROM products WHERE sku = 'BB-BAT-001'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 3, 2, 0 FROM products WHERE sku = 'BB-BAT-002'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 6, 4, 0 FROM products WHERE sku = 'BB-GLOVE-001'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 14, 2, 0 FROM products WHERE sku = 'BB-GLOVE-002'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 22, 5, 0 FROM products WHERE sku = 'BB-BALL-001'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 30, 8, 0 FROM products WHERE sku = 'BB-BALL-002'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 8, 2, 0 FROM products WHERE sku = 'BB-CLEAT-001'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 7, 2, 0 FROM products WHERE sku = 'BB-HELMET-001'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 2, 1, 0 FROM products WHERE sku = 'BB-PROTECT-001'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 16, 4, 0 FROM products WHERE sku = 'BB-UNIFORM-001'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 7, 1, 0 FROM products WHERE sku = 'BB-BAG-001'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

INSERT INTO inventories (product_id, available_quantity, reserved_quantity, version)
SELECT id, 5, 0, 0 FROM products WHERE sku = 'BB-NET-001'
ON CONFLICT (product_id) DO UPDATE
SET available_quantity = EXCLUDED.available_quantity, reserved_quantity = EXCLUDED.reserved_quantity;

-- 仕入先マスタ
INSERT INTO suppliers (code, name, contact_name, email, phone, note, active, created_at, updated_at)
VALUES
  ('SUP-BB-01', 'ミズノ商事', '田中 一郎', 'mizuno-sales@example.com', '03-1111-2222', '即納品中心。', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('SUP-BB-02', 'ゼット流通', '山本 次郎', 'zett-order@example.com', '06-3333-4444', '消耗品のロット発注に強い。', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('SUP-BB-03', 'ローリングス・ジャパン販売', '鈴木 花子', 'rawlings-b2b@example.com', '052-555-6666', '防具・バッグ系の仕入単価が安い。', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE
SET
  name = EXCLUDED.name,
  contact_name = EXCLUDED.contact_name,
  email = EXCLUDED.email,
  phone = EXCLUDED.phone,
  note = EXCLUDED.note,
  active = EXCLUDED.active,
  updated_at = CURRENT_TIMESTAMP;

-- 商品 × 仕入先契約
INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 7200, 5, 12, 6, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-BAT-001' AND s.code = 'SUP-BB-01'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 31500, 7, 5, 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-BAT-002' AND s.code = 'SUP-BB-01'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 21000, 6, 6, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-GLOVE-001' AND s.code = 'SUP-BB-02'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 11800, 4, 10, 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-GLOVE-002' AND s.code = 'SUP-BB-02'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 5200, 3, 12, 12, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-BALL-001' AND s.code = 'SUP-BB-02'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 4200, 3, 24, 12, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-BALL-002' AND s.code = 'SUP-BB-02'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 8600, 5, 6, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-CLEAT-001' AND s.code = 'SUP-BB-01'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 6500, 5, 8, 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-HELMET-001' AND s.code = 'SUP-BB-02'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 25500, 8, 2, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-PROTECT-001' AND s.code = 'SUP-BB-03'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 8200, 4, 10, 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-UNIFORM-001' AND s.code = 'SUP-BB-01'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 10200, 6, 4, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-BAG-001' AND s.code = 'SUP-BB-03'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_suppliers (product_id, supplier_id, unit_cost, lead_time_days, moq, lot_size, is_primary, created_at, updated_at)
SELECT p.id, s.id, 17800, 7, 3, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM products p, suppliers s
WHERE p.sku = 'BB-NET-001' AND s.code = 'SUP-BB-03'
ON CONFLICT (product_id, supplier_id) DO UPDATE
SET unit_cost = EXCLUDED.unit_cost, lead_time_days = EXCLUDED.lead_time_days, moq = EXCLUDED.moq, lot_size = EXCLUDED.lot_size, is_primary = EXCLUDED.is_primary, updated_at = CURRENT_TIMESTAMP;

-- 受注ヘッダ
INSERT INTO sales_orders (order_number, customer_name, status, created_at, updated_at)
VALUES
  ('SO-BB-2026-0001', '神戸ストームズ', 'RESERVED', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'),
  ('SO-BB-2026-0002', '大阪サンダース', 'CONFIRMED', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
  ('SO-BB-2026-0003', '京都ブルーウェーブ', 'CANCELLED', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '5 days'),
  ('SO-BB-2026-0004', '奈良フェニックス', 'RESERVED', CURRENT_TIMESTAMP - INTERVAL '12 hours', CURRENT_TIMESTAMP - INTERVAL '12 hours')
ON CONFLICT (order_number) DO UPDATE
SET
  customer_name = EXCLUDED.customer_name,
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at;

-- 受注明細を再作成
DELETE FROM sales_order_items soi
USING sales_orders so
WHERE soi.order_id = so.id
  AND so.order_number IN ('SO-BB-2026-0001', 'SO-BB-2026-0002', 'SO-BB-2026-0003', 'SO-BB-2026-0004');

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 2, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-BB-2026-0001' AND p.sku = 'BB-BAT-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 3, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-BB-2026-0001' AND p.sku = 'BB-BALL-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 2, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-BB-2026-0001' AND p.sku = 'BB-HELMET-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 4, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-BB-2026-0002' AND p.sku = 'BB-GLOVE-002';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 6, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-BB-2026-0002' AND p.sku = 'BB-CLEAT-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 1, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-BB-2026-0003' AND p.sku = 'BB-PROTECT-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 2, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-BB-2026-0003' AND p.sku = 'BB-BAG-001';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 5, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-BB-2026-0004' AND p.sku = 'BB-BALL-002';

INSERT INTO sales_order_items (order_id, product_id, quantity, unit_price)
SELECT so.id, p.id, 3, p.unit_price
FROM sales_orders so, products p
WHERE so.order_number = 'SO-BB-2026-0004' AND p.sku = 'BB-UNIFORM-001';

-- 仕入発注ヘッダ
INSERT INTO purchase_orders (order_number, supplier_id, supplier_name, note, status, created_at, updated_at, received_at)
SELECT
  'PO-BB-2026-0001', s.id, s.name, '春季大会向けの追加発注', 'ORDERED',
  CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL
FROM suppliers s
WHERE s.code = 'SUP-BB-01'
ON CONFLICT (order_number) DO UPDATE
SET
  supplier_id = EXCLUDED.supplier_id,
  supplier_name = EXCLUDED.supplier_name,
  note = EXCLUDED.note,
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at,
  received_at = EXCLUDED.received_at;

INSERT INTO purchase_orders (order_number, supplier_id, supplier_name, note, status, created_at, updated_at, received_at)
SELECT
  'PO-BB-2026-0002', s.id, s.name, '消耗品の補充発注（分納中）', 'PARTIALLY_RECEIVED',
  CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL
FROM suppliers s
WHERE s.code = 'SUP-BB-02'
ON CONFLICT (order_number) DO UPDATE
SET
  supplier_id = EXCLUDED.supplier_id,
  supplier_name = EXCLUDED.supplier_name,
  note = EXCLUDED.note,
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at,
  received_at = EXCLUDED.received_at;

INSERT INTO purchase_orders (order_number, supplier_id, supplier_name, note, status, created_at, updated_at, received_at)
SELECT
  'PO-BB-2026-0003', s.id, s.name, '防具・バッグの定期補充（完了）', 'RECEIVED',
  CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM suppliers s
WHERE s.code = 'SUP-BB-03'
ON CONFLICT (order_number) DO UPDATE
SET
  supplier_id = EXCLUDED.supplier_id,
  supplier_name = EXCLUDED.supplier_name,
  note = EXCLUDED.note,
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at,
  received_at = EXCLUDED.received_at;

-- 既存のサンプル入荷履歴を消して再作成
DELETE FROM purchase_order_receipt_items pri
USING purchase_order_receipts pr, purchase_orders po
WHERE pri.receipt_id = pr.id
  AND pr.purchase_order_id = po.id
  AND po.order_number IN ('PO-BB-2026-0001', 'PO-BB-2026-0002', 'PO-BB-2026-0003');

DELETE FROM purchase_order_receipts pr
USING purchase_orders po
WHERE pr.purchase_order_id = po.id
  AND po.order_number IN ('PO-BB-2026-0001', 'PO-BB-2026-0002', 'PO-BB-2026-0003');

DELETE FROM purchase_order_items poi
USING purchase_orders po
WHERE poi.purchase_order_id = po.id
  AND po.order_number IN ('PO-BB-2026-0001', 'PO-BB-2026-0002', 'PO-BB-2026-0003');

-- 仕入発注明細
INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 20, 0, 31500
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-BB-2026-0001' AND p.sku = 'BB-BAT-002';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 15, 0, 21000
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-BB-2026-0001' AND p.sku = 'BB-GLOVE-001';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 24, 10, 6500
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-BB-2026-0002' AND p.sku = 'BB-HELMET-001';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 30, 12, 5200
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-BB-2026-0002' AND p.sku = 'BB-BALL-001';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 8, 8, 25500
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-BB-2026-0003' AND p.sku = 'BB-PROTECT-001';

INSERT INTO purchase_order_items (purchase_order_id, product_id, quantity, received_quantity, unit_cost)
SELECT po.id, p.id, 12, 12, 10200
FROM purchase_orders po, products p
WHERE po.order_number = 'PO-BB-2026-0003' AND p.sku = 'BB-BAG-001';

-- 入荷履歴（分納中）
WITH receipt AS (
  INSERT INTO purchase_order_receipts (purchase_order_id, received_by, received_at)
  SELECT po.id, 'operator', CURRENT_TIMESTAMP - INTERVAL '1 day'
  FROM purchase_orders po
  WHERE po.order_number = 'PO-BB-2026-0002'
  RETURNING id
)
INSERT INTO purchase_order_receipt_items (receipt_id, product_id, quantity)
SELECT r.id, p.id, v.qty
FROM receipt r
JOIN (VALUES ('BB-HELMET-001', 10), ('BB-BALL-001', 12)) AS v(sku, qty) ON TRUE
JOIN products p ON p.sku = v.sku;

-- 入荷履歴（入荷完了: 1回目）
WITH receipt AS (
  INSERT INTO purchase_order_receipts (purchase_order_id, received_by, received_at)
  SELECT po.id, 'operator', CURRENT_TIMESTAMP - INTERVAL '3 days'
  FROM purchase_orders po
  WHERE po.order_number = 'PO-BB-2026-0003'
  RETURNING id
)
INSERT INTO purchase_order_receipt_items (receipt_id, product_id, quantity)
SELECT r.id, p.id, v.qty
FROM receipt r
JOIN (VALUES ('BB-PROTECT-001', 5), ('BB-BAG-001', 7)) AS v(sku, qty) ON TRUE
JOIN products p ON p.sku = v.sku;

-- 入荷履歴（入荷完了: 2回目）
WITH receipt AS (
  INSERT INTO purchase_order_receipts (purchase_order_id, received_by, received_at)
  SELECT po.id, 'admin', CURRENT_TIMESTAMP - INTERVAL '2 days'
  FROM purchase_orders po
  WHERE po.order_number = 'PO-BB-2026-0003'
  RETURNING id
)
INSERT INTO purchase_order_receipt_items (receipt_id, product_id, quantity)
SELECT r.id, p.id, v.qty
FROM receipt r
JOIN (VALUES ('BB-PROTECT-001', 3), ('BB-BAG-001', 5)) AS v(sku, qty) ON TRUE
JOIN products p ON p.sku = v.sku;

-- 監査ログ（サンプル）
DELETE FROM audit_logs
WHERE detail LIKE '[SAMPLE-BASEBALL]%';

INSERT INTO audit_logs (actor_username, actor_role, action, target_type, target_id, detail, created_at)
VALUES
  ('admin', 'ADMIN', 'PRODUCT_CREATE', 'PRODUCT', 'BB-BAT-001', '[SAMPLE-BASEBALL] 商品マスタを初期投入', CURRENT_TIMESTAMP - INTERVAL '2 days'),
  ('operator', 'OPERATOR', 'ORDER_CREATE', 'SALES_ORDER', 'SO-BB-2026-0001', '[SAMPLE-BASEBALL] 受注サンプルを登録', CURRENT_TIMESTAMP - INTERVAL '1 day'),
  ('operator', 'OPERATOR', 'PURCHASE_ORDER_RECEIVE', 'PURCHASE_ORDER', 'PO-BB-2026-0002', '[SAMPLE-BASEBALL] 分納入荷を登録', CURRENT_TIMESTAMP - INTERVAL '1 day');

COMMIT;
