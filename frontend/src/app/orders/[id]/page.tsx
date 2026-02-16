"use client";

import { useAuth } from "@/components/auth-provider";
import { cancelOrder, confirmOrder, getOrder } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/format";
import type { SalesOrder } from "@/types/api";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

export default function OrderDetailPage() {
  const { state } = useAuth();
  const params = useParams<{ id: string }>();
  const router = useRouter();

  const [order, setOrder] = useState<SalesOrder | null>(null);
  const [error, setError] = useState("");
  const [isUpdating, setIsUpdating] = useState(false);

  const orderId = Number(params.id);

  const role = state?.user.role;
  // 受注確定/キャンセル操作はADMIN/OPERATORのみに制限する。
  const canOperate = role === "ADMIN" || role === "OPERATOR";
  const credentials = state?.credentials;

  const totalAmount = useMemo(() => {
    if (!order) {
      return 0;
    }

    // 明細の数量×単価を合算して合計金額を算出する。
    return order.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  }, [order]);

  useEffect(() => {
    const currentCredentials = credentials;
    if (!currentCredentials || Number.isNaN(orderId)) {
      return;
    }
    let mounted = true;

    async function load() {
      setError("");

      try {
        // URLの受注IDに対応する詳細を取得する。
        const data = await getOrder(currentCredentials!, orderId);
        if (mounted) {
          setOrder(data);
        }
      } catch (err) {
        if (mounted) {
          setError(err instanceof Error ? err.message : "受注取得に失敗しました。");
        }
      }
    }

    void load();

    return () => {
      mounted = false;
    };
  }, [credentials, orderId]);

  if (!state || !credentials) {
    return null;
  }

  if (Number.isNaN(orderId)) {
    return (
      <section className="card">
        <h2>受注IDが不正です。</h2>
      </section>
    );
  }

  async function handleConfirm() {
    if (!order || !canOperate) {
      return;
    }

    setError("");
    setIsUpdating(true);

    try {
      // RESERVED -> CONFIRMED の遷移を実行する。
      const updated = await confirmOrder(credentials!, order.id);
      setOrder(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "確定に失敗しました。");
    } finally {
      setIsUpdating(false);
    }
  }

  async function handleCancel() {
    if (!order || !canOperate) {
      return;
    }

    setError("");
    setIsUpdating(true);

    try {
      // RESERVED -> CANCELLED の遷移を実行する。
      const updated = await cancelOrder(credentials!, order.id);
      setOrder(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "キャンセルに失敗しました。");
    } finally {
      setIsUpdating(false);
    }
  }

  return (
    <div className="page">
      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>受注詳細</h2>
          <button className="button secondary" type="button" onClick={() => router.push("/orders")}> 
            一覧へ戻る
          </button>
        </div>

        {error && <p className="inline-error">{error}</p>}

        {!order && !error && <p>読み込み中...</p>}

        {order && (
          <div className="page" style={{ gap: 14 }}>
            <div className="grid cols-2">
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">受注番号</div>
                <div style={{ marginTop: 6 }}>{order.orderNumber}</div>
              </div>
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">顧客名</div>
                <div style={{ marginTop: 6 }}>{order.customerName}</div>
              </div>
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">ステータス</div>
                <div style={{ marginTop: 6 }}>
                  <span className={`badge ${order.status}`}>{order.status}</span>
                </div>
              </div>
              <div className="card" style={{ boxShadow: "none" }}>
                <div className="stat-label">作成日時</div>
                <div style={{ marginTop: 6 }}>{formatDateTime(order.createdAt)}</div>
              </div>
            </div>

            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>SKU</th>
                    <th>商品</th>
                    <th>単価</th>
                    <th>数量</th>
                    <th>小計</th>
                  </tr>
                </thead>
                <tbody>
                  {order.items.map((item) => (
                    <tr key={`${item.productId}-${item.sku}`}>
                      <td>{item.sku}</td>
                      <td>{item.productName}</td>
                      <td>{formatCurrency(item.unitPrice)}</td>
                      <td>{item.quantity}</td>
                      <td>{formatCurrency(item.unitPrice * item.quantity)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="card" style={{ boxShadow: "none" }}>
              <div className="stat-label">合計金額</div>
              <div className="stat-value">{formatCurrency(totalAmount)}</div>
            </div>

            {canOperate && order.status === "RESERVED" && (
              <div className="button-row">
                <button className="button primary" type="button" onClick={handleConfirm} disabled={isUpdating}>
                  {isUpdating ? "処理中..." : "受注確定"}
                </button>
                <button className="button danger" type="button" onClick={handleCancel} disabled={isUpdating}>
                  {isUpdating ? "処理中..." : "受注キャンセル"}
                </button>
              </div>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
