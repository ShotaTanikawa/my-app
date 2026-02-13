"use client";

import { useAuth } from "@/components/auth-provider";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

export default function Home() {
  const { state, isHydrated } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isHydrated) {
      return;
    }

    if (state) {
      router.replace("/dashboard");
      return;
    }

    router.replace("/login");
  }, [isHydrated, router, state]);

  return <div className="page-message">移動しています...</div>;
}
