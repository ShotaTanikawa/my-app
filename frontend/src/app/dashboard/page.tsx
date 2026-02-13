"use client";

import { useAuth } from "@/components/auth-provider";
import { getOrders, getProducts } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { SalesOrder } from "@/types/api";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

const LOW_STOCK_THRESHOLD = Number(process.env.NEXT_PUBLIC_LOW_STOCK_THRESHOLD ?? 10);

export default function DashboardPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;

  const [orders, setOrders] = useState<SalesOrder[]>([]);
  const [productCount, setProductCount] = useState(0);
  const [lowStockCount, setLowStockCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

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
        const [products, allOrders] = await Promise.all([
          getProducts(currentCredentials!),
          getOrders(currentCredentials!),
        ]);

        if (!mounted) {
          return;
        }

        setProductCount(products.length);
        setLowStockCount(products.filter((item) => item.availableQuantity <= LOW_STOCK_THRESHOLD).length);
        setOrders(allOrders.slice(0, 5));
      } catch (err) {
        if (!mounted) {
          return;
        }

        const message = err instanceof Error ? err.message : "ダッシュボードの取得に失敗しました。";
        setError(message);
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

  const reservedOrderCount = useMemo(
    () => orders.filter((order) => order.status === "RESERVED").length,
    [orders],
  );

  if (!state || !credentials) {
    return null;
  }

  return (
    <div className="page">
      <div className="grid cols-4">
        <section className="card">
          <div className="stat-label">Products</div>
          <div className="stat-value">{loading ? "..." : productCount}</div>
        </section>
        <section className="card">
          <div className="stat-label">Orders (Recent)</div>
          <div className="stat-value">{loading ? "..." : orders.length}</div>
        </section>
        <section className="card">
          <div className="stat-label">Reserved (Recent)</div>
          <div className="stat-value">{loading ? "..." : reservedOrderCount}</div>
        </section>
        <section className="card">
          <div className="stat-label">Low Stock (≤ {LOW_STOCK_THRESHOLD})</div>
          <div className="stat-value">{loading ? "..." : lowStockCount}</div>
        </section>
      </div>

      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>最近の受注</h2>
          <Link href="/orders" className="button link">
            受注一覧へ
          </Link>
        </div>

        {error && <p className="inline-error">{error}</p>}

        {!error && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>受注番号</th>
                  <th>顧客</th>
                  <th>ステータス</th>
                  <th>作成日時</th>
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
                  </tr>
                ))}
                {!loading && orders.length === 0 && (
                  <tr>
                    <td colSpan={4}>受注データがありません。</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
