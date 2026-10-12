# Coaster Engineering 2.0.0 — VOLCANIC ABYSS

Minecraft Java Edition **1.20.1** / Forge **47.4.10** / Java **17**。MOD IDは `coasterengineering`。専用スプラインレールと専用車両を使う独立MODです。

2.0は1.1の完成ワールドを上書きせず、別ワールドでコースと建築を新設計します。車両モデル・描画素材・基本デザインを維持し、クリスタル湖、古代都市、巨大生命体の巣、火山心臓部、屋外脱出を体験する構成です。実施した検証と未検証項目は **[TEST_RESULTS.md](TEST_RESULTS.md)** に記載します。

## 導入と乗車

1. Minecraft 1.20.1を一度起動し、[Forge公式](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)の47.4.10 Installerでクライアントを導入します。Java 17を選択してください。
2. `coasterengineering-2.0.0.jar` を使用するゲームディレクトリの `mods/` へ入れます。同じMOD IDの旧JARはそのフォルダから外します。旧ファイルを保管し、2.0と同時に読み込ませないでください。
3. `CoasterWorld-VolcanicAbyss-2.0.zip` を展開し、`Coaster World - Volcanic Abyss 2.0` フォルダを `saves/` へ置きます。`level.dat` はワールドフォルダの直下です。
4. 新ワールドを開き、駅ホームの車両へ視線を少し下げて右クリックすると乗車します。3両・各2席、合計6席です。通常は7秒後に自動発車します。
5. F5で視点を切り替え、Shiftで降車します。走行中の降車は編成を安全停止し、プレイヤーを駅付近の安全な位置へ戻します。2.0の無人編成は約3秒後に安全確認した駅へ帰還します。乗員・緊急停止・駅の障害物がある間は帰還しません。終了時は「保存してタイトルへ戻る」を使います。

```text
.minecraft/
  mods/coasterengineering-2.0.0.jar
  saves/Coaster World - Volcanic Abyss 2.0/
    level.dat
    region/
    entities/
    data/
    coasterengineering/attractions/volcanic_abyss_v2.json
```

旧1.1ワールドと成果物は別に保管してください。新ZIPで旧ワールドを上書きする必要はありません。配布ワールドは個別のPlayer情報を除去した初回スポーン用保存データです。検証で増えたHUDの周回数は累積値です。

## 体験と実装範囲

全長約2,126ブロック、62制御点。地底の低速探索から古代都市のS字・高低差、怪物前の短い停車と逃走ローンチ、火山内のヘリックス、崖上の停車、大落下、屋外の高速脱出、駅停止へ進みます。速度は走行距離から制御し、曲率に応じた制限と区間別の加速・制動を適用します。

純Javaの計算は停車込み約172.05秒、発車待ち7秒を加えて約179.05秒、最高約118 km/h相当です。これを実ゲームで確認した結果と混同せず、実測結果はテスト報告を参照してください。1ブロック=1m、20 TPSを換算基準に使い、表示だけを大きくする処理はありません。

24個のJSONイベントと8区間の進行をサーバーで管理します。照明、扉、警報、怪物の出現・咆哮・威嚇、噴出・落石パーティクル、静寂、音楽切替、2か所の停車を連携し、実行履歴とタイマーを保存します。噴火や落石は演出であり、走行経路のブロックを破壊しません。怪物は専用の非戦闘Entityです。

**垂直360度ループは本コースに実装していません。** 独立した数学検証で既存フレームの垂直点の反転問題と並行移動フレームの連続性を確認しましたが、反転車両・座席・カメラの実ゲーム検証は未実施です。本コースは検証対象の非反転ヘリックスを採用します。

## BGMと快適性

5場面の音楽は既存のオリジナル3曲を再利用した仮音源です。Sunoなどで作成した曲はJARを編集せず、リソースパックから交換できます。未提供・読込失敗時も既存曲へフォールバックし、走行を継続します。

- `exploration.ogg`
- `ruins_chase.ogg`
- `monster_encounter.ogg`
- `eruption_escape.ogg`
- `finale.ogg`

[BGM_GUIDE.md](BGM_GUIDE.md)に交換手順、1曲から場面ごとの再生位置を切り出す方法、音量設定を記載します。任意秒へのランタイムシークは未実装です。曲の切替は走行距離とサーバーイベントに同期します。

クライアント設定は `config/coasterengineering-client.toml`。既存の設定値は上書きしません。

```toml
attractionMusicVolume = 0.8
rideSoundVolume = 0.35
cameraVibrationDegrees = 0.18
cameraFovPercent = 2.0
automaticGaze = true
automaticGazeBlend = 0.30
```

