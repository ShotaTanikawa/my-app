"use client";

import { useAuth } from "@/components/auth-provider";
import { getOrders, getProducts, getPurchaseOrders } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { PurchaseOrder, SalesOrder } from "@/types/api";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

// 在庫注意の基準値は環境変数で調整できるようにする。
const LOW_STOCK_THRESHOLD = Number(process.env.NEXT_PUBLIC_LOW_STOCK_THRESHOLD ?? 10);

export default function DashboardPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;

  const [orders, setOrders] = useState<SalesOrder[]>([]);
  const [purchaseOrders, setPurchaseOrders] = useState<PurchaseOrder[]>([]);
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
        // ダッシュボードの指標をまとめて取得し、待ち時間を短縮する。
        const [products, allOrders, allPurchaseOrders] = await Promise.all([
          getProducts(currentCredentials!),
          getOrders(currentCredentials!),
          getPurchaseOrders(currentCredentials!),
        ]);

        if (!mounted) {
          return;
        }

        setProductCount(products.length);
        setLowStockCount(products.filter((item) => item.availableQuantity <= LOW_STOCK_THRESHOLD).length);
        setOrders(allOrders.slice(0, 5));
        setPurchaseOrders(allPurchaseOrders);
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
    // 直近取得分から要対応の受注件数を算出する。
    () => orders.filter((order) => order.status === "RESERVED").length,
    [orders],
  );
  const orderedPurchaseCount = useMemo(
    () => purchaseOrders.filter((order) => order.status === "ORDERED").length,
    [purchaseOrders],
  );

  if (!state || !credentials) {
    return null;
  }

  return (
    <div className="page">
      <div className="grid cols-5">
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
        <section className="card">
          <div className="stat-label">Purchase Ordered</div>
          <div className="stat-value">{loading ? "..." : orderedPurchaseCount}</div>
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
