"use client";

import { useAuth } from "@/components/auth-provider";
import { getPurchaseOrders, getReplenishmentSuggestions } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { PurchaseOrder, ReplenishmentSuggestion } from "@/types/api";
import Link from "next/link";
import { useEffect, useState } from "react";

export default function PurchaseOrdersPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;
  const role = state?.user.role;
  const canOperate = role === "ADMIN" || role === "OPERATOR";

  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const [suggestions, setSuggestions] = useState<ReplenishmentSuggestion[]>([]);
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
        // 一覧表示に必要な発注データと補充提案を並列で取得する。
        const [orderData, suggestionData] = await Promise.all([
          getPurchaseOrders(currentCredentials!),
          getReplenishmentSuggestions(currentCredentials!),
        ]);

        if (!mounted) {
          return;
        }

        setOrders(orderData);
        setSuggestions(suggestionData);
      } catch (err) {
        if (!mounted) {
          return;
        }
        setError(err instanceof Error ? err.message : "仕入発注情報の取得に失敗しました。");
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

  return (
    <div className="page">
      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>仕入発注一覧</h2>
          {canOperate && (
            <Link className="button primary" href="/purchase-orders/new">
              新規発注
            </Link>
          )}
        </div>

        {error && <p className="inline-error">{error}</p>}

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>発注番号</th>
                <th>仕入先</th>
                <th>ステータス</th>
                <th>作成日時</th>
                <th>入荷日時</th>
                <th>明細数</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id}>
                  <td>
                    <Link href={`/purchase-orders/${order.id}`} className="button link">
                      {order.orderNumber}
                    </Link>
                  </td>
                  <td>{order.supplierName}</td>
                  <td>
                    <span className={`badge ${order.status}`}>{order.status}</span>
                  </td>
                  <td>{formatDateTime(order.createdAt)}</td>
                  <td>{order.receivedAt ? formatDateTime(order.receivedAt) : "-"}</td>
                  <td>{order.items.length}</td>
                </tr>
              ))}
              {!loading && orders.length === 0 && (
                <tr>
                  <td colSpan={6}>仕入発注データがありません。</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>補充提案</h2>
          <span style={{ color: "#607086", fontSize: 13 }}>{suggestions.length}件</span>
        </div>

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>SKU</th>
                <th>商品名</th>
                <th>販売可能</th>
                <th>引当済</th>
                <th>再発注点</th>
                <th>発注ロット</th>
                <th>不足数</th>
                <th>推奨数</th>
              </tr>
            </thead>
            <tbody>
              {suggestions.map((suggestion) => (
                <tr key={suggestion.productId}>
                  <td>{suggestion.sku}</td>
                  <td>{suggestion.productName}</td>
                  <td>{suggestion.availableQuantity}</td>
                  <td>{suggestion.reservedQuantity}</td>
                  <td>{suggestion.reorderPoint}</td>
                  <td>{suggestion.reorderQuantity}</td>
                  <td>{suggestion.shortageQuantity}</td>
                  <td>{suggestion.suggestedQuantity}</td>
                </tr>
              ))}
              {!loading && suggestions.length === 0 && (
                <tr>
                  <td colSpan={8}>補充提案はありません。</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
