import type {
  ApiErrorPayload,
  AuditLogPageResponse,
  AuditLogQuery,
  LoginResponse,
  MeResponse,
  Product,
  SalesOrder,
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
};

async function request<T>(path: string, options?: RequestOptions): Promise<T> {
  const headers = new Headers();
  headers.set("Content-Type", "application/json");

  if (options?.credentials) {
    headers.set("Authorization", toBearerAuthHeader(options.credentials.accessToken));
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options?.method ?? "GET",
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

export async function login(username: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: { username, password },
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

export async function getMe(credentials: Credentials): Promise<MeResponse> {
  return request<MeResponse>("/api/auth/me", { credentials });
}

export async function getProducts(credentials: Credentials): Promise<Product[]> {
  return request<Product[]>("/api/products", { credentials });
}

export async function createProduct(
  credentials: Credentials,
  body: {
    sku: string;
    name: string;
    description?: string;
    unitPrice: number;
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

export type { Credentials };
