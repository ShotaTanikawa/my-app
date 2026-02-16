"use client";

import { useAuth } from "@/components/auth-provider";
import { exportAuditLogsCsv, getAuditLogs } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { AuditLog } from "@/types/api";
import { FormEvent, useEffect, useState } from "react";

const PAGE_SIZE = 50;
const ACTION_OPTIONS = [
  "AUTH_LOGIN",
  "AUTH_REFRESH",
  "AUTH_LOGOUT",
  "PRODUCT_CREATE",
  "PRODUCT_UPDATE",
  "STOCK_ADD",
  "ORDER_CREATE",
  "ORDER_CONFIRM",
  "ORDER_CANCEL",
] as const;

export default function AuditLogsPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;
  const role = state?.user.role;

  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [draftAction, setDraftAction] = useState("");
  const [draftActor, setDraftActor] = useState("");
  const [actionFilter, setActionFilter] = useState("");
  const [actorFilter, setActorFilter] = useState("");
  const [isExporting, setIsExporting] = useState(false);

  useEffect(() => {
    // 監査ログ閲覧はADMINのみ許可する。
    if (role !== "ADMIN" || !credentials) {
      return;
    }
    const currentCredentials = credentials;

    let mounted = true;
    async function load() {
      setLoading(true);
      setError("");
      try {
        // 条件に応じて最新順で監査ログをページ取得する。
        const data = await getAuditLogs(currentCredentials, {
          page,
          size: PAGE_SIZE,
          action: actionFilter || undefined,
          actor: actorFilter || undefined,
        });
        if (mounted) {
          setLogs(data.items);
          setTotalPages(data.totalPages);
          setTotalElements(data.totalElements);
          setHasNext(data.hasNext);
          setHasPrevious(data.hasPrevious);
        }
      } catch (err) {
        if (!mounted) {
          return;
        }
        setError(err instanceof Error ? err.message : "監査ログの取得に失敗しました。");
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
  }, [credentials, role, page, actionFilter, actorFilter]);

  if (!state || !credentials) {
    return null;
  }

  if (role !== "ADMIN") {
    return (
      <div className="page">
        <section className="card">
          <h2>監査ログ</h2>
          <p style={{ marginTop: 10 }}>この画面はADMINのみ閲覧できます。</p>
        </section>
      </div>
    );
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setActionFilter(draftAction.trim());
    setActorFilter(draftActor.trim());
  }

  function handleClear() {
    setDraftAction("");
    setDraftActor("");
    setActionFilter("");
    setActorFilter("");
    setPage(0);
  }

  async function handleExportCsv() {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }

    setError("");
    setIsExporting(true);
    try {
      const blob = await exportAuditLogsCsv(currentCredentials, {
        action: actionFilter || undefined,
        actor: actorFilter || undefined,
        limit: 2000,
      });
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
      anchor.href = url;
      anchor.download = `audit-logs-${timestamp}.csv`;
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
          <h2>監査ログ</h2>
          <span style={{ color: "#607086", fontSize: 13 }}>
            全{totalElements}件 / {page + 1}ページ目
          </span>
        </div>

        <form className="form-grid" onSubmit={handleSearch}>
          <div className="field">
            <label htmlFor="audit-action">操作種別</label>
            <select
              id="audit-action"
              className="select"
              value={draftAction}
              onChange={(event) => setDraftAction(event.target.value)}
            >
              <option value="">すべて</option>
              {ACTION_OPTIONS.map((action) => (
                <option key={action} value={action}>
                  {action}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="audit-actor">ユーザー名</label>
            <input
              id="audit-actor"
              className="input"
              value={draftActor}
              onChange={(event) => setDraftActor(event.target.value)}
              placeholder="部分一致"
            />
          </div>
          <div className="button-row" style={{ alignItems: "end" }}>
            <button className="button primary" type="submit" disabled={loading}>
              検索
            </button>
            <button className="button secondary" type="button" onClick={handleClear} disabled={loading}>
              クリア
            </button>
            <button
              className="button secondary"
              type="button"
              onClick={handleExportCsv}
              disabled={loading || isExporting}
            >
              {isExporting ? "出力中..." : "CSV出力"}
            </button>
          </div>
        </form>

        {error && <p className="inline-error">{error}</p>}

        {!error && (
          <div className="page" style={{ gap: 10 }}>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>日時</th>
                    <th>ユーザー</th>
                    <th>ロール</th>
                    <th>操作</th>
                    <th>対象</th>
                    <th>詳細</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <tr key={log.id}>
                      <td>{formatDateTime(log.createdAt)}</td>
                      <td>{log.actorUsername}</td>
                      <td>{log.actorRole}</td>
                      <td>{log.action}</td>
                      <td>
                        {log.targetType ?? "-"}
                        {log.targetId ? `#${log.targetId}` : ""}
                      </td>
                      <td>{log.detail ?? "-"}</td>
                    </tr>
                  ))}
                  {!loading && logs.length === 0 && (
                    <tr>
                      <td colSpan={6}>条件に一致する監査ログがありません。</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <div className="button-row" style={{ justifyContent: "space-between" }}>
              <span style={{ color: "#607086", fontSize: 13 }}>
                {Math.max(totalPages, 1)}ページ中 {page + 1}ページ目
              </span>
              <div className="button-row">
                <button
                  className="button secondary"
                  type="button"
                  onClick={() => setPage((prev) => Math.max(prev - 1, 0))}
                  disabled={!hasPrevious || loading}
                >
                  前へ
                </button>
                <button
                  className="button secondary"
                  type="button"
                  onClick={() => setPage((prev) => prev + 1)}
                  disabled={!hasNext || loading}
                >
                  次へ
                </button>
              </div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
