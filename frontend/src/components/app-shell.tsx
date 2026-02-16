"use client";

import { useAuth } from "@/components/auth-provider";
import Link from "next/link";
import { usePathname } from "next/navigation";

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { state, logout } = useAuth();
  // 権限に応じてナビゲーション項目を出し分ける。
  const navItems = [
    { href: "/dashboard", label: "Dashboard" },
    { href: "/sessions", label: "Sessions" },
    { href: "/products", label: "Products" },
    { href: "/orders", label: "Orders" },
    ...(state?.user.role === "ADMIN" ? [{ href: "/audit-logs", label: "Audit Logs" }] : []),
  ];

  if (pathname === "/login") {
    // ログイン画面では共通シェルを表示しない。
    return <>{children}</>;
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <h1 className="brand">FlowStock</h1>
        <p className="brand-subtitle">Order & Inventory</p>
        <nav>
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={pathname.startsWith(item.href) ? "nav-link active" : "nav-link"}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </aside>
      <main className="main">
        <header className="header">
          <div>
            <div className="header-title">Operational Console</div>
            <div className="header-subtitle">在庫・受注を一元管理</div>
          </div>
          <div className="header-actions">
            <span className="role-chip">{state?.user.role ?? "GUEST"}</span>
            <span className="username-chip">{state?.user.username ?? "-"}</span>
            <button onClick={logout} className="ghost-button" type="button">
              Logout
            </button>
          </div>
        </header>
        <section className="content">{children}</section>
      </main>
    </div>
  );
}
