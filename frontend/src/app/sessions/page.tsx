"use client";

import { useAuth } from "@/components/auth-provider";
import { getSessions, revokeSession } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { AuthSession } from "@/types/api";
import { useCallback, useEffect, useState } from "react";

export default function SessionsPage() {
  const { state } = useAuth();
  const credentials = state?.credentials;

  const [sessions, setSessions] = useState<AuthSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [revokingSessionId, setRevokingSessionId] = useState("");

  const loadSessions = useCallback(async () => {
    if (!credentials) {
      return;
    }

    setLoading(true);
    setError("");
    try {
      const data = await getSessions(credentials);
      setSessions(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "セッション一覧の取得に失敗しました。");
    } finally {
      setLoading(false);
    }
  }, [credentials]);

  useEffect(() => {
    if (!credentials) {
      return;
    }
    void loadSessions();
  }, [credentials, loadSessions]);

  if (!state || !credentials) {
    return null;
  }

  async function handleRevoke(sessionId: string) {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }

    setError("");
    setSuccess("");
    setRevokingSessionId(sessionId);

    try {
      await revokeSession(currentCredentials, sessionId);
      setSuccess("セッションを失効しました。");
      await loadSessions();
    } catch (err) {
      setError(err instanceof Error ? err.message : "セッション失効に失敗しました。");
    } finally {
      setRevokingSessionId("");
    }
  }

  return (
    <div className="page">
      <section className="card">
        <div className="button-row" style={{ justifyContent: "space-between", marginBottom: 10 }}>
          <h2>ログインセッション</h2>
          <span style={{ color: "#607086", fontSize: 13 }}>{sessions.length}件</span>
        </div>
        <p style={{ margin: "0 0 12px", color: "#607086" }}>
          不明な端末や不要なセッションは「失効」で即時ログアウトできます。
        </p>

        {error && <p className="inline-error">{error}</p>}
        {success && <p style={{ color: "#137a49" }}>{success}</p>}

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>セッションID</th>
                <th>端末情報</th>
                <th>IP</th>
                <th>最終利用</th>
                <th>有効期限</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((session) => (
                <tr key={session.sessionId}>
                  <td>{session.sessionId}</td>
                  <td>{session.userAgent ?? "-"}</td>
                  <td>{session.ipAddress ?? "-"}</td>
                  <td>{formatDateTime(session.lastUsedAt)}</td>
                  <td>{formatDateTime(session.expiresAt)}</td>
                  <td>
                    <button
                      className="button danger"
                      type="button"
                      onClick={() => handleRevoke(session.sessionId)}
                      disabled={revokingSessionId === session.sessionId}
                    >
                      {revokingSessionId === session.sessionId ? "処理中..." : "失効"}
                    </button>
                  </td>
                </tr>
              ))}
              {!loading && sessions.length === 0 && (
                <tr>
                  <td colSpan={6}>有効なセッションはありません。</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
