import type { PurchaseOrder, SalesOrder, UserRole } from "@/types/api";

// 金額表示は常に日本円・整数表記で統一する。
const currencyFormatter = new Intl.NumberFormat("ja-JP", {
  style: "currency",
  currency: "JPY",
  maximumFractionDigits: 0,
});

// 日時表示は画面全体で同一フォーマットを使う。
const dateTimeFormatter = new Intl.DateTimeFormat("ja-JP", {
  dateStyle: "medium",
  timeStyle: "short",
});

export function formatCurrency(value: number): string {
  return currencyFormatter.format(value);
}

export function formatDateTime(value: string): string {
  return dateTimeFormatter.format(new Date(value));
}

const salesOrderStatusLabels: Record<SalesOrder["status"], string> = {
  RESERVED: "引当済",
  CONFIRMED: "確定",
  CANCELLED: "キャンセル",
};

const purchaseOrderStatusLabels: Record<PurchaseOrder["status"], string> = {
  ORDERED: "発注済",
  PARTIALLY_RECEIVED: "一部入荷",
  RECEIVED: "入荷完了",
  CANCELLED: "キャンセル",
};

const userRoleLabels: Record<UserRole, string> = {
  ADMIN: "管理者",
  OPERATOR: "担当者",
  VIEWER: "閲覧者",
};

export function formatSalesOrderStatus(status: SalesOrder["status"]): string {
  return salesOrderStatusLabels[status];
}

export function formatPurchaseOrderStatus(status: PurchaseOrder["status"]): string {
  return purchaseOrderStatusLabels[status];
}

export function formatUserRole(role: UserRole): string {
  return userRoleLabels[role];
}
