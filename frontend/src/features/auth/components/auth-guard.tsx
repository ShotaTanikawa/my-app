"use client";

import { useAuth } from "@/features/auth";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { state, isHydrated } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    // 復元完了後にのみガード判定を実行する。
    if (!isHydrated) {
      return;
    }

    // 非ログイン状態で保護ページに来た場合はログインへ送る。
    if (!state && pathname !== "/login") {
      router.replace("/login");
    }
  }, [isHydrated, state, pathname, router]);

  if (!isHydrated) {
    return <div className="page-message">認証状態を確認しています...</div>;
  }

  if (!state && pathname !== "/login") {
    return <div className="page-message">ログイン画面へ移動します...</div>;
  }

  return <>{children}</>;
}
