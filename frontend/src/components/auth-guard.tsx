"use client";

import { useAuth } from "@/components/auth-provider";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { state, isHydrated } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (!isHydrated) {
      return;
    }

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
