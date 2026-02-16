# デプロイ手順（Render + Vercel）

最終更新日: 2026-02-16

## 1. 構成

- Backend: Render Web Service（`/backend` を Docker build）
- Database: Render PostgreSQL
- Frontend: Vercel（`/frontend`）

## 2. Backend（Render）

まず `render.yaml` を使ったBlueprintデプロイを推奨します。

### 2.0 Blueprintで作成（推奨）

- Renderで `New +` -> `Blueprint`
- このリポジトリを選択すると `render.yaml` が読まれ、PostgreSQL + Backend が同時作成される
- 作成後に `CORS_ALLOWED_ORIGINS` だけVercelのURLへ更新する

### 2.1 Web Service作成

- Renderで `New +` -> `Web Service`
- リポジトリを接続
- `Root Directory`: `backend`
- `Runtime`: `Docker`
- `Health Check Path`: `/actuator/health`

`backend/Dockerfile` をそのまま利用できます。

### 2.2 環境変数

最低限:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_JWT_SECRET`（32文字以上）
- `CORS_ALLOWED_ORIGINS`（例: `https://your-app.vercel.app`）

推奨:

- `APP_JWT_EXPIRATION_SECONDS=900`（15分）
- `APP_JWT_REFRESH_EXPIRATION_SECONDS=1209600`（14日）
- `APP_SEED_ENABLED=false`（本番で初期ユーザー自動投入を止める）
- `LOW_STOCK_THRESHOLD=10`
- `LOW_STOCK_REPORT_CRON=0 0 1 * * *`
- `REFRESH_TOKEN_CLEANUP_CRON=0 0 * * * *`
- `AUDIT_LOG_RETENTION_ENABLED=true`
- `AUDIT_LOG_RETENTION_DAYS=90`
- `AUDIT_LOG_RETENTION_CRON=0 30 2 * * *`

## 3. Database（Render PostgreSQL）

- Renderで `New +` -> `PostgreSQL`
- DB作成後、接続情報をBackendの `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` に設定
- 初回起動時にFlywayが `V1` 〜 `V4` を自動適用

## 4. Frontend（Vercel）

### 4.1 Project作成

- Vercelで `New Project`
- 同じGitHubリポジトリを選択
- `Root Directory`: `frontend`

### 4.2 環境変数

- `NEXT_PUBLIC_API_BASE_URL=https://<render-backend-domain>`
- `NEXT_PUBLIC_LOW_STOCK_THRESHOLD=10`（任意）

CLIで設定する場合:

```bash
cd frontend
pnpm dlx vercel env add NEXT_PUBLIC_API_BASE_URL production
pnpm dlx vercel env add NEXT_PUBLIC_LOW_STOCK_THRESHOLD production
```

## 5. デプロイ後チェック

### 5.1 Backend疎通

```bash
curl -s https://<render-backend-domain>/actuator/health
```

### 5.2 ログインAPI

```bash
curl -s -X POST https://<render-backend-domain>/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

`APP_SEED_ENABLED=false` の場合は、初期ユーザーを別途投入してください。

## 6. 運用メモ

- CORSエラーが出る場合は `CORS_ALLOWED_ORIGINS` にVercelの実URLを正確に設定
- JWTシークレットは必ずランダムで長い値を使用
- ログイン/リフレッシュ失敗が増えたらBackendログの `401` を先に確認
