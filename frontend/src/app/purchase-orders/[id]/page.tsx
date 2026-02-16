"use client";

import { useAuth } from "@/components/auth-provider";
import {
  cancelPurchaseOrder,
  exportPurchaseOrderReceiptsCsv,
  getPurchaseOrder,
  getPurchaseOrderReceipts,
  receivePurchaseOrder,
} from "@/lib/api";
import { formatCurrency, formatDateTime, formatPurchaseOrderStatus } from "@/lib/format";
import type { PurchaseOrder, PurchaseOrderReceipt } from "@/types/api";
import { useParams, useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";

type ReceiptFilterState = {
  receivedBy: string;
  fromDate: string;
  toDate: string;
};

function toReceiptQuery(filters: ReceiptFilterState): {
  receivedBy?: string;
  from?: string;
  to?: string;
  limit: number;
} {
  const query: { receivedBy?: string; from?: string; to?: string; limit: number } = {
    limit: 500,
  };

  const receivedBy = filters.receivedBy.trim();
  if (receivedBy) {
    query.receivedBy = receivedBy;
  }

  if (filters.fromDate) {
    const from = new Date(`${filters.fromDate}T00:00:00`);
    if (!Number.isNaN(from.getTime())) {
      query.from = from.toISOString();
    }
  }

  if (filters.toDate) {
    const to = new Date(`${filters.toDate}T23:59:59`);
    if (!Number.isNaN(to.getTime())) {
      query.to = to.toISOString();
    }
  }

  return query;
}

export default function PurchaseOrderDetailPage() {
  const { state } = useAuth();
  const params = useParams<{ id: string }>();
  const router = useRouter();

  const [purchaseOrder, setPurchaseOrder] = useState<PurchaseOrder | null>(null);
  const [error, setError] = useState("");
  const [isUpdating, setIsUpdating] = useState(false);
  const [isExportingCsv, setIsExportingCsv] = useState(false);
  const [isLoadingReceipts, setIsLoadingReceipts] = useState(false);
  const [receiveInputs, setReceiveInputs] = useState<Record<number, string>>({});
  const [receipts, setReceipts] = useState<PurchaseOrderReceipt[]>([]);
  const [receiptReceivedBy, setReceiptReceivedBy] = useState("");
  const [receiptFromDate, setReceiptFromDate] = useState("");
  const [receiptToDate, setReceiptToDate] = useState("");
  const [appliedReceiptFilters, setAppliedReceiptFilters] = useState<ReceiptFilterState>({
    receivedBy: "",
    fromDate: "",
    toDate: "",
  });
  const [reloadKey, setReloadKey] = useState(0);

  const purchaseOrderId = Number(params.id);
  const credentials = state?.credentials;
  const role = state?.user.role;
  const canOperate = role === "ADMIN" || role === "OPERATOR";
  const canReceive =
    canOperate &&
    (purchaseOrder?.status === "ORDERED" || purchaseOrder?.status === "PARTIALLY_RECEIVED");

  const totalAmount = useMemo(() => {
    if (!purchaseOrder) {
      return 0;
    }
    // 明細の数量×仕入単価を合算して総発注額を算出する。
    return purchaseOrder.items.reduce((sum, item) => sum + item.unitCost * item.quantity, 0);
  }, [purchaseOrder]);

  useEffect(() => {
    const currentCredentials = credentials;
    if (!currentCredentials || Number.isNaN(purchaseOrderId)) {
      return;
    }

    let mounted = true;
    async function load() {
      setError("");
      setIsLoadingReceipts(true);
      try {
        const receiptQuery = toReceiptQuery(appliedReceiptFilters);
        const [data, receiptData] = await Promise.all([
          getPurchaseOrder(currentCredentials!, purchaseOrderId),
          getPurchaseOrderReceipts(currentCredentials!, purchaseOrderId, receiptQuery),
        ]);
        if (mounted) {
          setPurchaseOrder(data);
          setReceipts(receiptData);
          setReceiveInputs(toDefaultReceiveInputs(data));
        }
      } catch (err) {
        if (!mounted) {
          return;
        }
        setError(err instanceof Error ? err.message : "仕入発注の取得に失敗しました。");
      } finally {
        if (mounted) {
          setIsLoadingReceipts(false);
        }
      }
    }

    void load();
    return () => {
      mounted = false;
    };
  }, [
    credentials,
    purchaseOrderId,
    appliedReceiptFilters,
    reloadKey,
  ]);

  if (!state || !credentials) {
    return null;
  }

  if (Number.isNaN(purchaseOrderId)) {
    return (
      <section className="card">
        <h2>発注IDが不正です。</h2>
      </section>
    );
  }

  async function handleReceive() {
    if (!purchaseOrder || !canReceive) {
      return;
    }

    const receiveItems = purchaseOrder.items
      .map((item) => ({
        productId: item.productId,
        quantity: Number(receiveInputs[item.productId] ?? "0"),
        remainingQuantity: item.remainingQuantity,
      }))
      .filter((item) => Number.isFinite(item.quantity) && item.quantity > 0);

    if (receiveItems.length === 0) {
      setError("今回入荷数を1件以上入力してください。");
      return;
    }

    const invalidItem = receiveItems.find((item) => item.quantity > item.remainingQuantity);
    if (invalidItem) {
      setError("今回入荷数が未入荷数を超えています。");
      return;
    }

    setError("");
    setIsUpdating(true);
    try {
      const updated = await receivePurchaseOrder(credentials!, purchaseOrder.id, {
        items: receiveItems.map((item) => ({
          productId: item.productId,
          quantity: item.quantity,
        })),
      });
      setPurchaseOrder(updated);
      setReceiveInputs(toDefaultReceiveInputs(updated));
      setReloadKey((prev) => prev + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "入荷処理に失敗しました。");
    } finally {
      setIsUpdating(false);
    }
  }

  async function handleCancel() {
    if (!purchaseOrder || !canOperate) {
      return;
    }

    setError("");
    setIsUpdating(true);
    try {
      const updated = await cancelPurchaseOrder(credentials!, purchaseOrder.id);
      setPurchaseOrder(updated);
      setReceiveInputs(toDefaultReceiveInputs(updated));
      setReloadKey((prev) => prev + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "発注キャンセルに失敗しました。");
    } finally {
      setIsUpdating(false);
    }
  }

  function fillAllRemaining() {
    if (!purchaseOrder) {
      return;
    }
    setReceiveInputs(toDefaultReceiveInputs(purchaseOrder));
  }

  function toDefaultReceiveInputs(order: PurchaseOrder): Record<number, string> {
    const defaults: Record<number, string> = {};
    for (const item of order.items) {
      if (item.remainingQuantity > 0) {
        defaults[item.productId] = String(item.remainingQuantity);
      }
    }
    return defaults;
  }

  async function handleExportReceiptsCsv() {
    if (!purchaseOrder) {
      return;
    }

    setError("");
    setIsExportingCsv(true);
    try {
      const receiptQuery = toReceiptQuery(appliedReceiptFilters);
      const blob = await exportPurchaseOrderReceiptsCsv(credentials!, purchaseOrder.id, {
        ...receiptQuery,
        limit: 2000,
      });
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
      anchor.href = url;
      anchor.download = `purchase-order-${purchaseOrder.orderNumber}-receipts-${timestamp}.csv`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "入荷履歴CSVの出力に失敗しました。");
    } finally {
      setIsExportingCsv(false);
    }
  }

  function handleReceiptSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAppliedReceiptFilters({
      receivedBy: receiptReceivedBy,
      fromDate: receiptFromDate,
      toDate: receiptToDate,
    });
  }

  function handleReceiptFilterClear() {
    setReceiptReceivedBy("");
    setReceiptFromDate("");
    setReceiptToDate("");
    setAppliedReceiptFilters({
      receivedBy: "",
      fromDate: "",
      toDate: "",
    });
  }

  return (
    <div className="page">
      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>仕入発注詳細</h2>
          <button className="button secondary" type="button" onClick={() => router.push("/purchase-orders")}>
            一覧へ戻る
          </button>
        </div>
        <p style={{ margin: "0 0 12px", color: "#607086" }}>
          未入荷数を確認し、今回入荷を入力して登録します。入荷履歴は条件検索とCSV出力に対応しています。
        </p>

        {error && <p className="inline-error">{error}</p>}
        {!purchaseOrder && !error && <p>読み込み中...</p>}

        {purchaseOrder && (
          <div className="page" style={{ gap: 14 }}>
            <div className="grid cols-2">
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">発注番号</div>
                <div style={{ marginTop: 6 }}>{purchaseOrder.orderNumber}</div>
              </div>
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">仕入先</div>
                <div style={{ marginTop: 6 }}>
                  {purchaseOrder.supplierCode
                    ? `${purchaseOrder.supplierCode} / ${purchaseOrder.supplierName}`
                    : purchaseOrder.supplierName}
                </div>
              </div>
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">ステータス</div>
                <div style={{ marginTop: 6 }}>
                  <span className={`badge ${purchaseOrder.status}`}>
                    {formatPurchaseOrderStatus(purchaseOrder.status)}
                  </span>
                </div>
              </div>
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">作成日時</div>
                <div style={{ marginTop: 6 }}>{formatDateTime(purchaseOrder.createdAt)}</div>
              </div>
            </div>

            {purchaseOrder.note && (
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">備考</div>
                <div style={{ marginTop: 6 }}>{purchaseOrder.note}</div>
              </div>
            )}

            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>SKU</th>
                    <th>商品名</th>
                    <th>発注数</th>
                    <th>入荷済</th>
                    <th>未入荷</th>
                    {canReceive && <th>今回入荷</th>}
                    <th>仕入単価</th>
                    <th>小計</th>
                  </tr>
                </thead>
                <tbody>
                  {purchaseOrder.items.map((item) => (
                    <tr key={`${item.productId}-${item.sku}`}>
                      <td>{item.sku}</td>
                      <td>{item.productName}</td>
                      <td>{item.quantity}</td>
                      <td>{item.receivedQuantity}</td>
                      <td>{item.remainingQuantity}</td>
                      {canReceive && (
                        <td>
                          {item.remainingQuantity > 0 ? (
                            <input
                              className="input"
                              type="number"
                              min={0}
                              max={item.remainingQuantity}
                              step={1}
                              value={receiveInputs[item.productId] ?? "0"}
                              onChange={(event) =>
                                setReceiveInputs((prev) => ({
                                  ...prev,
                                  [item.productId]: event.target.value,
                                }))
                              }
                              style={{ width: 110 }}
                            />
                          ) : (
                            "-"
                          )}
                        </td>
                      )}
                      <td>{formatCurrency(item.unitCost)}</td>
                      <td>{formatCurrency(item.unitCost * item.quantity)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="card" style={{ boxShadow: "none" }}>
              <div className="stat-label">総発注額</div>
              <div className="stat-value">{formatCurrency(totalAmount)}</div>
            </div>

            <div className="grid cols-3">
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">総数量</div>
                <div className="stat-value">{purchaseOrder.totalQuantity}</div>
              </div>
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">総入荷済</div>
                <div className="stat-value">{purchaseOrder.totalReceivedQuantity}</div>
              </div>
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">総未入荷</div>
                <div className="stat-value">{purchaseOrder.totalRemainingQuantity}</div>
              </div>
            </div>

            {purchaseOrder.receivedAt && (
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">入荷日時</div>
                <div style={{ marginTop: 6 }}>{formatDateTime(purchaseOrder.receivedAt)}</div>
              </div>
            )}

            <section className="card" style={{ boxShadow: "none" }}>
              <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
                <h3>入荷履歴</h3>
                <div className="button-row">
                  <span style={{ color: "#607086", fontSize: 13 }}>{receipts.length}件</span>
                  <button
                    className="button secondary"
                    type="button"
                    onClick={handleExportReceiptsCsv}
                    disabled={isExportingCsv || isLoadingReceipts}
                  >
                    {isExportingCsv ? "出力中..." : "CSV出力"}
                  </button>
                </div>
              </div>

              <form className="form-grid" onSubmit={handleReceiptSearch}>
                <div className="field">
                  <label htmlFor="receipt-actor">登録者</label>
                  <input
                    id="receipt-actor"
                    className="input"
                    value={receiptReceivedBy}
                    onChange={(event) => setReceiptReceivedBy(event.target.value)}
                    placeholder="部分一致"
                  />
                </div>
                <div className="field">
                  <label htmlFor="receipt-from">開始日</label>
                  <input
                    id="receipt-from"
                    className="input"
                    type="date"
                    value={receiptFromDate}
                    onChange={(event) => setReceiptFromDate(event.target.value)}
                  />
                </div>
                <div className="field">
                  <label htmlFor="receipt-to">終了日</label>
                  <input
                    id="receipt-to"
                    className="input"
                    type="date"
                    value={receiptToDate}
                    onChange={(event) => setReceiptToDate(event.target.value)}
                  />
                </div>
                <div className="button-row" style={{ alignItems: "end" }}>
                  <button className="button primary" type="submit" disabled={isLoadingReceipts}>
                    検索
                  </button>
                  <button
                    className="button secondary"
                    type="button"
                    onClick={handleReceiptFilterClear}
                    disabled={isLoadingReceipts}
                  >
                    クリア
                  </button>
                </div>
              </form>

              {isLoadingReceipts && <p style={{ margin: 0, color: "#607086" }}>入荷履歴を読み込み中...</p>}
              {!isLoadingReceipts && receipts.length === 0 && (
                <p style={{ margin: 0, color: "#607086" }}>条件に一致する入荷履歴はありません。</p>
              )}

              <div className="page" style={{ gap: 10 }}>
                {!isLoadingReceipts &&
                  receipts.map((receipt) => (
                  <div key={receipt.id} className="card" style={{ boxShadow: "none", borderStyle: "dashed" }}>
                    <div className="grid cols-3" style={{ marginBottom: 8 }}>
                      <div>
                        <div className="stat-label">入荷日時</div>
                        <div style={{ marginTop: 4 }}>{formatDateTime(receipt.receivedAt)}</div>
                      </div>
                      <div>
                        <div className="stat-label">登録者</div>
                        <div style={{ marginTop: 4 }}>{receipt.receivedBy}</div>
                      </div>
                      <div>
                        <div className="stat-label">入荷数量合計</div>
                        <div style={{ marginTop: 4 }}>{receipt.totalQuantity}</div>
                      </div>
                    </div>

                    <div className="table-wrap">
                      <table className="table">
                        <thead>
                          <tr>
                            <th>SKU</th>
                            <th>商品名</th>
                            <th>入荷数</th>
                          </tr>
                        </thead>
                        <tbody>
                          {receipt.items.map((item) => (
                            <tr key={`${receipt.id}-${item.productId}`}>
                              <td>{item.sku}</td>
                              <td>{item.productName}</td>
                              <td>{item.quantity}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ))}
              </div>
            </section>

            {canOperate &&
              (purchaseOrder.status === "ORDERED" ||
                purchaseOrder.status === "PARTIALLY_RECEIVED") && (
              <div className="button-row">
                <button className="button secondary" type="button" onClick={fillAllRemaining} disabled={isUpdating}>
                  残数をセット
                </button>
                <button className="button primary" type="button" onClick={handleReceive} disabled={isUpdating}>
                  {isUpdating ? "処理中..." : "入荷登録"}
                </button>
                <button className="button danger" type="button" onClick={handleCancel} disabled={isUpdating}>
                  {isUpdating ? "処理中..." : "発注キャンセル"}
                </button>
              </div>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
