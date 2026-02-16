# プロジェクト構成ガイド

## 1. ルート構成

```text
my-app/
├── backend/               # Spring Boot API
├── frontend/              # Next.js Web UI
├── docs/                  # 要件/運用/設計ドキュメント
├── docker-compose.yml     # ローカルPostgreSQL起動
└── render.yaml            # Renderデプロイ設定
```

## 2. Backend構成

```text
backend/src/main/java/com/example/backend/
├── audit/                 # 監査ログ
├── common/                # 共通例外/エラーレスポンス
├── config/                # Security/初期化設定
├── idempotency/           # 冪等キー処理
├── inventory/             # 在庫ドメイン
├── jobs/                  # スケジュールジョブ
├── ops/                   # 運用通知/監視補助
├── order/                 # 受注ドメイン
├── product/               # 商品/カテゴリドメイン
├── purchase/              # 仕入発注ドメイン
├── sales/                 # 売上集計ドメイン
├── security/              # JWT/MFA/認証関連
├── supplier/              # 仕入先/契約ドメイン
└── user/                  # ユーザー/認証API
```

- DBマイグレーションは `backend/src/main/resources/db/migration/`
- テストは `backend/src/test/java/`

## 3. Frontend構成

```text
frontend/src/
├── app/                   # Next.js App Router（画面）
├── components/
│   └── layout/            # レイアウト共通UI
├── features/
│   ├── auth/              # 認証（Provider/Guard）
│   ├── category/          # カテゴリツリー処理
│   └── feedback/          # Toast通知
├── lib/                   # APIクライアント/汎用関数
└── types/                 # API型定義
```

### 方針

- 画面固有ロジックは `app/`
- 業務機能単位の再利用ロジックは `features/`
- 機能非依存の共通処理は `lib/`

## 4. 今後の追加ルール

- 新しい業務機能はまず `frontend/src/features/<feature-name>/` に配置する
- `app/` にはページコンポーネントとページ専用UIのみ置く
- backendはドメイン単位パッケージ配下に `dto` / `service` / `controller` をまとめる
