"use client";

import { useAuth } from "@/components/auth-provider";
import { getOrders, getProducts, getPurchaseOrders } from "@/lib/api";
import { formatDateTime, formatSalesOrderStatus } from "@/lib/format";
import type { PurchaseOrder, SalesOrder } from "@/types/api";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

// 在庫注意の基準値は環境変数で調整できるようにする。
const LOW_STOCK_THRESHOLD = Number(process.env.NEXT_PUBLIC_LOW_STOCK_THRESHOLD ?? 10);

export default function DashboardPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;
  const role = state?.user.role;
  const canOperate = role === "ADMIN" || role === "OPERATOR";

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
    () =>
      purchaseOrders.filter(
        (order) => order.status === "ORDERED" || order.status === "PARTIALLY_RECEIVED",
      ).length,
    [purchaseOrders],
  );

  if (!state || !credentials) {
    return null;
  }

  return (
    <div className="page">
      <div className="grid cols-5">
        <section className="card">
          <div className="stat-label">商品数</div>
          <div className="stat-value">{loading ? "..." : productCount}</div>
        </section>
        <section className="card">
          <div className="stat-label">直近受注件数</div>
          <div className="stat-value">{loading ? "..." : orders.length}</div>
        </section>
        <section className="card">
          <div className="stat-label">引当中受注</div>
          <div className="stat-value">{loading ? "..." : reservedOrderCount}</div>
        </section>
        <section className="card">
          <div className="stat-label">在庫注意 (≦ {LOW_STOCK_THRESHOLD})</div>
          <div className="stat-value">{loading ? "..." : lowStockCount}</div>
        </section>
        <section className="card">
          <div className="stat-label">未完了仕入発注</div>
          <div className="stat-value">{loading ? "..." : orderedPurchaseCount}</div>
        </section>
      </div>

      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>クイックスタート</h2>
          <span style={{ color: "#607086", fontSize: 13 }}>迷ったらこの順番で進める</span>
        </div>
        <div className="grid cols-3">
          <article className="card" style={{ boxShadow: "none" }}>
            <div className="stat-label">Step 1</div>
            <h3 style={{ marginTop: 6 }}>商品・仕入先を整備</h3>
            <p style={{ color: "#607086", marginTop: 8 }}>
              まず商品・在庫情報と仕入先契約を登録します。
            </p>
            <div className="button-row" style={{ marginTop: 10 }}>
              <Link href="/products" className="button secondary">
                商品・在庫へ
              </Link>
              <Link href="/suppliers" className="button secondary">
                仕入先へ
              </Link>
            </div>
          </article>
          <article className="card" style={{ boxShadow: "none" }}>
            <div className="stat-label">Step 2</div>
            <h3 style={{ marginTop: 6 }}>受注を登録</h3>
            <p style={{ color: "#607086", marginTop: 8 }}>
              顧客からの注文を入力し、引当状況を確認します。
            </p>
            <div className="button-row" style={{ marginTop: 10 }}>
              {canOperate ? (
                <Link href="/orders/new" className="button secondary">
                  新規受注
                </Link>
              ) : (
                <span style={{ color: "#607086", fontSize: 13 }}>作成権限がありません</span>
              )}
              <Link href="/orders" className="button secondary">
                受注一覧
              </Link>
            </div>
          </article>
          <article className="card" style={{ boxShadow: "none" }}>
            <div className="stat-label">Step 3</div>
            <h3 style={{ marginTop: 6 }}>仕入発注と入荷</h3>
            <p style={{ color: "#607086", marginTop: 8 }}>
              補充提案を参考に発注し、入荷履歴を管理します。
            </p>
            <div className="button-row" style={{ marginTop: 10 }}>
              {canOperate ? (
                <Link href="/purchase-orders/new" className="button secondary">
                  新規仕入発注
                </Link>
              ) : (
                <span style={{ color: "#607086", fontSize: 13 }}>作成権限がありません</span>
              )}
              <Link href="/purchase-orders" className="button secondary">
                仕入発注一覧
              </Link>
            </div>
          </article>
        </div>
      </section>

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
                      <span className={`badge ${order.status}`}>{formatSalesOrderStatus(order.status)}</span>
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
