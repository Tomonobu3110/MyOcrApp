# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An Android app that photographs or receives a shared receipt image, runs Japanese OCR on it, sends the recognized text to an AWS Lambda for parsing (shop name / date / amount / items), lets the user confirm/edit the result, then submits it as a legacy multipart form POST to one or two external CGI endpoints (personal and household ledgers). The `lambda/` directory holds that Lambda's source but is a separate deployment target — it ships to AWS independently of the Android app and is not built by Gradle.

## Commands

Build and test via the Gradle wrapper from the repo root (`gradlew.bat` on Windows, `gradlew` in Git Bash):

```
gradlew.bat assembleDebug          # build debug APK
gradlew.bat installDebug           # build and install on a connected device/emulator
gradlew.bat test                   # JVM unit tests (app/src/test)
gradlew.bat connectedAndroidTest    # instrumented tests on device/emulator (app/src/androidTest)
gradlew.bat lint                   # Android lint
```

There is currently no test suite worth running — `app/src/test` and `app/src/androidTest` only contain the unmodified Android Studio template tests.

The `lambda/` code has no build/test tooling in this repo; it's deployed manually/out-of-band to AWS Lambda (the entry point is `lambda_function.lambda_handler`).

## Architecture

**Activity flow:** `MainActivity` (camera capture + share-intent receiver) → OCR → Lambda call → `ConfirmActivity` (edit/confirm parsed fields) → `FormSubmitter` (posts to external CGI). `SettingActivity` edits the URLs/account/quick-entry defaults used throughout, all stored in the `AppSettings` SharedPreferences file (keys: `LambdaUrl`, `CgiUrl`, `Cgi2Url`, `Account`, `QuickShop`, `QuickPayment`, `QuickItems`).

**Getting an image into the pipeline** (`MainActivity`), two paths:
- CameraX capture via the in-app preview/shutter button.
- Receiving a shared image through the `ACTION_SEND` intent filter (e.g. "Share" from a gallery/photos app to this app), converted from `Uri` to a temp file via `uriToFile`.

Either path calls `processImage`, which runs ML Kit's Japanese text recognizer (`JapaneseTextRecognizerOptions`) on the image and shows the raw OCR text in a confirmation dialog before proceeding.

**Lambda round-trip:** `invokeLambdaFunction` POSTs the OCR text as `{"message": "<raw text>"}` to the user-configured `LambdaUrl` (via OkHttp) and expects back a JSON body with `shop`, `date`, `payment`, `items` fields extracted by the Lambda's heuristics (see below). That JSON is passed verbatim as `response_json` into `ConfirmActivity`'s launch intent. The "quick" button on `MainActivity` skips OCR/Lambda entirely and builds the same JSON shape locally from the saved quick-entry preferences plus today's date.

**Lambda parsing logic** (`lambda/lambda_function.py`): no real JSON parsing of the OCR text is attempted — the message is extracted from the raw request body by locating the 3rd and last double-quote characters (fragile, but intentional given how the body arrives). From that text it: fuzzy-matches a shop name against `shop_list` (`lambda/shops.py`, using `difflib` similarity, threshold-based, with shop → place sub-matching), extracts a date via regex (separate Japanese `年/月/日` and `yyyy/MM/dd` patterns, the latter normalized into the former), fuzzy-matches purchased items against `category_items` (`lambda/items.py`) and returns matched *category* names (not raw item text) as `items`, and guesses the payment amount by masking out dates/phone numbers then picking the number that appears more than once in the remaining text (receipts tend to print the total twice). Extending the shop/item recognition means editing the data tables in `shops.py`/`items.py`, not the matching code.

**Source of truth is AWS, not this repo:** the actual deployed Lambda (`MoneyManagerOcrFunction`, ap-northeast-1) is edited directly in the AWS Console's inline code editor, not via any CI/CD pipeline — there is no GitHub Actions workflow or IaC in this repo for it. That means `lambda/*.py` here can silently drift behind production; before trusting or extending the parsing logic, check the console (or pull the deployment package via "ダウンロード" → "ファンクションコード .zip をダウンロード") rather than assuming this checkout is current. The Lambda source itself is UTF-8 (Python 3.13 runtime) — match that when updating these files from the console.

**Submission** (`ConfirmActivity` + `FormSubmitter`): on submit, the date is reformatted from `yyyy年MM月dd日` to `yyyy/MM/dd` (with a Y2K-style fixup if the parsed year is < 2000), then a raw multipart/form-data POST (hand-rolled in `FormSubmitter`, not OkHttp) is sent to the CGI URL with fixed field names (`dtkind`, `date`, `group1`, `group2`, `detail`, `acount`, `card`, `inout`, `amount`, `page`, optionally `sent`) matching whatever legacy CGI ledger app these endpoints point to. The `detail` field (shop + item text) is deliberately encoded as EUC-JP bytes (`encodeToEucJp`) before being added as form data — the receiving CGI expects that encoding, not UTF-8. There are three submit buttons wired to different combinations of destination URL / account name / `sent` flag: 個人 (personal ledger only), 家計 (household ledger only), 立替 (post to personal marked as already-sent, then post to household under the configured `Account` name) — this is how shared/reimbursed purchases get recorded in both ledgers at once.

## Key constraints

- `minSdk 33` / `targetSdk 33`, `compileSdk 34` — this only targets fairly recent Android versions.
- All user-facing strings and in-code comments are Japanese; keep new UI text and comments consistent with that rather than switching to English.
- CGI/Lambda URLs, the ledger account name, and OCR quick-entry defaults are all runtime user settings (`SettingActivity`/`AppSettings` prefs), never hardcoded — don't hardcode endpoints when adding features that need them.
