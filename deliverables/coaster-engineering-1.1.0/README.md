# Coaster Engineering 1.1.0

Minecraft Java Edition **1.20.1**、Forge **47.4.10**、Java **17** 向けの独立したジェットコースターMODと完成ワールドです。MOD ID は `coasterengineering`。独自の3Dスプラインレールと専用車両を使用し、バニラのトロッコや Apex Predator に依存しません。

1.1.0では既存の車両モデル・外観、火山コースの45制御点の座標とバンクを維持し、建築の接合部、照明、音量、区間別の速度制御、乗車中の音とカメラ演出を改善しています。変更点は [CHANGELOG.md](CHANGELOG.md)、操作一覧は [CONTROLS.md](CONTROLS.md)、実施した試験・実測値・未検証項目は [TEST_RESULTS.md](TEST_RESULTS.md) を参照してください。

## 導入と最初の乗車

1. 公式Minecraft Launcherで1.20.1を一度起動し、使用するゲームディレクトリを確認します。
2. [Forge公式配布ページ](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)から **47.4.10** のInstallerを取得し、Java 17で起動して「Install client」を選びます。LauncherのForgeプロファイルでもJava 17を使用します。
3. `coasterengineering-1.1.0.jar` をそのゲームディレクトリの `mods` に入れます。旧 `coasterengineering-1.0.0.jar` は外し、同じMOD IDのJARを同時に入れないでください。
4. `CoasterWorld-VolcanicAbyss.zip` を展開し、ワールドフォルダを `saves` に置きます。既存の同名ワールドは上書きせず、バックアップしてから新しいZIPを別フォルダへ展開してください。
5. ワールド一覧から開くと、アドベンチャーモードで研究施設の駅から始まります。停車中の空席がある車両を右クリックすると乗車し、**7秒後に自動発車**します。3両・各2席、合計6席です。

`level.dat` はワールドフォルダの直下に必要です。ZIP自体や、同名フォルダが二重に入れ子になった状態では開けません。

```text
.minecraft/
  mods/
    coasterengineering-1.1.0.jar
  saves/
    Coaster World - Volcanic Abyss/
      level.dat
      region/
      entities/
      data/
      serverconfig/
```

**F5**で一人称・三人称を切り替え、**Shift**で降車します。1周すると駅の距離0で停止します。走行中に降車した場合は編成全体を停止し、駅付近の足場・液体・乗員の空間を確認して降車先を探します。終了時はメニューの「保存してタイトルへ戻る」を使います。HUDの周回数は保存済みの累積値です。

配布ワールドはプレイヤー個別の保存情報を除去し、コマンドを許可しています。初回スポーン用のワールドとして利用できます。

## ワールドと1.1.0の改善

「Coaster World - Volcanic Abyss」は、巨大火山、地底洞窟、研究施設の駅、待機列、入口、地下水脈・溶岩の展示、高速トンネル、屋外区間、帰還トンネル、オリジナルの怪物彫刻を含む保存済みワールドです。コースは約1,212ブロック、高低差約70ブロック、最高速度の設計上限は120km/h相当です。

1.1.0の品質更新は既存ワールドのコピーへ適用し、コースと車両のUUID、編成、駅、自動発車設定を保持します。車両のモデル・描画素材は1.0.0と同じものを使い、45制御点の座標・バンクと周回レイアウトを維持します。建築の修繕は屋内トンネル・洞窟の接合部を対象とし、意図した屋外区間、入口、窓を残します。

駅、洞窟、水辺、溶岩、怪物、高速区間へ標準の光源や装飾を追加します。色付きガラスなどの装飾は使いますが、Minecraft標準の光自体は白色です。座席からの光量の数値は、見せ場が人間の目で十分に見えることの証明とは区別します。

新しい速度プログラムは、探索、異変、急降下、怪物の見せ場での減速、洞窟、クライマックス、帰還を区間別に制御します。連続した走行距離に沿って目標速度を補間し、先読みして駅へ減速します。**正式Forgeサーバーでの全周は2,613 Tick、20 TPS換算130.65秒**で、保存・再起動後も同じ結果でした。旧版の178.45秒から約26.8%短縮し、最高120 km/h相当を維持しています。7秒の発車待ちを加えると137.65秒相当です。正式クライアントでも乗車・全周・駅停止・保存・再入場後の再乗車を確認しました。設定変更や処理速度によって実時間は変わります。詳しい検証と未検証項目は [TEST_RESULTS.md](TEST_RESULTS.md) を確認してください。

