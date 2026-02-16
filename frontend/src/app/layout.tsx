import type { Metadata } from "next";
import { Space_Grotesk, Manrope } from "next/font/google";
import "./globals.css";
import { AppShell } from "@/components/app-shell";
import { AuthGuard } from "@/components/auth-guard";
import { AuthProvider } from "@/components/auth-provider";

// 見出し用フォントをCSS変数として登録する。
const headingFont = Space_Grotesk({
  variable: "--font-heading",
  subsets: ["latin"],
});

// 本文用フォントをCSS変数として登録する。
const bodyFont = Manrope({
  variable: "--font-body",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "FlowStock",
  description: "Order and Inventory Management",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ja">
      <body className={`${headingFont.variable} ${bodyFont.variable}`}>
        {/* 認証状態を配下に共有する。 */}
        <AuthProvider>
          {/* 未認証時は画面遷移を制御する。 */}
          <AuthGuard>
            {/* ログイン画面以外の共通レイアウトを描画する。 */}
            <AppShell>{children}</AppShell>
          </AuthGuard>
        </AuthProvider>
      </body>
    </html>
  );
}
