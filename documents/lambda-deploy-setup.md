# Lambda自動デプロイのセットアップ

`main`ブランチへ`lambda/**`配下の変更がpushされると、GitHub Actionsが自動でAWS Lambda関数`MoneyManagerOcrFunction`（ap-northeast-1）のコードを更新する仕組み。

## 構成

- ワークフロー定義: [`.github/workflows/deploy-lambda.yml`](../.github/workflows/deploy-lambda.yml)
- 認証方式: **GitHub OIDC**（長期のアクセスキーをGitHub Secretsに保存しない方式）。GitHub Actionsの実行時にAWS STSから一時的な認証情報を取得する
- IAMロールの信頼ポリシー: [`aws-iam/trust-policy.json`](./aws-iam/trust-policy.json) — このリポジトリの`main`ブランチからのみ引き受け可能
- IAMロールの権限ポリシー: [`aws-iam/permissions-policy.json`](./aws-iam/permissions-policy.json) — `MoneyManagerOcrFunction`一つに対する`UpdateFunctionCode`系の権限のみ（最小権限）

## なぜAWS側の設定をClaudeが自動で行わなかったか

IAMロールやOIDCプロバイダーの作成は「AWSアカウントのセキュリティ設定の変更」にあたるため、安全のためユーザー自身に実行していただく方針にした。また実務上も、ローカル環境に設定されているAWS CLI認証情報はこのLambda関数のアカウント（`819376901674`）とは別のAWSアカウント（`864264994653`）を向いており、そもそも作業できる状態になかった。

以下の手順をこのLambda関数が存在するAWSアカウント（`819376901674`、東京リージョンの`MoneyManagerOcrFunction`が見えるアカウント）で、管理者権限を持つ認証情報を使って実行する。IAM自体はグローバルなリソースなのでリージョン指定は不要（Lambda関数本体や`aws lambda`系コマンドは東京=`ap-northeast-1`を指定する）。

## セットアップ手順（初回のみ・ユーザー側で実施）

### 1. GitHub用のOIDCプロバイダーを作成（既に他のワークフローで作成済みなら不要）

まず既存の有無を確認:

```bash
aws iam list-open-id-connect-providers
```

一覧に`token.actions.githubusercontent.com`が無ければ作成する:

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

### 2. IAMロールを作成

このリポジトリの`documents/aws-iam/trust-policy.json`を使う:

```bash
aws iam create-role \
  --role-name github-actions-moneymanager-lambda-deploy \
  --assume-role-policy-document file://documents/aws-iam/trust-policy.json
```

### 3. 権限ポリシーをアタッチ

このリポジトリの`documents/aws-iam/permissions-policy.json`を使う:

```bash
aws iam put-role-policy \
  --role-name github-actions-moneymanager-lambda-deploy \
  --policy-name lambda-update-code \
  --policy-document file://documents/aws-iam/permissions-policy.json
```

### 4. 作成したロールのARNを確認

```bash
aws iam get-role --role-name github-actions-moneymanager-lambda-deploy --query 'Role.Arn' --output text
```

`arn:aws:iam::819376901674:role/github-actions-moneymanager-lambda-deploy` のような文字列が返る。

### 5. GitHubリポジトリにロールARNを登録

GitHubの当該リポジトリ → Settings → Secrets and variables → Actions → **Variables**タブ → New repository variable

- Name: `AWS_DEPLOY_ROLE_ARN`
- Value: 手順4で取得したARN

（アクセスキーではなくロールのARNなので、Secretsではなく通常のVariablesで問題ない）

## 以降の動作

1. `lambda/`配下のファイルを変更して`main`にpush
2. GitHub Actionsが起動し、OIDCでAWSの一時認証情報を取得
3. `lambda/lambda_function.py` / `items.py` / `shops.py` を zip 化
4. `aws lambda update-function-code`で`MoneyManagerOcrFunction`を更新
5. `aws lambda wait function-updated`で反映完了を待って終了

## 動作確認方法

GitHubリポジトリの「Actions」タブで`Deploy Lambda`ワークフローの実行結果を確認する。AWS側はLambdaコンソールの「最終更新日」が更新されていれば反映成功。

## 作業ログ

- 2026-10-09: ワークフロー（`.github/workflows/deploy-lambda.yml`）とIAMポリシー一式（`documents/aws-iam/`）を作成。IAMロール自体の作成はユーザー側で実施待ち。ロールARNをGitHub Variablesに登録後、本ワークフローが有効化される。
- 2026-10-09: ユーザーがAWS CloudShellでセットアップ手順を実施（ClaudeがCloudShellにコマンド文字列を入力し、実行自体はユーザーがEnterキーで行う形で進行）。
  - OIDCプロバイダー（`token.actions.githubusercontent.com`）は既に作成済みだったことを確認（手順1は不要だった）
  - IAMロール`github-actions-moneymanager-lambda-deploy`を作成: `arn:aws:iam::819376901674:role/github-actions-moneymanager-lambda-deploy`
  - 権限ポリシー`lambda-update-code`をロールにアタッチ
  - GitHubリポジトリの Settings → Secrets and variables → Actions → Variables に `AWS_DEPLOY_ROLE_ARN` を登録（ブラウザで値を確認し、ロールARNと完全一致していることを確認済み）
  - **これでセットアップ完了。** 次回`lambda/**`を変更して`main`にpushすると、GitHub Actionsが自動でAWS Lambdaへデプロイする。
- 2026-10-09: ワークフローに`workflow_dispatch`トリガーを追加し（`lambda/**`以外の変更なのでpush自体ではデプロイは走らない）、GitHub Actionsの「Actions」タブから手動実行（Run workflow）で実機テストを実施。
  - 実行結果: Success（所要11秒）
  - AWS側: Lambdaコンソールの「最終更新日」が実行直後に更新されたことを確認（OIDC認証・zip化・`update-function-code`・`wait function-updated`まで一連の流れが正常動作）
  - **パイプライン動作確認済み。** 以降は`lambda/**`の変更をpushするだけで自動デプロイされる。手動で再デプロイしたい場合もActionsタブの「Run workflow」で随時可能。