怪物は静的なブロック彫刻です。地下水脈と溶岩は発光する青・オレンジのガラスによる視覚表現です。実流体、ループ反転、同じコースでの複数編成、怪物AI・モデルアニメーション、動的な色付き照明は未実装です。実同時複数人のマルチプレイ、Apex Predatorとの併用、人間による音の聴取は未検証です。

## 音とカメラの設定

クライアント設定はゲームディレクトリの `config/coasterengineering-client.toml`、サーバー設定はワールド内の `serverconfig/coasterengineering-server.toml` に保存されます。設定ファイルを編集するときはゲームを終了してください。

オリジナルBGM3曲を約 **−18 dBFS RMS**へ調整し、BGMの初期音量を **0.8**に変更しました。外部の音声サンプルは使用していません。BGM3曲、短い効果音4個、風切り音・車輪音2個の計9音源です。BGMと走行音はプレイヤー相対で再生し、Minecraftの「音楽」と「主音量」スライダーも適用します。

乗車時や区間切り替えでは音量を滑らかに変え、降車後は **40 Tick（通常約2秒）**でフェードアウトします。ワールド退出では音源を解放します。仮想音声出力での音源読込や稼働数の確認は、人間が実際に音を聴いた確認とは区別しています。

既存の設定ファイルに保存した値は上書きしません。旧初期値0.35を使っている場合は、必要に応じて `attractionMusicVolume=0.8` に変更してください。

| 設定 | 初期値・範囲 | 効果 |
| --- | --- | --- |
| `maximumSpeedKmh` | 120、1〜120 | サーバーが決める速度上限 |
| `maximumAcceleration` | 0.025、0.001〜0.025 | 正方向の加速度上限。単位は blocks/tick² |
| `attractionMusic` | true | 乗車中のBGMを有効にする |
| `attractionMusicVolume` | 0.8、0〜1 | BGM音量。Minecraftの音楽・主音量も適用 |
| `rideSounds` | true | 速度に応じた風切り音・車輪音を有効にする |
| `rideSoundVolume` | 0.35、0〜1 | 走行音の音量。Minecraftの音楽・主音量も適用 |
| `effectsIntensity` | 0.55、0〜1 | 粒子・画面端の光・短い効果音の強さ。0で無効 |
| `followTrackCamera` | true | 車両の向きと傾斜へ視点を追従し、マウスで見回す |
| `bankCamera` | true | カメラのバンク回転。falseで抑制 |
| `cameraRollLimitDegrees` | 25、0〜60 | カメラの傾き上限。車両のバンクは維持 |
| `cameraVibrationDegrees` | 0.18、0〜0.25 | 高速時の小さな追加振動。0で無効 |
| `cameraFovPercent` | 2、0〜3 | 高速時のFOV増加率。0で無効 |

追加振動とFOV変化は速度に応じて滑らかに増減し、停止・降車で無効になります。振動を抑えたい場合は `cameraVibrationDegrees=0.0`、FOV変化を抑えたい場合は `cameraFovPercent=0.0` にします。既存の `bankCamera` と `followTrackCamera` も使用できます。

```toml
attractionMusic = true
attractionMusicVolume = 0.8
rideSounds = true
rideSoundVolume = 0.35
cameraVibrationDegrees = 0.18
cameraFovPercent = 2.0
```

乗車中に `/coasterclient status` を実行すると、編成・速度・周回数に加えて音源と演出の状態を確認できます。音源読込の期待値は `sound assets=9/9`、通常の走行中は `active music=1` と `motion=2`、降車約2秒後は両方0です。停車中は走行音が無効になります。区間切り替えのクロスフェード中は音楽が一時的に2つ稼働します。これらはソフトウェアの診断値です。

## 既存の火山ワールドを更新する

