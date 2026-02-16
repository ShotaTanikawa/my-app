# 現行機能一覧（実装ベース）

最終更新日: 2026-02-16  
対象コミット帯: V1〜V1.2（SKU拡張・CSV取込・売上集計を含む）

## 1. システム概要

- 受注・在庫・仕入・監査・売上を一体運用するフルスタックWebアプリ
- Backend: Spring Boot 3.5 / Frontend: Next.js 16 + TypeScript
- 認証方式: JWT + Refresh Token（端末セッション管理あり）+ MFA(TOTP)
- 権限: `ADMIN` / `OPERATOR` / `VIEWER`

## 2. ロール権限マトリクス

| 機能領域 | ADMIN | OPERATOR | VIEWER |
| --- | --- | --- | --- |
| ログイン/リフレッシュ/ログアウト | 可 | 可 | 可 |
| パスワード再設定（申請/確定） | 可 | 可 | 可 |
| MFAセットアップ/有効化/無効化 | 可 | 可 | 可 |
| 自分のセッション一覧/失効 | 可 | 可 | 可 |
| 商品参照（一覧・詳細・検索） | 可 | 可 | 可 |
| 商品作成/更新 | 可 | 不可 | 不可 |
| 商品CSV一括取込 | 可 | 不可 | 不可 |
| SKU自動採番 | 可 | 不可 | 不可 |
| 商品カテゴリ作成/SKUルール更新 | 可 | 不可 | 不可 |
| 在庫追加 | 可 | 可 | 不可 |
| 受注作成/確定/キャンセル | 可 | 可 | 不可 |
| 仕入先作成/更新/有効無効 | 可 | 不可 | 不可 |
| 商品-仕入先契約（登録/解除） | 可 | 不可 | 不可 |
| 仕入発注参照 | 可 | 可 | 可 |
| 仕入発注作成/入荷/キャンセル | 可 | 可 | 不可 |
| 補充提案参照 | 可 | 可 | 可 |
| 売上レポート参照/CSV出力 | 可 | 可 | 可 |
| 監査ログ参照/CSV出力/クリーンアップ | 可 | 不可 | 不可 |

## 3. フロントエンド機能（画面）

| 画面 | ルート | 主な機能 |
| --- | --- | --- |
| ログイン | `/login` | 認証、失敗時エラー表示 |
| ダッシュボード | `/dashboard` | 商品数・受注数・低在庫件数・最近の受注 |
| セッション管理 | `/sessions` | 自分の端末セッション一覧、個別失効 |
| 商品管理 | `/products` | 商品検索（SKU/名称）、カテゴリ絞り込み、在庫注意フィルタ、ページング |
| 商品管理（管理系） | `/products` | 商品作成/更新、SKU自動採番、カテゴリ作成、カテゴリSKUルール更新、CSV取込 |
| 受注一覧 | `/orders` | 受注一覧参照 |
| 受注作成 | `/orders/new` | 顧客・明細入力、在庫引当付き受注作成 |
| 受注詳細 | `/orders/[id]` | 受注確定、受注キャンセル |
| 仕入発注一覧 | `/purchase-orders` | 発注一覧、補充提案一覧 |
| 仕入発注作成 | `/purchase-orders/new` | 提案取り込み、発注作成 |
| 仕入発注詳細 | `/purchase-orders/[id]` | 部分入荷/全量入荷、キャンセル、入荷履歴CSV出力 |
| 仕入先管理 | `/suppliers` | 仕入先CRUD、有効/無効、商品別契約条件管理 |
| 売上レポート | `/sales` | 集計表示、期間/粒度条件、CSV出力 |
| 監査ログ | `/audit-logs` | 条件検索、ページング、CSV出力、保持ポリシー削除 |

## 4. バックエンドAPI機能