振動は `cameraVibrationDegrees=0.0`、FOV変化は `cameraFovPercent=0.0`、自動視線誘導は `automaticGaze=false` で個別にOFFにできます。Minecraftの音楽・主音量も適用されます。設定編集はゲームを終了してから行います。

## 操作・編集と検証撮影

通常の乗車にコマンドは不要です。管理操作は権限レベル2を使用します。

```mcfunction
/coaster status
/coaster attraction status
/coaster attraction reload
/coaster start
/coaster stop
/coaster reset
/coaster validate fd0fb037-a161-3b04-931f-de6e3c3a422e
/coaster validate status
```

`reload` は読み込み済みの停車編成で使います。ワールド内 `coasterengineering/attractions/volcanic_abyss_v2.json` にイベント、音楽、区間別の加速度・制動・速度倍率・停車時間を設定します。固定停車位置の移動には経路側の変更も必要です。設定の詳細は [docs/ATTRACTION_EVENTS_2.0.md](docs/ATTRACTION_EVENTS_2.0.md)。既存のエディタ・駅GUI・レッドストーン操作は [CONTROLS.md](CONTROLS.md) を参照してください。

実乗車の撮影を有効にするには、乗車前に `/coasterclient capture start` を実行します。指定した区間と停車中の実際の描画フレームを `screenshots/volcanic-v2-*.png` へ保存し、駅へ帰還すると終了します。`capture status` と `capture stop` も利用できます。これは走行を操作する機能ではありません。詳細な景観・FPS確認手順は [docs/V2_SCREENSHOT_VALIDATION.md](docs/V2_SCREENSHOT_VALIDATION.md) に記載します。

## ソースからビルドする

Java 17のJDKで `coasterengineering` フォルダ内から実行します。Windowsでは `gradlew.bat` を使います。

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient
```

再難読化済みJARは `build/libs/coasterengineering-2.0.0.jar`。通常のソースビルドにFFmpegやBlockbenchは不要です。Blockbenchモデル、変換スクリプト、コース設計JSON、イベントJSON、BGMリソースパック雛形はソース一式に含めます。

```bash
python3 scripts/generate-monster-model.py --check
python3 scripts/verify-preserved-design.py
python3 scripts/measure-v2-course.py
```

コース設計JSONはJava生成器の座標・設定を出力した資料です。JSONの座標だけを編集しても生成器や保存済みコースは変わりません。経路の変更はゲーム内エディタ・編集コマンド、または`VolcanicAbyssV2.java`を変更してビルド・新規生成してください。イベントと区間の加速・制動・速度倍率はワールド内JSONから再読込できます。

怪物の編集手順は [docs/MONSTER_MODEL.md](docs/MONSTER_MODEL.md)、走行設計は [docs/V2_COURSE_DESIGN.md](docs/V2_COURSE_DESIGN.md)。1.1の設計と旧テスト記録は `docs/*_1.1.0.md` に保管しています。

## 新ワールドを自動生成する

**新しい専用フラットワールド**、seed20261012、地面の草Y63を使用します。生成は既存の1.1ワールドや建築済み領域を拒否します。制作済み配布ワールドを遊ぶ場合、この操作は不要です。

```bash
bash scripts/prepare-v2-server.sh "$PWD/run-v2-new"
./gradlew runServer -PcoasterServerDir=run-v2-new
```

サーバーの `Done` を待ち、コンソールで先頭の `/` なしで実行します。

```text
coaster volcano2
coaster volcano2 status
```

`READY` まで待ちます。途中で失敗・中止した領域は施工部分が残るため、新しいディレクトリで再生成します。車体・直立乗員の通過空間、室内壁面、座席光量の検査後に駅・コース・編成・怪物と完成マーカーを確定します。

```text
coaster validate fd0fb037-a161-3b04-931f-de6e3c3a422e
coaster validate status
save-all flush
stop
```

公式Forgeインストール済みのサーバーを使う場合は `scripts/commission-v2-world.py` で実生成・全周・正常保存のログを記録できます。実行中ワールドの配布は行いません。

## 配布物

`deliverables/coaster-engineering-2.0.0/` に2.0専用成果物を配置します。旧1.1配布は保管します。主なファイルはJAR、保存ワールドZIP、ソースZIP、設計・モデル・イベント資料、BGM手順、テスト報告、検証資料、SHA256SUMSです。実ゲームで確認できなかった事項はテスト報告に明記します。GitHubの「Code → Download ZIP」はビルド済みJARの取得とは別です。