Minecraftを正常に保存・終了してから、**ワールドフォルダ全体をバックアップ**します。1.1.0のJARへ置き換え、元ワールドのコピーを開いて更新してください。新しい配布ZIPへ乗り換える場合は別フォルダに展開します。

この更新は既存の固定Volcanicコース用です。権限レベル2が必要で、元の編成が全車両読み込み済み・停止・無人であることを確認します。変更した座標やバンクなどが適合しない場合は拒否します。

```mcfunction
/coaster upgrade volcano
/coaster upgrade volcano status
```

更新中は発車・乗車を禁止し、処理量を制限して修繕と照明を適用します。`status` の完了表示を待ってから、無人の走行診断を行います。

```mcfunction
/coaster validate 61da851c-7705-3a82-b7b3-2e2e8ee296b4
/coaster validate status
```

途中で止める場合は `/coaster upgrade volcano cancel`。既に適用した安全な景観変更は残りますが、完了前の速度設定と品質更新マーカーは確定しません。元へ完全に戻す場合はバックアップを使います。明示的に同じ更新計画をもう一度適用する操作は `/coaster upgrade volcano reapply` です。編集済みの区間速度も1.1.0の既定値へ戻します。再適用時もバックアップ・停止・無人・全車両の読み込みが必要です。

旧ワールドにJARを入れ替えただけでは、建築・照明の修繕を自動適用しません。旧コースや自作コースは従来の走行処理を保持します。更新後の検証内容は [TEST_RESULTS.md](TEST_RESULTS.md) を確認してください。

## 自分のコースを作る

権限のあるクリエイティブのワールドで、開けた場所から実行します。

```mcfunction
/coaster demo
/coaster train 3
```

`demo` は小さな立体周回コース、乗降デッキ、支持柱、1両を生成します。`train 3` は停止・無人の完全な編成を3両に変更します。乗車後は `/coaster start` で発車します。配布ワールドでこの生成操作は不要です。

独自レールには `NORMAL / BOOST / BRAKE / LIFT / STATION` があり、バンクと目標速度を制御点に保存します。コースは連続した距離で走行し、通常のコースでは重力・摩擦・区間別の加速と減速を使います。レールブロックを並べるだけでは経路は増えません。エディタの点を保存するか、既存コースの点を編集してください。

1コースにつき1編成で、1〜4両・各2席です。車両UUIDと編成リンクを保存し、アンロード中も予約を保持します。再読み込み時には停止状態へ復元し、欠けた車両を勝手に生成しません。走行では未読み込みチャンクを生成せず、車体・乗員の障害物、液体、ワールド境界を移動前に検査して停止します。

駅コントローラの紐づけ、GUI、レッドストーン、エディタ、警報解除などの手順は [CONTROLS.md](CONTROLS.md) にまとめています。緊急停止の警報解除は解除だけでは発車せず、後の発車操作または有効な自動発車条件で再開します。

## ソースからビルドする

