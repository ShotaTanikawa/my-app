"use client";

import { useAuth } from "@/components/auth-provider";
import { getOrders } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { SalesOrder } from "@/types/api";
import Link from "next/link";
import { useEffect, useState } from "react";

export default function OrdersPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;

  const [orders, setOrders] = useState<SalesOrder[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }
    let mounted = true;

    async function load() {
      setLoading(true);
      setError("");

      try {
        const data = await getOrders(currentCredentials!);
        if (mounted) {
          setOrders(data);
        }
      } catch (err) {
        if (!mounted) {
          return;
        }

        setError(err instanceof Error ? err.message : "受注の取得に失敗しました。");
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => {
      mounted = false;
    };
  }, [credentials]);

  if (!state || !credentials) {
    return null;
  }

  const canOperate = state.user.role === "ADMIN" || state.user.role === "OPERATOR";

  return (
    <div className="page">
      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>受注一覧</h2>
          {canOperate && (
            <Link className="button primary" href="/orders/new">
              新規受注
            </Link>
          )}
        </div>

        {error && <p className="inline-error">{error}</p>}

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>受注番号</th>
                <th>顧客名</th>
                <th>ステータス</th>
                <th>作成日時</th>
                <th>明細数</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id}>
                  <td>
                    <Link href={`/orders/${order.id}`} className="button link">
                      {order.orderNumber}
                    </Link>
                  </td>
                  <td>{order.customerName}</td>
                  <td>
                    <span className={`badge ${order.status}`}>{order.status}</span>
                  </td>
                  <td>{formatDateTime(order.createdAt)}</td>
                  <td>{order.items.length}</td>
                </tr>
              ))}
              {!loading && orders.length === 0 && (
                <tr>
                  <td colSpan={5}>受注データがありません。</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
