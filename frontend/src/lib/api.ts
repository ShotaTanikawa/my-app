import type {
  AuditLogCleanupResponse,
  ApiErrorPayload,
  AuthSession,
  AuditLogPageResponse,
  AuditLogQuery,
  LoginResponse,
  MfaSetupResponse,
  MeResponse,
  PasswordResetRequestResponse,
  Product,
  ProductCategory,
  ProductImportResult,
  ProductPageResponse,
  ProductQuery,
  ProductSkuSuggestion,
  ProductSupplierContract,
  PurchaseOrder,
  PurchaseOrderReceipt,
  PurchaseOrderReceiptQuery,
  ReplenishmentSuggestion,
  SalesOrder,
  SalesQuery,
  SalesReport,
  Supplier,
} from "@/types/api";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export class ApiClientError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
  }
}

function toBearerAuthHeader(accessToken: string): string {
  return `Bearer ${accessToken}`;
}

type Credentials = {
  accessToken: string;
};

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  credentials?: Credentials;
  idempotencyKey?: string;
  disableIdempotency?: boolean;
};

function generateIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function request<T>(path: string, options?: RequestOptions): Promise<T> {
  const method = options?.method ?? "GET";
  const headers = new Headers();
  headers.set("Content-Type", "application/json");

  if (options?.credentials) {
    headers.set("Authorization", toBearerAuthHeader(options.credentials.accessToken));
  }

  if (method !== "GET" && !options?.disableIdempotency) {
    headers.set("Idempotency-Key", options?.idempotencyKey ?? generateIdempotencyKey());
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: options?.body ? JSON.stringify(options.body) : undefined,
    // ダッシュボードや入力フォームで古い値を避けるため、常に最新レスポンスを取得する。
    cache: "no-store",
  });

  if (!response.ok) {
    let message = "API request failed";

    try {
      const payload = (await response.json()) as Partial<ApiErrorPayload>;
      message = payload.message ?? message;
    } catch {
      // エラーボディがJSONでない場合はHTTPステータステキストへフォールバックする。
      message = response.statusText || message;
    }

    throw new ApiClientError(message, response.status);
  }

  if (response.status === 204) {
    return null as T;
  }

  return (await response.json()) as T;
}

export async function login(username: string, password: string, mfaCode?: string): Promise<LoginResponse> {
  return request<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: { username, password, mfaCode: mfaCode?.trim() || undefined },
    // ログイン失敗時の再送を避けるため明示的に冪等キーを無効化する。
    disableIdempotency: true,
  });
}

export async function refresh(refreshToken: string): Promise<LoginResponse> {
  return request<LoginResponse>("/api/auth/refresh", {
    method: "POST",
    body: { refreshToken },
  });
}

export async function logout(refreshToken: string): Promise<void> {
  return request<void>("/api/auth/logout", {
    method: "POST",
    body: { refreshToken },
  });
}

export async function getSessions(credentials: Credentials): Promise<AuthSession[]> {
  return request<AuthSession[]>("/api/auth/sessions", { credentials });
}

export async function revokeSession(credentials: Credentials, sessionId: string): Promise<void> {
  return request<void>(`/api/auth/sessions/${sessionId}`, {
    method: "DELETE",
    credentials,
  });
}

export async function getMe(credentials: Credentials): Promise<MeResponse> {
  return request<MeResponse>("/api/auth/me", { credentials });
}

export async function requestPasswordReset(username: string): Promise<PasswordResetRequestResponse> {
  return request<PasswordResetRequestResponse>("/api/auth/password-reset/request", {
    method: "POST",
    body: { username },
    disableIdempotency: true,
  });
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  return request<void>("/api/auth/password-reset/confirm", {
    method: "POST",
    body: { token, newPassword },
  });
}

export async function setupMfa(credentials: Credentials): Promise<MfaSetupResponse> {
  return request<MfaSetupResponse>("/api/auth/mfa/setup", {
    method: "POST",
    credentials,
  });
}

export async function enableMfa(credentials: Credentials, code: string): Promise<MeResponse> {
  return request<MeResponse>("/api/auth/mfa/enable", {
    method: "POST",
    credentials,
    body: { code },
    disableIdempotency: true,
  });
}

export async function disableMfa(credentials: Credentials, code: string): Promise<MeResponse> {
  return request<MeResponse>("/api/auth/mfa/disable", {
    method: "POST",
    credentials,
    body: { code },
    disableIdempotency: true,
  });
}

export async function getProducts(credentials: Credentials): Promise<Product[]> {
  return request<Product[]>("/api/products", { credentials });
}

