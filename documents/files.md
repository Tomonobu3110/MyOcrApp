# 各ファイルの説明

## Androidアプリ（`app/src/main/java/com/example/myocrapplication/`）

### `MainActivity.java`
アプリのエントリポイント。主な役割は3つ。

- **カメラ撮影**: CameraXでプレビュー表示し、シャッターボタンでレシートを撮影する
- **画像共有の受信**: 他アプリから `ACTION_SEND`（画像の共有）インテントを受け取り、共有されたURIを一時ファイルに変換してOCRにかける（`uriToFile`）
- **OCR実行とLambda呼び出し**: ML KitのJapaneseTextRecognizerでOCRし（`processImage`）、結果をダイアログ表示後、OkHttpでAWS Lambdaへ送信する（`invokeLambdaFunction`）。Lambdaの応答JSONを`ConfirmActivity`へ渡す
- **クイック登録**: OCRを経由せず、`SettingActivity`で保存したデフォルト値（店・支払方法・品目）と今日の日付からその場でJSONを組み立て、直接`ConfirmActivity`へ渡す
- **設定画面・ブラウザ遷移への導線**: 設定ボタン、個人/家計それぞれのCGI URLをブラウザで開くボタンを持つ

### `ConfirmActivity.java`
Lambda（またはクイック登録）が返したJSON（店名・日付・支払金額・品目）をフォームに表示し、ユーザーが確認・修正できる画面。

- 「今日」ボタンで日付欄を本日の日付に設定
- 日付を`yyyy年MM月dd日`形式から送信用の`yyyy/MM/dd`形式に変換（西暦2000年未満とパースされた場合に+2000年する補正ロジックあり）
- 3種類の送信ボタンを持ち、`FormSubmitter`を使って外部CGIへ登録する
  - **個人**: 個人用家計簿のみに登録
  - **家計**: 家計（世帯共有）用家計簿のみに登録
  - **立替**: 個人用に「送金済み」フラグ付きで登録し、同時に家計用にも登録（誰かの代わりに支払った場合に、両方の帳簿へ同時反映するため）
- 品目・店名のテキストは送信時にEUC-JPへエンコードしてから送られる（受け側のCGIがEUC-JPを前提にしているため）

### `FormSubmitter.java`
`multipart/form-data`形式のPOSTリクエストを、OkHttpなどのライブラリを使わず`HttpURLConnection`で手組みして送信するユーティリティクラス。フィールド名・値のペアを追加（`addField`）していき、`submitForm()`で一括送信する。レガシーなCGIプログラム（恐らく昔ながらのPerl/PHP等の家計簿CGI）を送信先として想定した作りになっている。

### `SettingActivity.java`
アプリの各種設定を保存・編集する画面。`SharedPreferences`の`AppSettings`に以下のキーで保存される。

| キー | 用途 |
|---|---|
| `LambdaUrl` | OCR結果解析用のAWS Lambda関数URL |
| `CgiUrl` | 個人用家計簿CGIのURL |
| `Cgi2Url` | 家計（世帯共有）用家計簿CGIのURL |
| `Account` | 「立替」時に家計簿側へ登録する際の登録者名 |
| `QuickShop` / `QuickPayment` / `QuickItems` | クイック登録で使うデフォルトの店名・支払方法・品目 |

## AWS Lambda（`lambda/`）

**注意**: このディレクトリの内容はAWSコンソールで直接編集・デプロイされており、CI/CDの仕組みはない。そのため本リポジトリの内容が実際にデプロイされているコードと乖離することがある（詳しくは[overview.md](./overview.md)参照）。

### `lambda_function.py`
Lambda関数のエントリポイント（ハンドラ: `lambda_handler`）。Androidアプリから送られたOCR生テキストを受け取り、以下を行う。

1. リクエストボディから「3番目のダブルクオートの位置」〜「最後のダブルクオートの位置」でメッセージ本文を切り出す（正規のJSONパースではなく、文字位置ベースの力技な抽出）
2. `find_shop_name`で`shops.py`の店舗候補とあいまい文字列マッチング（`difflib`の類似度）を行い、店名を推定
3. 正規表現で日付（`yyyy年MM月dd日`形式、または`yyyy/MM/dd`形式。後者は前者の形式に正規化される）を抽出
4. `add_items_from_message`で`items.py`のカテゴリ別品目リストとマッチングし、該当する**カテゴリ名**（品目そのものではない）を返す
5. 日付・電話番号らしき数字列をマスクした上で、レシートに2回印字されがちな「合計金額」を、出現回数が2回以上ある数値として推定（`find_max_duplicate`）
6. 店名・日付・支払金額・品目リストをJSONで返却

### `items.py`
カテゴリ名と、そのカテゴリに属する品目名候補のリスト（`category_items`）。「ワイン」「お酒」「コーヒー」「お昼ごはん」など、よく買う商品を元にしたカテゴリ分けがされている。新しい商品を認識させたい場合はこのリストに追記する。

### `shops.py`
店舗名と、その店舗のOCR誤認識パターンを含む候補文字列のリスト（`shop_list`）。一部の店舗には`place_list`（支店・拠点の絞り込み）が設定されており、店名が一致した上でさらに場所の候補文字列がマッチすると「〇〇の△△」という形で支店名付きの店名を返す。新しい店舗・支店を認識させたい場合はこのリストに追記する。

## その他

- `README.md`: プロジェクト名のみの簡易ファイル
- `CLAUDE.md`: Claude Code（AIコーディングアシスタント）向けのプロジェクトガイド。ビルドコマンドやアーキテクチャの要点をまとめたもの
- `app/build.gradle` / `build.gradle` / `settings.gradle`: Gradleのビルド設定（Androidアプリのみに関係し、`lambda/`には無関係）
