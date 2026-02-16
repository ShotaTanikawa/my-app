"use client";

import { useAuth } from "@/components/auth-provider";
import { formatUserRole } from "@/lib/format";
import Link from "next/link";
import { usePathname } from "next/navigation";

type NavItem = {
  href: string;
  label: string;
  description: string;
  roles?: Array<"ADMIN" | "OPERATOR" | "VIEWER">;
};

type HeaderAction = {
  href: string;
  label: string;
  roles?: Array<"ADMIN" | "OPERATOR" | "VIEWER">;
};

type PageMeta = {
  title: string;
  description: string;
  primaryAction?: HeaderAction;
};

const navItems: NavItem[] = [
  {
    href: "/dashboard",
    label: "ダッシュボード",
    description: "全体状況と優先タスクを確認",
  },
  {
    href: "/sales",
    label: "売上管理",
    description: "売上集計と明細の分析",
  },
  {
    href: "/orders",
    label: "受注管理",
    description: "受注作成と確定処理",
  },
  {
    href: "/purchase-orders",
    label: "仕入発注",
    description: "発注と入荷の進捗管理",
  },
  {
    href: "/products",
    label: "商品・在庫",
    description: "商品マスタと在庫数量の管理",
  },
  {
    href: "/suppliers",
    label: "仕入先",
    description: "仕入先マスタと契約条件",
  },
  {
    href: "/sessions",
    label: "セッション",
    description: "ログイン端末の失効管理",
  },
  {
    href: "/audit-logs",
    label: "監査ログ",
    description: "操作履歴の監査とCSV出力",
    roles: ["ADMIN"],
  },
];

function canUseByRole(
  role: "ADMIN" | "OPERATOR" | "VIEWER" | undefined,
  roles?: Array<"ADMIN" | "OPERATOR" | "VIEWER">,
): boolean {
  if (!roles) {
    return true;
  }
  if (!role) {
    return false;
  }
  return roles.includes(role);
}

function getPageMeta(pathname: string): PageMeta {
  if (pathname === "/dashboard") {
    return {
      title: "ダッシュボード",
      description: "今日の在庫・受注・仕入の状況をまとめて確認します。",
      primaryAction: { href: "/orders/new", label: "新規受注を登録", roles: ["ADMIN", "OPERATOR"] },
    };
  }
  if (pathname === "/orders") {
    return {
      title: "受注一覧",
      description: "受注の作成・確定・キャンセルを行います。",
      primaryAction: { href: "/orders/new", label: "新規受注", roles: ["ADMIN", "OPERATOR"] },
    };
  }
  if (pathname === "/sales") {
    return {
      title: "売上管理",
      description: "期間指定で売上サマリー・推移・明細を確認します。",
      primaryAction: { href: "/orders/new", label: "新規受注", roles: ["ADMIN", "OPERATOR"] },
    };
  }
  if (pathname === "/orders/new") {
    return {
      title: "新規受注作成",
      description: "顧客名と明細を入力して受注を登録します。",
      primaryAction: { href: "/orders", label: "受注一覧へ戻る" },
    };
  }
  if (pathname.startsWith("/orders/")) {
    return {
      title: "受注詳細",
      description: "明細確認と受注ステータスの変更を行います。",
      primaryAction: { href: "/orders", label: "受注一覧へ" },
    };
  }
  if (pathname === "/purchase-orders") {
    return {
      title: "仕入発注一覧",
      description: "発注作成、入荷進捗、補充提案を確認します。",
      primaryAction: {
        href: "/purchase-orders/new",
        label: "新規仕入発注",
        roles: ["ADMIN", "OPERATOR"],
      },
    };
  }
  if (pathname === "/purchase-orders/new") {
    return {
      title: "新規仕入発注",
      description: "仕入先と明細を入力して発注します。",
      primaryAction: { href: "/purchase-orders", label: "仕入発注一覧へ戻る" },
    };
  }
  if (pathname.startsWith("/purchase-orders/")) {
    return {
      title: "仕入発注詳細",
      description: "入荷登録、履歴確認、CSV出力を行います。",
      primaryAction: { href: "/purchase-orders", label: "仕入発注一覧へ" },
    };
  }
  if (pathname === "/products") {
    return {
      title: "商品・在庫管理",
      description: "商品マスタの編集と在庫調整を行います。",
    };
  }
  if (pathname === "/suppliers") {
    return {
      title: "仕入先管理",
      description: "仕入先マスタと契約条件を管理します。",
    };
  }
  if (pathname === "/sessions") {
    return {
      title: "ログインセッション",
      description: "不要なセッションを失効して安全性を保ちます。",
    };
  }
  if (pathname === "/audit-logs") {
    return {
      title: "監査ログ",
      description: "操作履歴の検索・CSV出力・クリーンアップを行います。",
    };
  }
  return {
    title: "FlowStock",
    description: "受発注と在庫運用を一元管理します。",
  };
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { state, logout } = useAuth();
  const role = state?.user.role;
  const pageMeta = getPageMeta(pathname);
  const allowedNavItems = navItems.filter((item) => canUseByRole(role, item.roles));
  const primaryAction = pageMeta.primaryAction;

  if (pathname === "/login") {
    // ログイン画面では共通シェルを表示しない。
    return <>{children}</>;
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <h1 className="brand">FlowStock</h1>
        <p className="brand-subtitle">受発注・在庫オペレーション</p>
        <div className="sidebar-section-title">業務メニュー</div>
        <nav>
          {allowedNavItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={pathname.startsWith(item.href) ? "nav-link active" : "nav-link"}
            >
              <span className="nav-label">{item.label}</span>
              <span className="nav-caption">{item.description}</span>
            </Link>
          ))}
        </nav>
      </aside>
      <main className="main">
        <header className="header">
          <div>
            <div className="header-breadcrumb">FlowStock / {pageMeta.title}</div>
            <div className="header-title">{pageMeta.title}</div>
            <div className="header-subtitle">{pageMeta.description}</div>
          </div>
          <div className="header-actions">
            {primaryAction && canUseByRole(role, primaryAction.roles) && (
              <Link href={primaryAction.href} className="button secondary header-action-link">
                {primaryAction.label}
              </Link>
            )}
            <span className="role-chip">
              {role ? `${formatUserRole(role)} (${role})` : "ゲスト"}
            </span>
            <span className="username-chip">{state?.user.username ?? "-"}</span>
            <button onClick={logout} className="ghost-button" type="button">
              ログアウト
            </button>
          </div>
        </header>
        <section className="content">{children}</section>
      </main>
    </div>
  );
}