export async function getProductsPage(
  credentials: Credentials,
  query: ProductQuery = {},
): Promise<ProductPageResponse> {
  const searchParams = new URLSearchParams();
  searchParams.set("page", String(query.page ?? 0));
  searchParams.set("size", String(query.size ?? 20));

  if (query.q) {
    searchParams.set("q", query.q);
  }
  if (query.categoryId !== undefined) {
    searchParams.set("categoryId", String(query.categoryId));
  }
  if (query.lowStockOnly !== undefined) {
    searchParams.set("lowStockOnly", String(query.lowStockOnly));
  }

  return request<ProductPageResponse>(`/api/products/page?${searchParams.toString()}`, { credentials });
}

export async function getProductCategories(credentials: Credentials): Promise<ProductCategory[]> {
  return request<ProductCategory[]>("/api/product-categories", { credentials });
}

export async function createProductCategory(
  credentials: Credentials,
  body: {
    code: string;
    name: string;
    parentId?: number;
    active?: boolean;
    sortOrder?: number;
    skuPrefix?: string;
    skuSequenceDigits?: number;
  },
): Promise<ProductCategory> {
  return request<ProductCategory>("/api/product-categories", { method: "POST", credentials, body });
}

export async function updateProductCategorySkuRule(
  credentials: Credentials,
  categoryId: number,
  body: {
    skuPrefix?: string;
    skuSequenceDigits?: number;
  },
): Promise<ProductCategory> {
  return request<ProductCategory>(`/api/product-categories/${categoryId}/sku-rule`, {
    method: "PUT",
    credentials,
    body,
  });
}

