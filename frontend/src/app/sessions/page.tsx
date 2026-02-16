"use client";

import { useAuth } from "@/features/auth";
import { useToast } from "@/features/feedback";
import { disableMfa, enableMfa, getSessions, revokeSession, setupMfa } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { AuthSession } from "@/types/api";
import { useCallback, useEffect, useState } from "react";

export default function SessionsPage() {
  const { state, reloadMe } = useAuth();
  const { showError, showSuccess } = useToast();
  const credentials = state?.credentials;

  const [sessions, setSessions] = useState<AuthSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [revokingSessionId, setRevokingSessionId] = useState("");
  const [mfaCode, setMfaCode] = useState("");
  const [mfaSetupSecret, setMfaSetupSecret] = useState("");
  const [mfaSetupOtpAuthUrl, setMfaSetupOtpAuthUrl] = useState("");
  const [mfaLoadingAction, setMfaLoadingAction] = useState<"" | "setup" | "enable" | "disable">("");
  const [mfaError, setMfaError] = useState("");
  const [mfaSuccess, setMfaSuccess] = useState("");

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
      const message = "セッションを失効しました。";
      setSuccess(message);
      showSuccess(message);
      await loadSessions();
    } catch (err) {
      const message = err instanceof Error ? err.message : "セッション失効に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setRevokingSessionId("");
    }
  }

  function clearMfaMessages() {
    setMfaError("");
    setMfaSuccess("");
  }

  async function handleSetupMfa() {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }

    clearMfaMessages();
    setMfaLoadingAction("setup");
    try {
      const response = await setupMfa(currentCredentials);
      setMfaSetupSecret(response.secret);
      setMfaSetupOtpAuthUrl(response.otpauthUrl);
      setMfaCode("");
      await reloadMe();

      const message = "MFAセットアップを開始しました。認証アプリに登録して6桁コードで有効化してください。";
      setMfaSuccess(message);
      showSuccess(message);
    } catch (err) {
      const message = err instanceof Error ? err.message : "MFAセットアップの開始に失敗しました。";
      setMfaError(message);
      showError(message);
    } finally {
      setMfaLoadingAction("");
    }
  }

  async function handleEnableMfa() {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }
    if (!mfaCode.trim()) {
      const message = "MFAを有効化するには6桁コードを入力してください。";
      setMfaError(message);
      showError(message);
      return;
    }

    clearMfaMessages();
    setMfaLoadingAction("enable");
    try {
      await enableMfa(currentCredentials, mfaCode.trim());
      await reloadMe();
      setMfaSetupSecret("");
      setMfaSetupOtpAuthUrl("");
      setMfaCode("");

      const message = "MFAを有効化しました。次回ログインからコード入力が必要です。";
      setMfaSuccess(message);
      showSuccess(message);
    } catch (err) {
      const message = err instanceof Error ? err.message : "MFA有効化に失敗しました。";
      setMfaError(message);
      showError(message);
    } finally {
      setMfaLoadingAction("");
    }
  }

  async function handleDisableMfa() {
    const currentCredentials = credentials;
    if (!currentCredentials) {
      return;
    }
    if (!mfaCode.trim()) {
      const message = "MFAを無効化するには現在の6桁コードを入力してください。";
      setMfaError(message);
      showError(message);
      return;
    }

    clearMfaMessages();
    setMfaLoadingAction("disable");
    try {
      await disableMfa(currentCredentials, mfaCode.trim());
      await reloadMe();
      setMfaSetupSecret("");
      setMfaSetupOtpAuthUrl("");
      setMfaCode("");

      const message = "MFAを無効化しました。";
      setMfaSuccess(message);
      showSuccess(message);
    } catch (err) {
      const message = err instanceof Error ? err.message : "MFA無効化に失敗しました。";
      setMfaError(message);
      showError(message);
    } finally {
      setMfaLoadingAction("");
    }
  }

  return (
    <div className="page">
      <section className="card">
        <h2 style={{ marginBottom: 8 }}>MFA（二要素認証）</h2>
        <p style={{ margin: "0 0 12px", color: "#607086" }}>
          ログイン時の不正アクセスを防ぐため、認証アプリのワンタイムコードを追加で要求します。
        </p>
        <div className="button-row" style={{ alignItems: "center", marginBottom: 10 }}>
          <span
            className={state.user.mfaEnabled ? "badge CONFIRMED" : "badge CANCELLED"}
            style={{ fontSize: 12 }}
          >
            {state.user.mfaEnabled ? "MFA有効" : "MFA無効"}
          </span>
          {!state.user.mfaEnabled && (
            <button
              className="button secondary"
              type="button"
              onClick={handleSetupMfa}
              disabled={mfaLoadingAction !== ""}
            >
              {mfaLoadingAction === "setup" ? "セットアップ中..." : "セットアップ開始"}
            </button>
          )}
        </div>

        {mfaSetupSecret && (
          <div className="grid cols-2" style={{ marginBottom: 12 }}>
            <div className="field">
              <label htmlFor="mfa-secret">共有シークレット</label>
              <input id="mfa-secret" className="input" value={mfaSetupSecret} readOnly />
            </div>
            <div className="field">
              <label htmlFor="mfa-otpauth-url">OTPAuth URL</label>
              <input id="mfa-otpauth-url" className="input" value={mfaSetupOtpAuthUrl} readOnly />
            </div>
          </div>
        )}

        <div className="form-grid single">
          <div className="field">
            <label htmlFor="mfa-code-input">認証コード（6桁）</label>
            <input
              id="mfa-code-input"
              className="input"
              value={mfaCode}
              onChange={(event) => setMfaCode(event.target.value)}
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="123456"
            />
          </div>
        </div>

        <div className="button-row" style={{ marginTop: 12 }}>
          {!state.user.mfaEnabled && (
            <button
              className="button primary"
              type="button"
              onClick={handleEnableMfa}
              disabled={mfaLoadingAction !== ""}
            >
              {mfaLoadingAction === "enable" ? "有効化中..." : "MFAを有効化"}
            </button>
          )}
          {state.user.mfaEnabled && (
            <button
              className="button danger"
              type="button"
              onClick={handleDisableMfa}
              disabled={mfaLoadingAction !== ""}
            >
              {mfaLoadingAction === "disable" ? "無効化中..." : "MFAを無効化"}
            </button>
          )}
        </div>
        {mfaError && <p className="inline-error">{mfaError}</p>}
        {mfaSuccess && <p style={{ color: "#137a49", marginTop: 8 }}>{mfaSuccess}</p>}
      </section>

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
