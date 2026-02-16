// フロントで扱うユーザーロールの集合。
export type UserRole = "ADMIN" | "OPERATOR" | "VIEWER";

// 現在ユーザー情報。
export type MeResponse = {
  username: string;
  role: UserRole;
};

// ログイン/リフレッシュ時のトークン応答。
export type LoginResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  user: MeResponse;
};

// ログイン端末セッション情報。
export type AuthSession = {
  sessionId: string;
  userAgent: string | null;
  ipAddress: string | null;
  createdAt: string;
  lastUsedAt: string;
  expiresAt: string;
};

// 商品と在庫の結合ビュー。
export type Product = {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  unitPrice: number;
  reorderPoint: number;
  reorderQuantity: number;
  categoryId: number | null;
  categoryCode: string | null;
  categoryName: string | null;
  availableQuantity: number;
  reservedQuantity: number;
};

// 商品カテゴリ。
export type ProductCategory = {
  id: number;
  code: string;
  name: string;
  active: boolean;
  sortOrder: number;
  skuPrefix: string | null;
  skuSequenceDigits: number;
};

// 商品検索条件。
export type ProductQuery = {
  page?: number;
  size?: number;
  q?: string;
  categoryId?: number;
  lowStockOnly?: boolean;
};

// 商品一覧ページング結果。
export type ProductPageResponse = {
  items: Product[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
};

// 商品CSV一括取込の行エラー。
export type ProductImportError = {
  rowNumber: number;
  message: string;
};

// 商品CSV一括取込の実行結果。
export type ProductImportResult = {
  totalRows: number;
  successRows: number;
  createdRows: number;
  updatedRows: number;
  failedRows: number;
  errors: ProductImportError[];
};

// SKU自動採番候補。
export type ProductSkuSuggestion = {
  sku: string;
};

// 受注明細1行分。
export type SalesOrderItem = {
  productId: number;
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
};

// 受注ヘッダと明細の集約モデル。
export type SalesOrder = {
  id: number;
  orderNumber: string;
  customerName: string;
  status: "RESERVED" | "CONFIRMED" | "CANCELLED";
  createdAt: string;
  items: SalesOrderItem[];
};

// 売上集計の粒度。
export type SalesGroupBy = "DAY" | "WEEK" | "MONTH";

// 売上サマリー。
export type SalesSummary = {
  from: string | null;
  to: string | null;
  metricBasis: string;
  totalSalesAmount: number;
  orderCount: number;
  totalItemQuantity: number;
  averageOrderAmount: number;
};

// 売上推移ポイント。
export type SalesTrendPoint = {
  bucketStart: string;
  totalSalesAmount: number;
  orderCount: number;
  totalItemQuantity: number;
};

// 売上明細1行分。
export type SalesLine = {
  orderId: number;
  orderNumber: string;
  customerName: string;
  soldAt: string;
  productId: number;
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
};

// 売上レポートの検索条件。
export type SalesQuery = {
  from?: string;
  to?: string;
  groupBy?: SalesGroupBy;
  lineLimit?: number;
  limit?: number;
};

// 売上レポート。
export type SalesReport = {
  summary: SalesSummary;
  groupBy: SalesGroupBy;
  trends: SalesTrendPoint[];
  lines: SalesLine[];
  lineLimit: number;
  totalLineCount: number;
};

// 仕入発注明細1行分。
export type PurchaseOrderItem = {
  productId: number;
  sku: string;
  productName: string;
  quantity: number;
  receivedQuantity: number;
  remainingQuantity: number;
  unitCost: number;
};

// 1回の入荷イベント明細。
export type PurchaseOrderReceiptItem = {
  productId: number;
  sku: string;
  productName: string;
  quantity: number;
};

// 1回の入荷イベント。
export type PurchaseOrderReceipt = {
  id: number;
  receivedBy: string;
  receivedAt: string;
  totalQuantity: number;
  items: PurchaseOrderReceiptItem[];
};

// 入荷履歴検索条件。
export type PurchaseOrderReceiptQuery = {
  receivedBy?: string;
  from?: string;
  to?: string;
  limit?: number;
};

// 仕入発注ヘッダと明細の集約モデル。
export type PurchaseOrder = {
  id: number;
  orderNumber: string;
  supplierId: number | null;
  supplierCode: string | null;
  supplierName: string;
  note: string | null;
  status: "ORDERED" | "PARTIALLY_RECEIVED" | "RECEIVED" | "CANCELLED";
  createdAt: string;
  receivedAt: string | null;
  totalQuantity: number;
  totalReceivedQuantity: number;
  totalRemainingQuantity: number;
  items: PurchaseOrderItem[];
  receipts: PurchaseOrderReceipt[];
};

// 補充提案1件分。
export type ReplenishmentSuggestion = {
  productId: number;
  sku: string;
  productName: string;
  availableQuantity: number;
  reservedQuantity: number;
  reorderPoint: number;
  reorderQuantity: number;
  shortageQuantity: number;
  suggestedQuantity: number;
  suggestedSupplierId: number | null;
  suggestedSupplierCode: string | null;
  suggestedSupplierName: string | null;
  suggestedUnitCost: number | null;
  leadTimeDays: number | null;
  moq: number;
  lotSize: number;
};

// 仕入先マスタ。
export type Supplier = {
  id: number;
  code: string;
  name: string;
  contactName: string | null;
  email: string | null;
  phone: string | null;
  note: string | null;
  active: boolean;
};

// 商品と仕入先の契約条件。
export type ProductSupplierContract = {
  supplierId: number;
  supplierCode: string;
  supplierName: string;
  supplierActive: boolean;
  unitCost: number;
  leadTimeDays: number;
  moq: number;
  lotSize: number;
  primary: boolean;
};

// 監査ログ1件分。
export type AuditLog = {
  id: number;
  actorUsername: string;
  actorRole: string;
  action: string;
  targetType: string | null;
  targetId: string | null;
  detail: string | null;
  createdAt: string;
};

// 監査ログ検索条件。
export type AuditLogQuery = {
  page?: number;
  size?: number;
  limit?: number;
  action?: string;
  actor?: string;
  from?: string;
  to?: string;
};

// 監査ログページング結果。
export type AuditLogPageResponse = {
  items: AuditLog[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
};

// 監査ログクリーンアップ実行結果。
export type AuditLogCleanupResponse = {
  deletedCount: number;
  retentionDays: number;
  cutoff: string;
  executedAt: string;
};

// バックエンド統一エラーペイロード。
export type ApiErrorPayload = {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
};