export async function importProductsCsv(
  credentials: Credentials,
  file: File,
): Promise<ProductImportResult> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${API_BASE_URL}/api/products/import`, {
    method: "POST",
    headers: {
      Authorization: toBearerAuthHeader(credentials.accessToken),
    },
    body: formData,
    cache: "no-store",
  });

  if (!response.ok) {
    let message = "CSV import failed";

    try {
      const payload = (await response.json()) as Partial<ApiErrorPayload>;
      message = payload.message ?? message;
    } catch {
      message = response.statusText || message;
    }

    throw new ApiClientError(message, response.status);
  }

  return (await response.json()) as ProductImportResult;
}

export async function getNextProductSku(
  credentials: Credentials,
  categoryId?: number,
): Promise<ProductSkuSuggestion> {
  const searchParams = new URLSearchParams();
  if (categoryId !== undefined) {
    searchParams.set("categoryId", String(categoryId));
  }
  const query = searchParams.toString();
  const path = query ? `/api/products/sku/next?${query}` : "/api/products/sku/next";
  return request<ProductSkuSuggestion>(path, { credentials });
}

export async function createProduct(
  credentials: Credentials,
  body: {
    sku: string;
    name: string;
    description?: string;
    unitPrice: number;
    reorderPoint: number;
    reorderQuantity: number;
    categoryId?: number;
  },
): Promise<Product> {
  return request<Product>("/api/products", { method: "POST", credentials, body });
}

export async function updateProduct(
  credentials: Credentials,
  productId: number,
  body: {
    name: string;
    description?: string;
    unitPrice: number;
    reorderPoint?: number;
    reorderQuantity?: number;
    categoryId?: number;
  },
): Promise<Product> {
  return request<Product>(`/api/products/${productId}`, { method: "PUT", credentials, body });
}

export async function addStock(
  credentials: Credentials,
  productId: number,
  quantity: number,
): Promise<Product> {
  return request<Product>(`/api/products/${productId}/stock`, {
    method: "POST",
    credentials,
    body: { quantity },
  });
}

export async function getOrders(credentials: Credentials): Promise<SalesOrder[]> {
  return request<SalesOrder[]>("/api/orders", { credentials });
}

export async function getOrder(credentials: Credentials, orderId: number): Promise<SalesOrder> {
  return request<SalesOrder>(`/api/orders/${orderId}`, { credentials });
}

export async function createOrder(
  credentials: Credentials,
  body: {
    customerName: string;
    items: Array<{ productId: number; quantity: number }>;
  },
): Promise<SalesOrder> {
  return request<SalesOrder>("/api/orders", { method: "POST", credentials, body });
}

export async function confirmOrder(credentials: Credentials, orderId: number): Promise<SalesOrder> {
  return request<SalesOrder>(`/api/orders/${orderId}/confirm`, {
    method: "POST",
    credentials,
  });
}

export async function cancelOrder(credentials: Credentials, orderId: number): Promise<SalesOrder> {
  return request<SalesOrder>(`/api/orders/${orderId}/cancel`, {
    method: "POST",
    credentials,
  });
}

export async function getSalesReport(
  credentials: Credentials,
  query: SalesQuery = {},
): Promise<SalesReport> {
  const searchParams = new URLSearchParams();
  searchParams.set("groupBy", query.groupBy ?? "DAY");
  searchParams.set("lineLimit", String(query.lineLimit ?? 200));

  if (query.from) {
    searchParams.set("from", query.from);
  }
  if (query.to) {
    searchParams.set("to", query.to);
  }

  return request<SalesReport>(`/api/sales?${searchParams.toString()}`, { credentials });
}

export async function exportSalesCsv(
  credentials: Credentials,
  query: SalesQuery = {},
): Promise<Blob> {
  const searchParams = new URLSearchParams();
  searchParams.set("limit", String(query.limit ?? 2000));

  if (query.from) {
    searchParams.set("from", query.from);
  }
  if (query.to) {
    searchParams.set("to", query.to);
  }

  const response = await fetch(`${API_BASE_URL}/api/sales/export.csv?${searchParams.toString()}`, {
    method: "GET",
    headers: {
      Authorization: toBearerAuthHeader(credentials.accessToken),
    },
    cache: "no-store",
  });

  if (!response.ok) {
    let message = "CSV export failed";

    try {
      const payload = (await response.json()) as Partial<ApiErrorPayload>;
      message = payload.message ?? message;
    } catch {
      message = response.statusText || message;
    }

    throw new ApiClientError(message, response.status);
  }

  return response.blob();
}

export async function getPurchaseOrders(credentials: Credentials): Promise<PurchaseOrder[]> {
  return request<PurchaseOrder[]>("/api/purchase-orders", { credentials });
}

export async function getPurchaseOrder(credentials: Credentials, purchaseOrderId: number): Promise<PurchaseOrder> {
  return request<PurchaseOrder>(`/api/purchase-orders/${purchaseOrderId}`, { credentials });
}

export async function getPurchaseOrderReceipts(
  credentials: Credentials,
  purchaseOrderId: number,
  query: PurchaseOrderReceiptQuery = {},
): Promise<PurchaseOrderReceipt[]> {
  const searchParams = new URLSearchParams();
  searchParams.set("limit", String(query.limit ?? 200));

  if (query.receivedBy) {
    searchParams.set("receivedBy", query.receivedBy);
  }
  if (query.from) {
    searchParams.set("from", query.from);
  }
  if (query.to) {
    searchParams.set("to", query.to);
  }

  return request<PurchaseOrderReceipt[]>(
    `/api/purchase-orders/${purchaseOrderId}/receipts?${searchParams.toString()}`,
    { credentials },
  );
}

export async function getReplenishmentSuggestions(
  credentials: Credentials,
): Promise<ReplenishmentSuggestion[]> {
  return request<ReplenishmentSuggestion[]>("/api/purchase-orders/suggestions", { credentials });
}

export async function createPurchaseOrder(
  credentials: Credentials,
  body: {
    supplierId?: number;
    supplierName?: string;
    note?: string;
    items: Array<{ productId: number; quantity: number; unitCost: number }>;
  },
): Promise<PurchaseOrder> {
  return request<PurchaseOrder>("/api/purchase-orders", {
    method: "POST",
    credentials,
    body,
  });
}

export async function receivePurchaseOrder(
  credentials: Credentials,
  purchaseOrderId: number,
  body?: {
    items: Array<{ productId: number; quantity: number }>;
  },
): Promise<PurchaseOrder> {
  return request<PurchaseOrder>(`/api/purchase-orders/${purchaseOrderId}/receive`, {
    method: "POST",
    credentials,
    body,
  });
}

export async function cancelPurchaseOrder(
  credentials: Credentials,
  purchaseOrderId: number,
): Promise<PurchaseOrder> {
  return request<PurchaseOrder>(`/api/purchase-orders/${purchaseOrderId}/cancel`, {
    method: "POST",
    credentials,
  });
}

export async function exportPurchaseOrderReceiptsCsv(
  credentials: Credentials,
  purchaseOrderId: number,
  query: PurchaseOrderReceiptQuery = {},
): Promise<Blob> {
  const searchParams = new URLSearchParams();
  searchParams.set("limit", String(query.limit ?? 2000));

  if (query.receivedBy) {
    searchParams.set("receivedBy", query.receivedBy);
  }
  if (query.from) {
    searchParams.set("from", query.from);
  }
  if (query.to) {
    searchParams.set("to", query.to);
  }

  const response = await fetch(
    `${API_BASE_URL}/api/purchase-orders/${purchaseOrderId}/receipts/export.csv?${searchParams.toString()}`,
    {
    method: "GET",
    headers: {
      Authorization: toBearerAuthHeader(credentials.accessToken),
    },
    cache: "no-store",
    },
  );

  if (!response.ok) {
    let message = "CSV export failed";

    try {
      const payload = (await response.json()) as Partial<ApiErrorPayload>;
      message = payload.message ?? message;
    } catch {
      message = response.statusText || message;
    }

    throw new ApiClientError(message, response.status);
  }

  return response.blob();
}

export async function getSuppliers(credentials: Credentials): Promise<Supplier[]> {
  return request<Supplier[]>("/api/suppliers", { credentials });
}

export async function getSupplier(credentials: Credentials, supplierId: number): Promise<Supplier> {
  return request<Supplier>(`/api/suppliers/${supplierId}`, { credentials });
}

export async function createSupplier(
  credentials: Credentials,
  body: {
    code: string;
    name: string;
    contactName?: string;
    email?: string;
    phone?: string;
    note?: string;
  },
): Promise<Supplier> {
  return request<Supplier>("/api/suppliers", { method: "POST", credentials, body });
}

export async function updateSupplier(
  credentials: Credentials,
  supplierId: number,
  body: {
    code: string;
    name: string;
    contactName?: string;
    email?: string;
    phone?: string;
    note?: string;
    active?: boolean;
  },
): Promise<Supplier> {
  return request<Supplier>(`/api/suppliers/${supplierId}`, {
    method: "PUT",
    credentials,
    body,
  });
}

export async function activateSupplier(credentials: Credentials, supplierId: number): Promise<Supplier> {
  return request<Supplier>(`/api/suppliers/${supplierId}/activate`, {
    method: "POST",
    credentials,
  });
}

export async function deactivateSupplier(credentials: Credentials, supplierId: number): Promise<Supplier> {
  return request<Supplier>(`/api/suppliers/${supplierId}/deactivate`, {
    method: "POST",
    credentials,
  });
}

export async function getProductSuppliers(
  credentials: Credentials,
  productId: number,
): Promise<ProductSupplierContract[]> {
  return request<ProductSupplierContract[]>(`/api/products/${productId}/suppliers`, { credentials });
}

export async function upsertProductSupplier(
  credentials: Credentials,
  productId: number,
  body: {
    supplierId: number;
    unitCost: number;
    leadTimeDays?: number;
    moq?: number;
    lotSize?: number;
    primary?: boolean;
  },
): Promise<ProductSupplierContract> {
  return request<ProductSupplierContract>(`/api/products/${productId}/suppliers`, {
    method: "POST",
    credentials,
    body,
  });
}

export async function removeProductSupplier(
  credentials: Credentials,
  productId: number,
  supplierId: number,
): Promise<void> {
  return request<void>(`/api/products/${productId}/suppliers/${supplierId}`, {
    method: "DELETE",
    credentials,
  });
}

export async function getAuditLogs(
  credentials: Credentials,
  query: AuditLogQuery = {},
): Promise<AuditLogPageResponse> {
  const searchParams = new URLSearchParams();
  searchParams.set("page", String(query.page ?? 0));
  searchParams.set("size", String(query.size ?? 50));

  if (query.action) {
    searchParams.set("action", query.action);
  }
  if (query.actor) {
    searchParams.set("actor", query.actor);
  }
  if (query.from) {
    searchParams.set("from", query.from);
  }
  if (query.to) {
    searchParams.set("to", query.to);
  }

  return request<AuditLogPageResponse>(`/api/audit-logs?${searchParams.toString()}`, { credentials });
}

export async function exportAuditLogsCsv(
  credentials: Credentials,
  query: AuditLogQuery = {},
): Promise<Blob> {
  const searchParams = new URLSearchParams();
  searchParams.set("limit", String(query.limit ?? 1000));

  if (query.action) {
    searchParams.set("action", query.action);
  }
  if (query.actor) {
    searchParams.set("actor", query.actor);
  }
  if (query.from) {
    searchParams.set("from", query.from);
  }
  if (query.to) {
    searchParams.set("to", query.to);
  }

  const response = await fetch(`${API_BASE_URL}/api/audit-logs/export.csv?${searchParams.toString()}`, {
    method: "GET",
    headers: {
      Authorization: toBearerAuthHeader(credentials.accessToken),
    },
    cache: "no-store",
  });

  if (!response.ok) {
    let message = "CSV export failed";

    try {
      const payload = (await response.json()) as Partial<ApiErrorPayload>;
      message = payload.message ?? message;
    } catch {
      message = response.statusText || message;
    }

    throw new ApiClientError(message, response.status);
  }

  return response.blob();
}

export async function cleanupAuditLogs(
  credentials: Credentials,
  retentionDays?: number,
): Promise<AuditLogCleanupResponse> {
  const searchParams = new URLSearchParams();
  if (retentionDays !== undefined) {
    searchParams.set("retentionDays", String(retentionDays));
  }

  const querySuffix = searchParams.toString();
  const path = querySuffix ? `/api/audit-logs/cleanup?${querySuffix}` : "/api/audit-logs/cleanup";

  return request<AuditLogCleanupResponse>(path, {
    method: "POST",
    credentials,
  });
}

export type { Credentials };
