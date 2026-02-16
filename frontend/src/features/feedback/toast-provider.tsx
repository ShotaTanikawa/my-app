"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

type ToastKind = "success" | "error" | "info";

type ToastItem = {
  id: number;
  kind: ToastKind;
  message: string;
};

type ToastContextValue = {
  showSuccess: (message: string) => void;
  showError: (message: string) => void;
  showInfo: (message: string) => void;
};

const TOAST_DURATION_MS = 3200;
const MAX_TOASTS = 4;

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const timersRef = useRef<Record<number, number>>({});
  const nextToastIdRef = useRef(1);

  const dismiss = useCallback((toastId: number) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== toastId));
    const timer = timersRef.current[toastId];
    if (timer != null) {
      window.clearTimeout(timer);
      delete timersRef.current[toastId];
    }
  }, []);

  const pushToast = useCallback(
    (kind: ToastKind, message: string) => {
      const toastId = nextToastIdRef.current++;

      setToasts((prev) => [...prev, { id: toastId, kind, message }].slice(-MAX_TOASTS));

      timersRef.current[toastId] = window.setTimeout(() => {
        setToasts((prev) => prev.filter((toast) => toast.id !== toastId));
        delete timersRef.current[toastId];
      }, TOAST_DURATION_MS);
    },
    [],
  );

  useEffect(() => {
    return () => {
      // 画面遷移中に残ったtimerをクリーンアップする。
      for (const timer of Object.values(timersRef.current)) {
        window.clearTimeout(timer);
      }
      timersRef.current = {};
    };
  }, []);

  const value = useMemo<ToastContextValue>(
    () => ({
      showSuccess: (message) => pushToast("success", message),
      showError: (message) => pushToast("error", message),
      showInfo: (message) => pushToast("info", message),
    }),
    [pushToast],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-region" aria-live="polite" aria-atomic="true">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`toast-item ${toast.kind}`}
            role={toast.kind === "error" ? "alert" : "status"}
          >
            <span>{toast.message}</span>
            <button
              type="button"
              className="toast-close"
              onClick={() => dismiss(toast.id)}
              aria-label="通知を閉じる"
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return context;
}
