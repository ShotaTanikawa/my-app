export type UserRole = "ADMIN" | "OPERATOR" | "VIEWER";

export type MeResponse = {
  username: string;
  role: UserRole;
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

export type ApiErrorPayload = {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
};
