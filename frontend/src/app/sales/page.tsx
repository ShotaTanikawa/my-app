"use client";

import { useAuth } from "@/features/auth";
import { exportSalesCsv, getSalesReport } from "@/lib/api";
import { formatCurrency, formatDateTime } from "@/lib/format";
import type { SalesGroupBy, SalesReport } from "@/types/api";
import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";

type SalesFilterState = {
  fromDate: string;
  toDate: string;
  groupBy: SalesGroupBy;
};

function toIsoStartOfDay(value: string): string | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = new Date(`${value}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) {
    return undefined;
  }
  return parsed.toISOString();
}

function toIsoEndOfDay(value: string): string | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = new Date(`${value}T23:59:59`);
  if (Number.isNaN(parsed.getTime())) {
    return undefined;
  }
  return parsed.toISOString();
}

function toDateInputValue(date: Date): string {
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function buildInitialFilters(): SalesFilterState {
  const now = new Date();
  const firstDateOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  return {
    fromDate: toDateInputValue(firstDateOfMonth),
    toDate: toDateInputValue(now),
    groupBy: "DAY",
  };
}

export default function SalesPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;

  const [draftFilters, setDraftFilters] = useState<SalesFilterState>(() => buildInitialFilters());
  const [appliedFilters, setAppliedFilters] = useState<SalesFilterState>(() => buildInitialFilters());
  const [report, setReport] = useState<SalesReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);
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
        const data = await getSalesReport(currentCredentials!, {
          from: toIsoStartOfDay(appliedFilters.fromDate),
          to: toIsoEndOfDay(appliedFilters.toDate),
          groupBy: appliedFilters.groupBy,
          lineLimit: 300,
        });
        if (mounted) {
          setReport(data);
        }
      } catch (err) {
        if (!mounted) {
          return;
        }
        setError(err instanceof Error ? err.message : "売上データの取得に失敗しました。");
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
  }, [credentials, appliedFilters]);

  if (!state || !credentials) {
    return null;
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAppliedFilters(draftFilters);
  }

  function handleClear() {
    const next = buildInitialFilters();
    setDraftFilters(next);
    setAppliedFilters(next);
  }

  async function handleExportCsv() {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }
    setError("");
    setIsExporting(true);
    try {
      const blob = await exportSalesCsv(currentCredentials, {
        from: toIsoStartOfDay(appliedFilters.fromDate),
        to: toIsoEndOfDay(appliedFilters.toDate),
        limit: 5000,
      });
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
      anchor.href = url;
      anchor.download = `sales-report-${timestamp}.csv`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "CSV出力に失敗しました。");
    } finally {
      setIsExporting(false);
    }
  }

  return (
    <div className="page">
      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>売上サマリー</h2>
          <button
            className="button secondary"
            type="button"
            onClick={handleExportCsv}
            disabled={loading || isExporting}
          >
            {isExporting ? "出力中..." : "CSV出力"}
          </button>
        </div>
        <p style={{ margin: "0 0 12px", color: "#607086" }}>
          計上基準: 受注ステータスが`CONFIRMED`になった時点（システム上は更新日時基準）。
        </p>

        <form className="form-grid" onSubmit={handleSearch}>
          <div className="field">
            <label htmlFor="sales-from">開始日</label>
            <input
              id="sales-from"
              className="input"
              type="date"
              value={draftFilters.fromDate}
              onChange={(event) =>
                setDraftFilters((prev) => ({ ...prev, fromDate: event.target.value }))
              }
            />
          </div>
          <div className="field">
            <label htmlFor="sales-to">終了日</label>
            <input
              id="sales-to"
              className="input"
              type="date"
              value={draftFilters.toDate}
              onChange={(event) =>
                setDraftFilters((prev) => ({ ...prev, toDate: event.target.value }))
              }
            />
          </div>
          <div className="field">
            <label htmlFor="sales-group-by">集計粒度</label>
            <select
              id="sales-group-by"
              className="select"
              value={draftFilters.groupBy}
              onChange={(event) =>
                setDraftFilters((prev) => ({
                  ...prev,
                  groupBy: event.target.value as SalesGroupBy,
                }))
              }
            >
              <option value="DAY">日次</option>
              <option value="WEEK">週次</option>
              <option value="MONTH">月次</option>
            </select>
          </div>
          <div className="button-row" style={{ alignItems: "end" }}>
            <button className="button primary" type="submit" disabled={loading}>
              検索
            </button>
            <button className="button secondary" type="button" onClick={handleClear} disabled={loading}>
              クリア
            </button>
          </div>
        </form>

        {error && <p className="inline-error">{error}</p>}

        {!error && report && (
          <div className="grid cols-4" style={{ marginTop: 12 }}>
            <div className="card" style={{ boxShadow: "none" }}>
              <div className="stat-label">売上合計</div>
              <div className="stat-value">{formatCurrency(report.summary.totalSalesAmount)}</div>
            </div>
            <div className="card" style={{ boxShadow: "none" }}>
              <div className="stat-label">注文件数</div>
              <div className="stat-value">{report.summary.orderCount}</div>
            </div>
            <div className="card" style={{ boxShadow: "none" }}>
              <div className="stat-label">平均受注単価</div>
              <div className="stat-value">{formatCurrency(report.summary.averageOrderAmount)}</div>
            </div>
            <div className="card" style={{ boxShadow: "none" }}>
              <div className="stat-label">販売数量</div>
              <div className="stat-value">{report.summary.totalItemQuantity}</div>
            </div>
          </div>
        )}
      </section>

      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>売上推移</h2>
          {report && <span style={{ color: "#607086", fontSize: 13 }}>{report.trends.length}件</span>}
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>期間開始</th>
                <th>売上合計</th>
                <th>注文件数</th>
                <th>販売数量</th>
              </tr>
            </thead>
            <tbody>
              {report?.trends.map((trend) => (
                <tr key={trend.bucketStart}>
                  <td>{formatDateTime(trend.bucketStart)}</td>
                  <td>{formatCurrency(trend.totalSalesAmount)}</td>
                  <td>{trend.orderCount}</td>
                  <td>{trend.totalItemQuantity}</td>
                </tr>
              ))}
              {!loading && (!report || report.trends.length === 0) && (
                <tr>
                  <td colSpan={4}>対象期間の売上推移データはありません。</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>売上明細</h2>
          {report && (
            <span style={{ color: "#607086", fontSize: 13 }}>
              表示 {report.lines.length}件 / 全{report.totalLineCount}件
            </span>
          )}
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>売上日時</th>
                <th>受注番号</th>
                <th>顧客</th>
                <th>SKU</th>
                <th>商品名</th>
                <th>数量</th>
                <th>単価</th>
                <th>小計</th>
              </tr>
            </thead>
            <tbody>
              {report?.lines.map((line) => (
                <tr key={`${line.orderId}-${line.productId}-${line.soldAt}`}>
                  <td>{formatDateTime(line.soldAt)}</td>
                  <td>
                    <Link href={`/orders/${line.orderId}`} className="button link">
                      {line.orderNumber}
                    </Link>
                  </td>
                  <td>{line.customerName}</td>
                  <td>{line.sku}</td>
                  <td>{line.productName}</td>
                  <td>{line.quantity}</td>
                  <td>{formatCurrency(line.unitPrice)}</td>
                  <td>{formatCurrency(line.lineAmount)}</td>
                </tr>
              ))}
              {!loading && (!report || report.lines.length === 0) && (
                <tr>
                  <td colSpan={8}>対象期間の売上明細はありません。</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
