"use client";

import { useAuth } from "@/features/auth";
import { useToast } from "@/features/feedback";
import { ApiClientError, confirmPasswordReset, requestPasswordReset } from "@/lib/api";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";

type LoginMode = "login" | "reset-request" | "reset-confirm";

export default function LoginPage() {
  const { login, state, isHydrated } = useAuth();
  const { showError, showSuccess } = useToast();
  const router = useRouter();

  const [mode, setMode] = useState<LoginMode>("login");
  const [username, setUsername] = useState("operator");
  const [password, setPassword] = useState("operator123");
  const [mfaCode, setMfaCode] = useState("");
  const [resetRequestUsername, setResetRequestUsername] = useState("operator");
  const [resetToken, setResetToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmNewPassword, setConfirmNewPassword] = useState("");
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    // 認証情報の復元が終わるまで判定しない。
    if (!isHydrated) {
      return;
    }

    // 既ログインの場合はログイン画面を経由させない。
    if (state) {
      router.replace("/dashboard");
    }
  }, [isHydrated, router, state]);

  function switchMode(nextMode: LoginMode) {
    setMode(nextMode);
    setError("");
    setInfo("");
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setInfo("");
    setIsSubmitting(true);

    try {
      // 認証成功時にAuthProvider側へトークンを保存する。
      await login(username, password, mfaCode);
      router.replace("/dashboard");
    } catch (err) {
      if (err instanceof ApiClientError) {
        if (err.message.includes("MFA")) {
          setError("MFAコードが未入力か不正です。認証アプリの6桁コードを入力してください。");
        } else {
          setError(err.message || "ログインに失敗しました。");
        }
      } else {
        setError("ログインに失敗しました。");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleRequestPasswordReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setInfo("");
    setIsSubmitting(true);

    try {
      const response = await requestPasswordReset(resetRequestUsername);
      const message = "パスワード再設定の申請を受け付けました。";
      showSuccess(message);

      if (response.resetToken) {
        setResetToken(response.resetToken);
        setMode("reset-confirm");
        setError("");
        setInfo("検証トークンを取得しました。次に「再設定確定」で新しいパスワードを設定してください。");
      } else {
        setInfo(
          "申請を受け付けました。運用環境ではメール通知されます。学習用にトークンを表示する場合はバックエンドに APP_PASSWORD_RESET_EXPOSE_TOKEN=true を設定してください。",
        );
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "パスワード再設定申請に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleConfirmPasswordReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setInfo("");

    if (newPassword !== confirmNewPassword) {
      const message = "新しいパスワードが一致していません。";
      setError(message);
      showError(message);
      return;
    }
    if (newPassword.trim().length < 8) {
      const message = "新しいパスワードは8文字以上で入力してください。";
      setError(message);
      showError(message);
      return;
    }

    setIsSubmitting(true);
    try {
      await confirmPasswordReset(resetToken, newPassword);
      setResetToken("");
      setNewPassword("");
      setConfirmNewPassword("");
      setMode("login");
      setError("");
      setInfo("パスワードを更新しました。新しいパスワードでログインしてください。");
      showSuccess("パスワードを更新しました。");
    } catch (err) {
      const message = err instanceof Error ? err.message : "パスワード再設定に失敗しました。";
      setError(message);
      showError(message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <h1 className="login-title">FlowStock</h1>
        <p className="login-subtitle">受発注・在庫管理へログイン</p>
        <div className="login-helper">
          <div className="login-helper-title">学習用アカウント</div>
          <p className="login-helper-item">担当者: `operator / operator123`</p>
          <p className="login-helper-item">管理者: `admin / admin123`</p>
        </div>

        <div className="button-row login-mode-switch" style={{ marginBottom: 14 }}>
          <button
            className={mode === "login" ? "button primary" : "button secondary"}
            type="button"
            onClick={() => switchMode("login")}
          >
            ログイン
          </button>
          <button
            className={mode === "reset-request" ? "button primary" : "button secondary"}
            type="button"
            onClick={() => switchMode("reset-request")}
          >
            再設定申請
          </button>
          <button
            className={mode === "reset-confirm" ? "button primary" : "button secondary"}
            type="button"
            onClick={() => switchMode("reset-confirm")}
          >
            再設定確定
          </button>
        </div>

        {mode === "login" && (
          <form onSubmit={handleSubmit}>
            <div className="form-grid single">
              <div className="field">
                <label htmlFor="username">ユーザー名</label>
                <input
                  id="username"
                  className="input"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  autoComplete="username"
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="password">パスワード</label>
                <input
                  id="password"
                  className="input"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="mfa-code">MFAコード（有効な場合のみ）</label>
                <input
                  id="mfa-code"
                  className="input"
                  value={mfaCode}
                  onChange={(event) => setMfaCode(event.target.value)}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  placeholder="6桁コード"
                />
              </div>
            </div>
            <div className="button-row" style={{ marginTop: 14 }}>
              <button className="button primary" type="submit" disabled={isSubmitting}>
                {isSubmitting ? "認証中..." : "ログイン"}
              </button>
            </div>
          </form>
        )}

        {mode === "reset-request" && (
          <form onSubmit={handleRequestPasswordReset}>
            <div className="form-grid single">
              <div className="field">
                <label htmlFor="reset-request-username">再設定するユーザー名</label>
                <input
                  id="reset-request-username"
                  className="input"
                  value={resetRequestUsername}
                  onChange={(event) => setResetRequestUsername(event.target.value)}
                  autoComplete="username"
                  required
                />
              </div>
            </div>
            <div className="button-row" style={{ marginTop: 14 }}>
              <button className="button primary" type="submit" disabled={isSubmitting}>
                {isSubmitting ? "申請中..." : "再設定リンクを申請"}
              </button>
            </div>
          </form>
        )}

        {mode === "reset-confirm" && (
          <form onSubmit={handleConfirmPasswordReset}>
            <div className="form-grid single">
              <div className="field">
                <label htmlFor="reset-token">再設定トークン</label>
                <input
                  id="reset-token"
                  className="input"
                  value={resetToken}
                  onChange={(event) => setResetToken(event.target.value)}
                  placeholder="受け取ったトークンを入力"
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="new-password">新しいパスワード</label>
                <input
                  id="new-password"
                  className="input"
                  type="password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  autoComplete="new-password"
                  minLength={8}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="new-password-confirm">新しいパスワード（確認）</label>
                <input
                  id="new-password-confirm"
                  className="input"
                  type="password"
                  value={confirmNewPassword}
                  onChange={(event) => setConfirmNewPassword(event.target.value)}
                  autoComplete="new-password"
                  minLength={8}
                  required
                />
              </div>
            </div>
            <div className="button-row" style={{ marginTop: 14 }}>
              <button className="button primary" type="submit" disabled={isSubmitting}>
                {isSubmitting ? "更新中..." : "パスワードを更新"}
              </button>
            </div>
          </form>
        )}

        {error && <p className="inline-error">{error}</p>}
        {info && <p className="inline-info">{info}</p>}
      </div>
    </div>
  );
}
