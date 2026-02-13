# Order and Inventory Management (Full Stack MVP)

受発注・在庫管理システムのフルスタックMVPです。

要件定義ドキュメント: `docs/requirements.md`
技術判断メモ: `docs/tech-decisions.md`
UI仕様: `docs/ui-spec.md`

- フロントエンド: Next.js 16 + TypeScript
- Spring Security + ロール制御（`ADMIN` / `OPERATOR` / `VIEWER`）
- `@Transactional` による在庫引当・確定・キャンセル
- `@Scheduled` による低在庫レポートジョブ
- Flywayマイグレーション
- PostgreSQL構成（Docker Compose）
- Actuator (`/actuator/health`, `/actuator/info`)

## 技術スタック

- Java 21
- Spring Boot 3.5
- Next.js 16
- React 19
- Spring Data JPA
- Spring Security (HTTP Basic)
- Flyway
- PostgreSQL

## セットアップ

### 1. DB起動

```bash
docker compose up -d postgres
```

### 2. バックエンド起動

```bash
cd backend
./mvnw spring-boot:run
```

### 3. フロントエンド起動

```bash
cd frontend
pnpm dev
```

ブラウザ:

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080`

フロントエンド環境変数（任意）:

- `NEXT_PUBLIC_API_BASE_URL`（デフォルト: `http://localhost:8080`）
- `NEXT_PUBLIC_LOW_STOCK_THRESHOLD`（ダッシュボード低在庫判定、デフォルト: `10`）

デフォルト接続先:

- DB URL: `jdbc:postgresql://localhost:5432/order_mgmt`
- USER: `app`
- PASSWORD: `app`

必要に応じて以下で上書きできます。

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `LOW_STOCK_THRESHOLD`（低在庫閾値）
- `LOW_STOCK_REPORT_CRON`（低在庫レポート実行cron）

## 初期ユーザー

`DataInitializer` で起動時に自動作成されます。

- `admin / admin123` (ADMIN)
- `operator / operator123` (OPERATOR)
- `viewer / viewer123` (VIEWER)

## フロントエンド画面

- `/login`
- `/dashboard`
- `/products`
- `/orders`
- `/orders/new`
- `/orders/[id]`

## E2Eテスト（Playwright）

前提:

- PostgreSQL, Backend が起動済み
- FrontendはPlaywrightが自動起動
- `E2E_BASE_URL` 未指定時は `http://localhost:3000` を使用

初回のみ:

```bash
cd frontend
pnpm exec playwright install chromium
```

実行:

```bash
cd frontend
pnpm test:e2e
```

テスト内容:

- ADMINで商品作成・在庫追加
- OPERATORで受注作成
- 受注の確定フロー
- 受注のキャンセルフロー

## CI（GitHub Actions）

ワークフロー:

- `.github/workflows/ci.yml`

実行ジョブ:

- Backend Test（`./mvnw test`）
- Frontend Lint & Build（`pnpm lint`, `pnpm build`）
- End-to-End Test（PostgreSQL起動 + Spring Boot起動 + `pnpm test:e2e`）

## API

### 認証確認

```bash
curl -u admin:admin123 http://localhost:8080/api/auth/me
```

### 商品作成（ADMIN）

```bash
curl -u admin:admin123 -X POST http://localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{
    "sku": "SKU-001",
    "name": "Wireless Mouse",
    "description": "Ergonomic model",
    "unitPrice": 2980.00
  }'
```

### 在庫追加（ADMIN/OPERATOR）

```bash
curl -u operator:operator123 -X POST http://localhost:8080/api/products/1/stock \
  -H 'Content-Type: application/json' \
  -d '{"quantity": 50}'
```

### 受注作成（ADMIN/OPERATOR）

```bash
curl -u operator:operator123 -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerName": "Acme Corp",
    "items": [
      {"productId": 1, "quantity": 3}
    ]
  }'
```

### 受注確定

```bash
curl -u operator:operator123 -X POST http://localhost:8080/api/orders/1/confirm
```

### 受注キャンセル

```bash
curl -u operator:operator123 -X POST http://localhost:8080/api/orders/1/cancel
```

## 今後の拡張候補

- JWT認証
- 監査ログ（誰がいつ在庫を動かしたか）
- CSV一括取込
- 日次バッチ（在庫レポート）
- UI/UX改善（検索・フィルタ・ページング）
