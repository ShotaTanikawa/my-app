"use client";

import { useAuth } from "@/features/auth";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

export default function Home() {
  const { state, isHydrated } = useAuth();
  const router = useRouter();

  useEffect(() => {
    // 復元処理中は遷移先を確定させない。
    if (!isHydrated) {
      return;
    }

    // 認証状態の有無で初期遷移先を切り替える。
    if (state) {
      router.replace("/dashboard");
      return;
    }

    router.replace("/login");
  }, [isHydrated, router, state]);

  return <div className="page-message">移動しています...</div>;
}
