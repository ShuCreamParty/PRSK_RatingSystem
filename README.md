# PRSK_RatingSystem

音ゲーのスコア画像を読み取り、レートを計算・管理するAndroidアプリです。

## エンドユーザー向けインストール（最短）

1. GitHubのReleasesページから `app-release.apk` をダウンロード
2. Android端末で「この提供元のアプリを許可（不明なアプリのインストール）」をON
3. ダウンロードしたAPKを開いてインストール

## 開発者向け: 配布APKの作成

### ローカルで作成

- 署名鍵を使う場合は `keystore.properties.example` を `keystore.properties` にコピーして値を設定
- 署名鍵を未設定でも `assembleRelease` は実行可能（debug署名でフォールバック）

```powershell
.\gradlew.bat assembleRelease
```

生成先:

- `app/build/outputs/apk/release/`

### GitHub Actionsで自動配布

タグ `v*` をpushすると、ActionsでRelease APKをビルドしてGitHub Releaseへ添付します。

```powershell
git tag v1.0.1
git push origin v1.0.1
```

以下のRepository Secretsを設定するとリリース署名で配布できます。

- `ANDROID_KEYSTORE_BASE64` (keystoreファイルのbase64)
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Secrets未設定時はdebug署名でビルドされます（テスト配布向け）。
