# 技術判断メモ（MVP）

最終更新日: 2026-02-16

## 1. 認証方式: HTTP Basic と JWT の違い

## 1.1 HTTP Basic

概要:

- `Authorization: Basic base64(username:password)` を毎リクエスト送る方式

利点:

- 実装が最小で速い
- Spring Securityで即導入可能
- 検証しやすい（curlでも扱いやすい）

注意点:

- 毎回ユーザー名/パスワード相当情報を送る
- ブラウザフロント実装時に扱いづらい（ログアウト制御やUX）
- 本番ではHTTPS必須

向いている段階:

- MVP初期・学習初期・管理者が少数の内部利用

## 1.2 JWT

概要:

- ログイン時にトークンを発行し、以降は `Bearer <token>` を送る方式

利点:

- フロントエンドとの相性がよい
- ロールやユーザー情報をトークンに含められる
- APIを分離した構成にしやすい

注意点:

- 実装コストが上がる（発行・検証・有効期限・リフレッシュ）
- 失効制御を設計しないとセキュリティ運用が難しい

向いている段階:

- 画面運用が本格化した後、または外部公開を見据える段階

## 1.3 このプロジェクトでの採用

- 現在: `JWT + Refresh Token`
- V1.1: 監査ログ検索/ページング + CSVエクスポート + 端末単位失効を導入済み
- 次フェーズ: 失効イベント通知・保持ポリシー・不審セッション検知へ拡張

理由:

- フロントエンドとの認証連携をシンプルにしつつ、Spring Securityの構成を実践的に学べるため
- 将来の外部公開やサービス分離に備えた形へ先に寄せるため

## 2. デプロイ先: Render と Railway

## 2.1 Render

利点:

- WebサービスとPostgreSQLを素直に構成できる
- 設定UIがわかりやすく、初学者向け
- ヘルスチェック運用がしやすい

注意点:

- プラン次第でスリープや起動時間の制約あり

## 2.2 Railway

利点:

- 初期セットアップが速い
- DBや環境変数の管理がしやすい
- 開発初期の検証用途に向く

注意点:

- 料金・リソース制限の把握が必要
- 運用設計はRenderより自分で意識する場面が増える

## 2.3 このプロジェクトでの採用

- 第一候補: `Render`
- 代替候補: `Railway`

理由:

- MVPで必要な要素（Spring Boot API + PostgreSQL + ヘルスチェック）が素直に載る
- 学習と運用のバランスがよい

## 3. デプロイ前提の最小構成

- Backend: Spring Boot (`/backend`)
- DB: PostgreSQL
- 環境変数:
  - `DB_URL`
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `LOW_STOCK_THRESHOLD`
  - `LOW_STOCK_REPORT_CRON`
  - `APP_JWT_SECRET`
  - `APP_JWT_EXPIRATION_SECONDS`
  - `APP_JWT_REFRESH_EXPIRATION_SECONDS`
  - `REFRESH_TOKEN_CLEANUP_CRON`
  - `APP_SEED_ENABLED`
