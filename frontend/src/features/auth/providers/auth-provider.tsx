"use client";

import {
  getMe,
  login as loginApi,
  logout as logoutApi,
  refresh as refreshApi,
  type Credentials,
} from "@/lib/api";
import type { LoginResponse, MeResponse } from "@/types/api";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

type AuthState = {
  credentials: Credentials;
  refreshToken: string;
  expiresAt: number;
  user: MeResponse;
};

type AuthContextValue = {
  state: AuthState | null;
  isHydrated: boolean;
  login: (username: string, password: string, mfaCode?: string) => Promise<void>;
  logout: () => void;
  reloadMe: () => Promise<void>;
};

const STORAGE_KEY = "order_mgmt_auth";
const REFRESH_BUFFER_MS = 60_000;

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function toAuthState(auth: LoginResponse): AuthState {
  return {
    credentials: { accessToken: auth.accessToken },
    refreshToken: auth.refreshToken,
    expiresAt: Date.now() + auth.expiresIn * 1000,
    user: normalizeUser(auth.user),
  };
}

function normalizeUser(user: MeResponse): MeResponse {
  return {
    ...user,
    mfaEnabled: Boolean(user.mfaEnabled),
  };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState | null>(null);
  const [isHydrated, setIsHydrated] = useState(false);

  const persistState = useCallback((nextState: AuthState) => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(nextState));
    setState(nextState);
  }, []);

  const clearState = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY);
    setState(null);
  }, []);

  useEffect(() => {
    // リロード後もログイン状態を維持するため、sessionStorageから復元する。
    let active = true;

    async function hydrate() {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) {
        if (active) {
          setIsHydrated(true);
        }
        return;
      }

      try {
        const parsed = JSON.parse(raw) as Partial<AuthState>;
        if (
          !parsed.credentials?.accessToken ||
          !parsed.refreshToken ||
          typeof parsed.expiresAt !== "number" ||
          !parsed.user
        ) {
          throw new Error("Invalid auth state");
        }

        if (parsed.expiresAt - Date.now() <= REFRESH_BUFFER_MS) {
          const refreshed = await refreshApi(parsed.refreshToken);
          if (!active) {
            return;
          }

          persistState(toAuthState(refreshed));
        } else if (active) {
          setState({
            ...(parsed as AuthState),
            user: normalizeUser(parsed.user as MeResponse),
          });
        }
      } catch {
        sessionStorage.removeItem(STORAGE_KEY);
      } finally {
        if (active) {
          setIsHydrated(true);
        }
      }
    }

    void hydrate();
    return () => {
      active = false;
    };
  }, [persistState]);

  const login = useCallback(async (username: string, password: string, mfaCode?: string) => {
    // ログインAPIでJWTとRefresh Tokenを取得し、検証済みユーザー情報と保持する。
    const auth = await loginApi(username, password, mfaCode);
    persistState(toAuthState(auth));
  }, [persistState]);

  useEffect(() => {
    if (!state) {
      return;
    }

    const waitMs = Math.max(state.expiresAt - Date.now() - REFRESH_BUFFER_MS, 0);
    let active = true;
    const timer = window.setTimeout(async () => {
      try {
        const refreshed = await refreshApi(state.refreshToken);
        if (!active) {
          return;
        }

        persistState(toAuthState(refreshed));
      } catch {
        if (!active) {
          return;
        }
        // リフレッシュ失敗時はセッションを破棄して再ログインへ戻す。
        clearState();
      }
    }, waitMs);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [state, persistState, clearState]);

  const logout = useCallback(() => {
    const refreshToken = state?.refreshToken;
    if (refreshToken) {
      void logoutApi(refreshToken).catch(() => undefined);
    }
    clearState();
  }, [state, clearState]);

  const reloadMe = useCallback(async () => {
    if (!state) {
      return;
    }

    const me = await getMe(state.credentials);
    const nextState: AuthState = {
      ...state,
      user: normalizeUser(me),
    };
    persistState(nextState);
  }, [state, persistState]);

  const value = useMemo(
    () => ({
      state,
      isHydrated,
      login,
      logout,
      reloadMe,
    }),
    [state, isHydrated, login, logout, reloadMe],
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
