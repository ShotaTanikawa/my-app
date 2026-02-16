"use client";

import { useAuth } from "@/components/auth-provider";
import { getAuditLogs } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { AuditLog } from "@/types/api";
import { useEffect, useState } from "react";

export default function AuditLogsPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;
  const role = state?.user.role;

  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

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
        // 画面表示コストと可読性のバランスを取り、最新200件を取得する。
        const data = await getAuditLogs(currentCredentials, 200);
        if (mounted) {
          setLogs(data);
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
  }, [credentials, role]);

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

  return (
    <div className="page">
      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>監査ログ</h2>
          <span style={{ color: "#607086", fontSize: 13 }}>最新200件</span>
        </div>

        {error && <p className="inline-error">{error}</p>}

        {!error && (
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
                    <td colSpan={6}>監査ログがありません。</td>
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
