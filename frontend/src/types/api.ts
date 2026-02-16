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

// 商品と在庫の結合ビュー。
export type Product = {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  unitPrice: number;
  availableQuantity: number;
  reservedQuantity: number;
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

// バックエンド統一エラーペイロード。
export type ApiErrorPayload = {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
};
