# Claude用IAMユーザー（claude-cli-mmm）のセットアップ

Claude Code（このリポジトリで作業するAIアシスタント）が、`MoneyManagerOcrFunction`のCloudWatch LogsとLambda本体をAWS CLI経由で直接参照・呼び出しできるようにするための、専用の最小権限IAMユーザー。

## 目的・背景

Lambdaの実運用ログ（ユーザーが実際にAndroidアプリから送信したOCRテキスト）と、家計簿アプリ（`tomo3110.sakura.ne.jp`）側の入力履歴を突き合わせて、実データに基づくLambdaのテストケース（[`lambda/test_cases/test_cases.json`](../lambda/test_cases/test_cases.json)）を作成する作業のために用意した。

当初はブラウザ操作（Lambdaコンソール・CloudWatchコンソール）のみで進めていたが、

- CloudWatch Logsの検索結果はコンソール上で長い行が途中で見切れ、スクリーンショット越しの手入力では正確に読み取れない
- クリップボードのコピー／ブラウザのファイルダウンロードは、自動操作の安全装置でブロックされる
- CloudShellへのコマンド入力も同様にブロックされる

といった制約があり、まとまった件数のログを正確に扱うにはAWS CLIからの直接アクセスが必要だった。ユーザーの管理者アカウント（`Administrator`）はアクセスキーが上限（2個）に達していて流用できなかったため、Claude専用の新規IAMユーザーを作成する方針にした。

## 権限方針

**最小権限**: `MoneyManagerOcrFunction`のCloudWatch Logsの読み取りと、同関数の`Invoke`/設定参照のみに絞っている。コード更新（`UpdateFunctionCode`等）の権限は**意図的に含めていない** — Lambdaのコード変更は[`lambda-deploy-setup.md`](./lambda-deploy-setup.md)の通りGitHub Actions経由の`git push`で行うフローが既にあり、Claudeがこのユーザーの権限でコードを直接書き換えられる状態にはしない。

権限ポリシー本体: [`aws-iam/claude-cli-mmm-permissions-policy.json`](./aws-iam/claude-cli-mmm-permissions-policy.json)

## なぜAWS側の設定をClaudeが自動で行わなかったか

IAMユーザーの作成・アクセスキー発行は「AWSアカウントのセキュリティ設定の変更」にあたるため、ブラウザ自動操作の安全装置で実際にブロックされた（`[claude-code-guide]`ではなく、Claude in Chrome拡張機能側の制御）。[`lambda-deploy-setup.md`](./lambda-deploy-setup.md)のIAMロール作成のときと同様、ユーザー自身がAWSコンソールで手動作成する方針にした。

## セットアップ手順（実施済み）

AWSアカウント`819376901674`（`MoneyManagerOcrFunction`が存在するアカウント）、IAMコンソールで実施。

### 1. IAMユーザーを作成

- ユーザー名: `claude-cli-mmm`
- コンソールアクセスは不要（プログラムアクセスのみ）

### 2. 権限ポリシーをインラインでアタッチ

[`aws-iam/claude-cli-mmm-permissions-policy.json`](./aws-iam/claude-cli-mmm-permissions-policy.json)の内容をそのまま貼り付け。

### 3. アクセスキーを発行

作成したユーザー → セキュリティ認証情報タブ → アクセスキーを作成 → ユースケース「コマンドラインインターフェイス (CLI)」。

### 4. ローカルのAWS CLIにプロファイルとして設定

チャットには値を出さず、ターミナルで直接設定（シークレットを会話ログに残さないため）:

```
aws configure set aws_access_key_id <キーID> --profile mmm
aws configure set aws_secret_access_key <シークレット> --profile mmm
aws configure set region ap-northeast-1 --profile mmm
```

以降、Claudeは`aws --profile mmm ...`でこのユーザーとして呼び出す。

## 既知の制約・ハマりどころ

- **`logs:DescribeLogGroups`はリソースをロググループ単位に絞ると権限エラーになる**（`AccessDeniedException`）。このアクションはアカウント/リージョン全体に対する一覧操作として評価されるため、特定ロググループへのスコープでは許可されない。今回はロググループ名（`/aws/lambda/MoneyManagerOcrFunction`）が既知だったため、`DescribeLogGroups`で存在確認する手順自体を省略し、ポリシーからも外した。
- Git Bash（MSYS2）経由で`aws`コマンドを呼ぶ際、`/aws/lambda/...`のような先頭が`/`のオプション値はパス変換の対象になり、意図しないWindowsパスに化けることがある。回避するには該当コマンドの前に`export MSYS_NO_PATHCONV=1`を付ける。
- `aws logs get-query-results`等の出力に日本語を含む場合、素の`aws`コマンド実行だけだとコンソールのコードページ起因で`UnicodeEncodeError`が出ることがある。`PYTHONUTF8=1 PYTHONIOENCODING=utf-8`を付けて回避した。

## 作業ログ

- 2026-10-13: `claude-cli-mmm`IAMユーザーを作成し、CloudWatch Logs（`MoneyManagerOcrFunction`のロググループ限定）の読み取り・検索権限と、同Lambda関数への`Invoke`/設定参照権限を付与。ローカルAWS CLIに`mmm`プロファイルとして設定。
- 2026-10-13: このプロファイルを使って過去60日分のCloudWatch Logsを取得し、家計簿アプリの入力履歴と突き合わせて52件のテストケースを作成（[`lambda/test_cases/test_cases.json`](../lambda/test_cases/test_cases.json)）。52件すべてをLambdaコンソールの「共有可能」テストイベントとしても登録済み。
