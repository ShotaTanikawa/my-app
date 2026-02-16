"use client";

import { useAuth } from "@/components/auth-provider";
import { cancelPurchaseOrder, getPurchaseOrder, receivePurchaseOrder } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/format";
import type { PurchaseOrder } from "@/types/api";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

export default function PurchaseOrderDetailPage() {
  const { state } = useAuth();
  const params = useParams<{ id: string }>();
  const router = useRouter();

  const [purchaseOrder, setPurchaseOrder] = useState<PurchaseOrder | null>(null);
  const [error, setError] = useState("");
  const [isUpdating, setIsUpdating] = useState(false);

  const purchaseOrderId = Number(params.id);
  const credentials = state?.credentials;
  const role = state?.user.role;
  const canOperate = role === "ADMIN" || role === "OPERATOR";

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
      try {
        const data = await getPurchaseOrder(currentCredentials!, purchaseOrderId);
        if (mounted) {
          setPurchaseOrder(data);
        }
      } catch (err) {
        if (!mounted) {
          return;
        }
        setError(err instanceof Error ? err.message : "仕入発注の取得に失敗しました。");
      }
    }

    void load();
    return () => {
      mounted = false;
    };
  }, [credentials, purchaseOrderId]);

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
    if (!purchaseOrder || !canOperate) {
      return;
    }

    setError("");
    setIsUpdating(true);
    try {
      const updated = await receivePurchaseOrder(credentials!, purchaseOrder.id);
      setPurchaseOrder(updated);
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
    } catch (err) {
      setError(err instanceof Error ? err.message : "発注キャンセルに失敗しました。");
    } finally {
      setIsUpdating(false);
    }
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
                <div style={{ marginTop: 6 }}>{purchaseOrder.supplierName}</div>
              </div>
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">ステータス</div>
                <div style={{ marginTop: 6 }}>
                  <span className={`badge ${purchaseOrder.status}`}>{purchaseOrder.status}</span>
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
                    <th>数量</th>
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

            {purchaseOrder.receivedAt && (
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">入荷日時</div>
                <div style={{ marginTop: 6 }}>{formatDateTime(purchaseOrder.receivedAt)}</div>
              </div>
            )}

            {canOperate && purchaseOrder.status === "ORDERED" && (
              <div className="button-row">
                <button className="button primary" type="button" onClick={handleReceive} disabled={isUpdating}>
                  {isUpdating ? "処理中..." : "入荷処理"}
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
