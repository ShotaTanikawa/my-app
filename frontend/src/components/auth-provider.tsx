"use client";

import { getMe, type Credentials } from "@/lib/api";
import type { MeResponse } from "@/types/api";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

type AuthState = {
  credentials: Credentials;
  user: MeResponse;
};

type AuthContextValue = {
  state: AuthState | null;
  isHydrated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
};

const STORAGE_KEY = "order_mgmt_auth";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState | null>(null);
  const [isHydrated, setIsHydrated] = useState(false);

  useEffect(() => {
    // リロード後もログイン状態を維持するため、sessionStorageから復元する。
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      setIsHydrated(true);
      return;
    }

    try {
      const parsed = JSON.parse(raw) as AuthState;
      setState(parsed);
    } catch {
      sessionStorage.removeItem(STORAGE_KEY);
    } finally {
      setIsHydrated(true);
    }
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const credentials = { username, password };
    // 保存前にバックエンドで資格情報を検証し、正しい認証情報のみ保持する。
    const user = await getMe(credentials);
    const nextState = { credentials, user };

    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(nextState));
    setState(nextState);
  }, []);

  const logout = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY);
    setState(null);
  }, []);

  const value = useMemo(
    () => ({
      state,
      isHydrated,
      login,
      logout,
    }),
    [state, isHydrated, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }

  return context;
}
