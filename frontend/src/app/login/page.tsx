"use client";

import { useAuth } from "@/components/auth-provider";
import { ApiClientError } from "@/lib/api";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";

export default function LoginPage() {
  const { login, state, isHydrated } = useAuth();
  const router = useRouter();

  const [username, setUsername] = useState("operator");
  const [password, setPassword] = useState("operator123");
  const [error, setError] = useState("");
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

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setIsSubmitting(true);

    try {
      // 認証成功時にAuthProvider側へトークンを保存する。
      await login(username, password);
      router.replace("/dashboard");
    } catch (err) {
      if (err instanceof ApiClientError) {
        setError(err.message || "ログインに失敗しました。");
      } else {
        setError("ログインに失敗しました。");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1 className="login-title">FlowStock</h1>
        <p className="login-subtitle">受発注・在庫管理へログイン</p>
        <div className="login-helper">
          <div className="login-helper-title">学習用アカウント</div>
          <p className="login-helper-item">担当者: `operator / operator123`</p>
          <p className="login-helper-item">管理者: `admin / admin123`</p>
        </div>
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
        </div>
        {error && <p className="inline-error">{error}</p>}
        <div className="button-row" style={{ marginTop: 14 }}>
          <button className="button primary" type="submit" disabled={isSubmitting}>
            {isSubmitting ? "認証中..." : "ログイン"}
          </button>
        </div>
      </form>
    </div>
  );
}
