# Windowsへのダウンロードと導入

この作業環境はCodexクラウドのLinuxです。`/workspace/`のパスをWindowsのエクスプローラーへ貼り付けてもアクセスできません。GitHub上の2.0専用成果物をローカルPCへ保存してください。

## ファイルを保存する

[2.0成果物フォルダ](https://github.com/schnauzer0927-boop/minecraftMOD/tree/download/coaster-engineering-2.0.0/deliverables/coaster-engineering-2.0.0)をブラウザーで開き、必要なファイルのページで「Download raw file」をクリックします。アーカイブのプレビューは不要です。

- `coasterengineering-2.0.0.jar`
- `CoasterWorld-VolcanicAbyss-2.0.zip`
- `CoasterEngineering-Source.zip`（開発する場合）
- 設計・モデル・イベント資料、README、操作方法、BGM手順、テスト報告も同じフォルダにあります。

ブラウザーでCtrl+Jを押してダウンロード一覧を開き、「フォルダーに表示」を選びます。通常の保存先は`C:\Users\<Windowsユーザー名>\Downloads`です。ブラウザーの保存設定を変更している場合は、その保存先を使います。

## Minecraftへ配置する

1. Forge47.4.10 / Minecraft1.20.1 / Java17の構成を使います。
2. Win+Rで`%APPDATA%\.minecraft`を開き、JARを`mods`へ入れます。Minecraftランチャーで独自のゲームディレクトリを指定している場合は、そちらを開きます。同じMOD IDの旧JARは別の保管フォルダへ移してください。
3. ワールドZIPを右クリックして「すべて展開」。中の`Coaster World - Volcanic Abyss 2.0`フォルダを`saves`へコピーします。
4. `saves\Coaster World - Volcanic Abyss 2.0\level.dat`の位置を確認し、新ワールドを開きます。駅の車両を右クリックすると乗車できます。

旧1.1ワールドの削除・上書きは不要です。JARはZIPとして展開せず、そのまま`mods`へ配置します。ワールドZIP全体を`mods`へ入れないでください。

ダウンロード後、PowerShellの`Get-FileHash <ファイル名> -Algorithm SHA256`で、同梱の`SHA256SUMS`と一致することを確認できます。
