# Ops Runbook

## 1. Scope

このRunbookは以下を対象にする。

- 認証・セキュリティ運用（MFA、ログイン制限、IP制限、秘密情報ローテーション）
- 監視とアラート（API遅延、エラー率、DB接続数）
- バックアップ/リストア運用
- 障害時の一次対応

## 2. 監視項目

### 2.1 メトリクス

- `http.server.requests`:
  - API遅延（平均/分位）
  - エラー率（5xx比率）
- `hikaricp.connections.active`:
  - DBアクティブ接続数
- `app.api.request.duration`:
  - アプリ独自のAPI処理時間

### 2.2 ログ

- `ApiRequestLoggingFilter` が `X-Request-Id` を付与し、JSON形式でアクセスログを出力。
- 障害調査時は `requestId` をキーにAPIログと監査ログを突合する。

### 2.3 アラート

`AlertNotificationService` のしきい値設定例。

- API遅延: `APP_ALERTS_API_LATENCY_THRESHOLD_MS=1200`
- エラー率: `APP_ALERTS_API_ERROR_RATE_THRESHOLD=0.05`
- DB接続: `APP_ALERTS_DB_ACTIVE_CONNECTION_THRESHOLD=20`

通知先は環境変数で設定。

- Slack: `APP_ALERTS_SLACK_WEBHOOK_URL`
- Email: `APP_ALERTS_EMAIL_ENABLED`, `APP_ALERTS_EMAIL_TO`

## 3. 秘密情報ローテーション

対象。

- `APP_JWT_SECRET`（署名用）
- `APP_JWT_VERIFY_SECRETS`（旧鍵検証用、カンマ区切り）
- DB資格情報（`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`）

手順。

1. 新しい`APP_JWT_SECRET`を作成する。
2. 旧`APP_JWT_SECRET`を`APP_JWT_VERIFY_SECRETS`へ移動する。
3. デプロイしてトークンの新規発行が新鍵で行われることを確認する。
4. 既存トークン有効期限経過後、`APP_JWT_VERIFY_SECRETS`から旧鍵を削除する。

## 4. バックアップ/復元

## 4.1 日次バックアップ

- マネージドPostgreSQL（Render等）の自動バックアップを有効化する。
- バックアップ世代は最低7日以上を保持する。

## 4.2 復元リハーサル（月次）

1. 新規検証DBを作成する。
2. 最新バックアップから検証DBへ復元する。
3. APIを検証DBへ向けて起動し、以下を確認する。
   - ログイン可能
   - 商品/在庫/受注/入荷の主要データ整合性
   - Flywayマイグレーションが整合している
4. 復元時間（RTO）とデータ時点（RPO）を記録する。

## 4.3 障害時復旧手順

1. 障害宣言（影響範囲と開始時刻を記録）
2. 書き込み停止（必要時）
3. 直近正常バックアップを選定
4. DB復元
5. アプリを再デプロイ
6. スモークテスト（ログイン/受注作成/入荷/一覧表示）
7. サービス再開宣言
8. 事後レビュー（原因、恒久対策、再発防止）

## 5. 認証インシデント対応

### 5.1 不正ログイン試行増加

- `Too many failed login attempts` の発生件数を確認。
- 必要に応じて一時的に `APP_LOGIN_LOCK_SECONDS` を引き上げる。
- 影響ユーザーへパスワード再設定を案内する。

### 5.2 管理画面への異常アクセス

- `APP_ADMIN_ALLOWED_IPS` を見直し。
- 監査ログの `AUTH_*` とアクセスログを `requestId` で追跡。

## 6. 同時更新の整合性

- 受注/発注は `@Version` による楽観ロックで競合検知。
- 書き込み系APIは `Idempotency-Key` で重複実行を抑止。
- 競合時は `409 CONFLICT` を返し、クライアントは再読み込み後に再試行する。

## 7. 定期点検チェックリスト

- 週次: エラー率・遅延のしきい値見直し
- 月次: バックアップ復元リハーサル
- 月次: 秘密情報ローテーション計画レビュー
- 四半期: Runbookの更新と訓練
