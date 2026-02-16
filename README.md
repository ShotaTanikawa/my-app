# Order and Inventory Management (Full Stack MVP)

受発注・在庫管理システムのフルスタックMVPです。

要件定義ドキュメント: `docs/requirements.md`
技術判断メモ: `docs/tech-decisions.md`
UI仕様: `docs/ui-spec.md`
デプロイ手順: `docs/deploy.md`
V1.1ロードマップ: `docs/v1.1-roadmap.md`

- フロントエンド: Next.js 16 + TypeScript
- Spring Security + ロール制御（`ADMIN` / `OPERATOR` / `VIEWER`）
- `@Transactional` による在庫引当・確定・キャンセル
- `@Scheduled` による低在庫レポートジョブ
- 監査ログCSV出力・保持ポリシー（手動クリーンアップ/定期削除）
- Flywayマイグレーション
- PostgreSQL構成（Docker Compose）
- Actuator (`/actuator/health`, `/actuator/info`)

## 技術スタック

- Java 21
- Spring Boot 3.5
- Next.js 16
- React 19
- Spring Data JPA
- Spring Security (JWT Bearer)
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
- `APP_JWT_SECRET`（JWT署名シークレット）
- `APP_JWT_EXPIRATION_SECONDS`（JWT有効期限秒）
- `APP_JWT_REFRESH_EXPIRATION_SECONDS`（Refresh Token有効期限秒）
- `REFRESH_TOKEN_CLEANUP_CRON`（Refresh Tokenクリーンアップcron）
- `AUDIT_LOG_RETENTION_ENABLED`（監査ログ定期クリーンアップ有効/無効）
- `AUDIT_LOG_RETENTION_DAYS`（監査ログ保持日数）
- `AUDIT_LOG_RETENTION_CRON`（監査ログ定期クリーンアップcron）
- `APP_SEED_ENABLED`（初期ユーザー自動作成フラグ）

## 初期ユーザー

`DataInitializer` で起動時に自動作成されます。

- `admin / admin123` (ADMIN)
- `operator / operator123` (OPERATOR)
- `viewer / viewer123` (VIEWER)

## フロントエンド画面

- `/login`
- `/dashboard`
- `/sessions`
- `/products`
- `/orders`
- `/orders/new`
- `/orders/[id]`
- `/audit-logs`（ADMIN）

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
- 監査ログ画面で操作履歴を検証

## CI（GitHub Actions）

ワークフロー:

- `.github/workflows/ci.yml`

実行ジョブ:

- Backend Test（`./mvnw test`）
- Frontend Lint & Build（`pnpm lint`, `pnpm build`）
- End-to-End Test（PostgreSQL起動 + Spring Boot起動 + `pnpm test:e2e`）

## API

### ログインしてトークン取得

```bash
LOGIN_JSON=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}')

TOKEN=$(echo "${LOGIN_JSON}" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "${LOGIN_JSON}" | jq -r '.refreshToken')
```

### 商品作成（ADMIN）

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer ${TOKEN}" \
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
OP_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"operator","password":"operator123"}' | jq -r '.accessToken')

curl -X POST http://localhost:8080/api/products/1/stock \
  -H "Authorization: Bearer ${OP_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"quantity": 50}'
```

### 受注作成（ADMIN/OPERATOR）

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer ${OP_TOKEN}" \
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
curl -X POST http://localhost:8080/api/orders/1/confirm \
  -H "Authorization: Bearer ${OP_TOKEN}"
```

### 受注キャンセル

```bash
curl -X POST http://localhost:8080/api/orders/1/cancel \
  -H "Authorization: Bearer ${OP_TOKEN}"
```

### アクセストークン更新

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"${REFRESH_TOKEN}\"}"
```

### ログアウト（Refresh Token失効）

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"${REFRESH_TOKEN}\"}"
```

### セッション一覧取得（端末管理）

```bash
curl -X GET http://localhost:8080/api/auth/sessions \
  -H "Authorization: Bearer ${TOKEN}"
```

### セッション失効（端末単位）

```bash
curl -X DELETE http://localhost:8080/api/auth/sessions/<sessionId> \
  -H "Authorization: Bearer ${TOKEN}"
```

### 監査ログ取得（ADMIN）

```bash
curl -X GET "http://localhost:8080/api/audit-logs?page=0&size=50&action=ORDER_CREATE&actor=operator" \
  -H "Authorization: Bearer ${TOKEN}"
```

### 監査ログCSV出力（ADMIN）

```bash
curl -X GET "http://localhost:8080/api/audit-logs/export.csv?action=ORDER_CREATE&actor=operator&limit=1000" \
  -H "Authorization: Bearer ${TOKEN}" \
  -o audit-logs.csv
```

### 監査ログクリーンアップ（ADMIN）

```bash
curl -X POST "http://localhost:8080/api/audit-logs/cleanup?retentionDays=90" \
  -H "Authorization: Bearer ${TOKEN}"
```

## 今後の拡張候補

- 監査ログ条件のプリセット保存
- CSV一括取込
- 日次バッチ（在庫レポート）
- UI/UX改善（監査ログ条件の保存・共有）
