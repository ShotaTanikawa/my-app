export type UserRole = "ADMIN" | "OPERATOR" | "VIEWER";

export type MeResponse = {
  username: string;
  role: UserRole;
};

export type LoginResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  user: MeResponse;
};

export type Product = {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  unitPrice: number;
  availableQuantity: number;
  reservedQuantity: number;
};

export type SalesOrderItem = {
  productId: number;
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
};

export type SalesOrder = {
  id: number;
  orderNumber: string;
  customerName: string;
  status: "RESERVED" | "CONFIRMED" | "CANCELLED";
  createdAt: string;
  items: SalesOrderItem[];
};

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

export type ApiErrorPayload = {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
};