`coasterengineering` は既存のApex Predatorプロジェクトとは独立したGradleプロジェクトです。Java 17の**JDK**を選び、このディレクトリで実行します。

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient
```

Windowsでは `gradlew.bat` を使います。`build` は単体テストと配布用JARの再難読化を含み、成果物は `build/libs/coasterengineering-1.1.0.jar` です。初回ビルドにはGradle・Forge・依存ライブラリの取得が必要です。ビルド成功だけで表示・乗車・全周走行を確認したとは判断しません。

`CoasterEngineering-Source.zip` を使う場合は展開した `coasterengineering` フォルダへ移動し、同じコマンドでビルドします。Gradle Wrapper、Javaソース、テスト、リソース・音源、生成スクリプト、文書を収録します。通常のソースビルドで音源を生成し直す必要はありません。

クラウド環境では、リポジトリ側のセットアップ済みスクリプトを利用できます。

```bash
cd /workspace/minecraftMOD/coasterengineering
source ../scripts/environment.sh
./gradlew build runGameTestServer
bash ../scripts/start-display.sh
source ../scripts/display-environment.sh
export ALSOFT_DRIVERS=null
./gradlew runClient
```

`ALSOFT_DRIVERS=null` は仮想音声出力による検証用です。音を人間が聴く環境では通常の音声出力を使用します。`runClient` は開発クライアントであり、最終JARの本番起動を確認する場合は、公式Forge Installerと通常のMinecraft LauncherへビルドしたJARを導入して別途試験してください。

Forgeを起動せず走行計算のテストだけを実行する場合は `bash scripts/test-track.sh`。外観維持は `python3 scripts/verify-preserved-design.py`、音源検査は `python3 scripts/generate-audio.py --verify-only` で確認できます。音源検査・再生成にはPython 3.12以上とFFmpegの `libvorbis` を使います。

## 新しい火山ワールドを生成する

Javaの生成処理で、建築、独自経路、駅、3両編成を実ワールドへ配置します。シード **20261011**、草の表面Y=63の空の専用フラットワールドを使用し、既存の建築・コースがある領域では生成を拒否します。自分の既存ワールドへ火山を重ねて生成する操作は想定していません。

新しい専用ディレクトリを準備し、Java 17でローカルの開発サーバーを起動します。準備スクリプトはアドベンチャーモード・ローカル検証用の設定を作成します。

```bash
bash scripts/prepare-volcano-server.sh "$PWD/run-volcano-rebuild"
./gradlew runServer -PcoasterServerDir=run-volcano-rebuild
```

サーバーの `Done` を待ち、コンソールで実行します。コンソールでは先頭の `/` を付けません。

```text
coaster volcano
coaster volcano status
```

1.1.0では基礎建設の確定後に修繕・照明の品質更新を自動で開始します。`CONSTRUCTED`（基礎建設完了／品質更新中）の表示から、続けて品質更新の状態を確認し、`COMPLETED` まで待ってください。更新中は乗車・発車を禁止します。

```text
coaster upgrade volcano status
coaster validate 61da851c-7705-3a82-b7b3-2e2e8ee296b4
coaster validate status
```

品質更新が完了してから `validate` を実行し、生成や更新の進行中に診断を開始しないでください。診断は通常のサーバーTickで無人編成を1周走行させ、一時的なチャンク保持を完了・失敗・中止時に解除します。実プレイヤーの乗車試験とは別です。

完了後は `save-all flush`、`stop` の順で保存・終了します。生成の中止・失敗では施工済みの部分が残るため、生成を最初からやり直す場合は新しい専用ディレクトリを使います。配布ワールドで再生成する必要はありません。

## 配布ファイルとGitHubからの取得

1.1.0の成果物は `dist/quality-1.1.0/` にまとめます。

| ファイル | 内容 |
| --- | --- |
| `coasterengineering-1.1.0.jar` | 通常のForgeへ導入する再難読化済みMOD |
| `CoasterWorld-VolcanicAbyss.zip` | `saves` へ展開できる保存済み火山ワールド |
| `CoasterEngineering-Source.zip` | Java 17・Gradleでビルドするソース一式 |
| `CoasterEngineering-Validation.zip` | 配布時に収録する検証ログ・結果・実画面など |
| `README.md` / `CONTROLS.md` / `TEST_RESULTS.md` / `CHANGELOG.md` | 導入・操作・実施した試験・変更内容 |
| `LICENSE` / `SHA256SUMS` | ライセンスとその配布物のチェックサム |

チェックサムは取得した**同じ版**の `SHA256SUMS` と照合してください。READMEに旧版のJARハッシュを固定して載せていません。

[GitHubリポジトリ](https://github.com/schnauzer0927-boop/minecraftMOD)の「Code → Download ZIP」はソースを取得する操作です。プレイするには、配布されたビルド済みJARとワールドZIPを取得するか、ソースからJARをビルドしてワールドを生成します。GitHub Releasesなどで配布物を取得する場合も、1.1.0のJAR・対応ワールド・同梱文書を揃えてください。Releaseの公開状況と、ローカルで作成済みの配布物は別です。

保存済みワールドとJARから配布物を作る場合は、Minecraftを停止してから次を実行します。スクリプトは実行中ワールドをセッションロックで拒否し、ZIPのCRCを検査します。検証用ZIPは別途収録します。

```bash
python3 scripts/build-distribution.py \
  --world "run-volcano-rebuild/Coaster World - Volcanic Abyss" \
  --out dist/quality-1.1.0
```