### 4.1 認証・セッション

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/sessions`
- `DELETE /api/auth/sessions/{sessionId}`
- `POST /api/auth/password-reset/request`
- `POST /api/auth/password-reset/confirm`
- `POST /api/auth/mfa/setup`
- `POST /api/auth/mfa/enable`
- `POST /api/auth/mfa/disable`

### 4.2 商品・カテゴリ・在庫

- `GET /api/products`
- `GET /api/products/page`（`q`, `categoryId`, `lowStockOnly`, `page`, `size`）
- `GET /api/products/{productId}`
- `POST /api/products`
- `PUT /api/products/{productId}`
- `POST /api/products/{productId}/stock`
- `GET /api/products/sku/next`（カテゴリルール対応）
- `POST /api/products/import`（CSV一括取込）
- `GET /api/product-categories`
- `POST /api/product-categories`
- `PUT /api/product-categories/{categoryId}/sku-rule`

### 4.3 受注

- `GET /api/orders`
- `GET /api/orders/{orderId}`
- `POST /api/orders`
- `POST /api/orders/{orderId}/confirm`
- `POST /api/orders/{orderId}/cancel`

### 4.4 仕入先・契約

- `GET /api/suppliers`
- `GET /api/suppliers/{supplierId}`
- `POST /api/suppliers`
- `PUT /api/suppliers/{supplierId}`
- `POST /api/suppliers/{supplierId}/activate`
- `POST /api/suppliers/{supplierId}/deactivate`
- `GET /api/products/{productId}/suppliers`
- `POST /api/products/{productId}/suppliers`
- `DELETE /api/products/{productId}/suppliers/{supplierId}`

### 4.5 仕入発注・補充提案

- `GET /api/purchase-orders`
- `GET /api/purchase-orders/{purchaseOrderId}`
- `GET /api/purchase-orders/suggestions`
- `POST /api/purchase-orders`
- `POST /api/purchase-orders/{purchaseOrderId}/receive`（部分入荷対応）
- `POST /api/purchase-orders/{purchaseOrderId}/cancel`
- `GET /api/purchase-orders/{purchaseOrderId}/receipts`
- `GET /api/purchase-orders/{purchaseOrderId}/receipts/export.csv`

### 4.6 売上レポート

- `GET /api/sales`（`from`, `to`, `groupBy`, `lineLimit`）
- `GET /api/sales/export.csv`

### 4.7 監査ログ

- `GET /api/audit-logs`（`page`, `size`, `action`, `actor`, `from`, `to`）
- `GET /api/audit-logs/export.csv`
- `POST /api/audit-logs/cleanup`

## 5. 業務フロー実装状況

### 5.1 受注フロー（在庫引当）

1. 受注作成時に `available -> reserved` へ引当  
2. 受注確定時に `reserved` を消し込み  
3. 受注キャンセル時に `reserved -> available` を戻し  
4. 不正遷移や在庫不足は業務エラー（409）で拒否

### 5.2 仕入発注フロー（補充）

1. 在庫・契約条件（MOQ/ロット/主仕入先）から補充提案算出  
2. 仕入発注作成  
3. 部分入荷/全量入荷で在庫加算  
4. 入荷履歴を検索/CSV出力可能

### 5.3 SKU運用フロー

1. カテゴリごとにSKUルール（`skuPrefix`, `skuSequenceDigits`）を管理  
2. SKU自動採番APIで `PREFIX-YYMMDD-連番` 形式の次候補を返却  
3. 商品作成時/CSV取込時にSKUを大文字化・形式検証  
4. SKU重複は大文字小文字を無視して検知

### 5.4 監査・運用フロー

1. 主要ドメイン操作を監査ログへ記録  
2. 監査ログを条件検索・CSV出力  
3. 手動クリーンアップ + 定期保持ポリシー削除に対応

### 5.5 セキュリティ強化フロー

1. ログイン試行回数制限（閾値超過で一時ロック）  
2. MFA有効ユーザーはログイン時にTOTP必須  
3. 管理者アクセスはIP制限（`APP_ADMIN_ALLOWED_IPS`）  
4. パスワード再設定時は既存セッションを失効  
5. JWT署名鍵ローテーション（`APP_JWT_VERIFY_SECRETS`）対応

### 5.6 同時更新整合性フロー

1. 受注/仕入発注は`@Version`で楽観ロック  
2. 書き込みAPIは`Idempotency-Key`で重複実行抑止  
3. 競合時は`409`を返却し再読込リトライ可能

## 6. バッチ・定期処理

- 低在庫レポートジョブ（`jobs.low-stock-report-cron`）
- Refresh Tokenクリーンアップ（`jobs.refresh-token-cleanup-cron`）
- 監査ログ保持削除ジョブ（`jobs.audit-log-retention-cron`）
- Idempotencyキー期限切れクリーンアップ（`jobs.idempotency-cleanup-cron`）
- パスワード再設定トークンクリーンアップ（`jobs.password-reset-token-cleanup-cron`）
- API遅延/エラー率/DB接続数アラート監視（`jobs.ops-metrics-alert-interval-ms`）

## 7. データ・マイグレーション

- Flywayバージョン: `V1`〜`V12` 適用済み
- 主な拡張:
  - 認証セッション管理
  - 監査ログ
  - 仕入発注/部分入荷/入荷履歴
  - 仕入先/商品契約
  - 商品カテゴリ/検索インデックス
  - カテゴリSKUルール

## 8. 品質保証・CI/CD

- Backend Test: `./mvnw test`
- Frontend Lint/Build: `pnpm lint`, `pnpm build`
- E2E: Playwrightで受注主要フローと監査ログ連携を検証
- GitHub Actionsで上記を自動実行
- デプロイ:
  - Backend: Render
  - Frontend: Vercel

## 9. 既知の運用注意

- Vercelは `Root Directory=frontend` の設定が必須
- CSV取込はUTF-8前提
- 監査ログCSV/入荷履歴CSVは取得件数上限パラメータで制御
