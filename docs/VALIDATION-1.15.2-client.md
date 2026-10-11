# Apex Predator 1.15.2 — 実エディター・実クライアント確認

2026年10月11日（日本時間）。ネイティブアプリケーションを使った検証記録です。製品のJava、AI、攻撃判定を変更してポーズを作る方法は使用していません。

## Blockbenchでの正式編集元

公式Linux版Blockbench 5.2.2とGeckoLib Animation Utils 4.2.5を使用しました。実アプリのプロジェクト読込・再保存・編集・Paint・プレビュー・書き出しを、検証専用コピーで実行しました。

- `geckolib_model`として498キューブ・28ボーン・37クリップが読み込まれました。
- 埋め込み`apex.png`・`apex_glow.png`・`apex_healing_glow.png`は、すべて1024×1024で読み込みエラー0でした。
- 実アプリで再保存したコピーを専用CLIで検査し、geometry/pivot/UV、37クリップ・5316キーフレーム、3枚のPNGが正式resourceと一致しました。
- `head`のpivot、`face_center`の寸法・UVを実編集し、別コピーへ保存しました。専用CLIから出力したgeoへ反映されています。
- 実Paintで3枚のPNGそれぞれ1ピクセルだけを変更し、専用CLIでその3枚を出力しました。RGBA比較の差分領域はいずれも`(120,100)-(121,101)`の1ピクセルでした。
- 17クリップそれぞれ5時刻を実Animatorへ適用しました。498キューブのworld matrixは85時刻すべて有限でした。blade combo/slam/charge/run/sprintを実画面でも確認しました。
- rawアニメーション書き出しの名称・loop・長さ・時刻・easingは一致しました。数値93箇所に最大`1.69e-15`の浮動小数点丸めがありました。元resourceへ書き戻していません。

実UI画像: [正式モデルとUV](visuals/apex-reference-match-blockbench.png)。この画像は実Blockbenchウィンドウのキャプチャです。

初回起動では新しい検証プロファイルのWebGL初期化が失敗しました。ソフトウェア描画用の起動設定で正常に読み込めました。検証終了は正常終了0。正式bbmodel・geometry・3枚PNG・animation JSONは、開始前後のSHA-256が一致しました。実行用の`run/options.txt`やconfigはこの段階では変更していません。

最初の実charge側面を比較すると、元クリップはbody 12° + chest 38°の前傾でしたが、頭頂は立ち姿より約5モデル単位しか下がらず、設計図の極端な低姿勢との差が残りました。そこで同じ角度・同じ距離の実アプリでB/C候補を比較し、より少ない補正で頭部の前向きと後方フィンの流れを保つBを採用しました。

正式版では`charge`のchest 53° / neck -30°、`charge_prepare`後半のchest 40° / neck -20°を使用します。2クリップ・2ボーンのX回転16値だけを補正し、開始時の姿勢・全時刻・easing・既存の身体/四肢/position・残る35クリップを維持しました。上体は元より水平に近づきます。設計図の極端な四肢の折り畳みを完全に再制作したモーションとは区別しています。

補正を正式反映したBBを実アプリで再保存し、専用CLIで498キューブ・28ボーン・37クリップ・5316キーフレーム・3PNGの一致を再確認しました。17クリップ×5時刻も再適用し、85時刻のworld matrixがすべて有限でした。[正式版の側面charge](visuals/apex-reference-match-charge-blockbench.png)はその実UIの画像です。最終正常終了0、検証担当は正式assetを書き換えていません。

生のネイティブ記録はignoredの`build/reference1152/native/`にあります。`editor-audit.json`、`body-inspect.json`、`apex-resaved.bbmodel`、`edit-and-paint.json`、`native-motion-samples.json`、`formal-hashes-before.json` / `formal-hashes-after-native.json`に確認結果を保存しています。仮想画面・ソフトウェア描画による検証で、ハードウェアGPUの性能測定ではありません。

## Minecraft実クライアント（TaCZなし）

`./gradlew runClient -PquickPlayWorld=ApexReference1152Plain`を実行し、Minecraft 1.20.1 / Forge 47.4.10 / Java 17 / GeckoLibの実ウィンドウを1280×720で確認しました。既存の検証ワールドを私用コピーへ複製し、元ワールドには入力していません。新しいモデル・PNGは正式resourceから読み込まれました。画像はX11の実画面を無加工で保存したものです。

[実ゲーム内45度](visuals/apex-reference-match-ingame.png)のほか、同距離の正面・側面・背面・45度を撮影しました。独立担当も4枚を実表示し、細い橙色の目、銀色の面と黒い内部、V型胸部、細い腰、複合ブレード、後方フィン、首/肩/肘/膝/足首の接続を確認しました。透明な三角プレートを正面・側面・背面から見ても、白穴や棋盤模様の透けは見つかりませんでした。

既存の`/apex animate`をNoAIの検証個体に使用し、idle / walk / run / sprint / charge_prepare / charge / blade_combo_1–3 / jump_prepare / jump / slam / dodge_left / dodge_right / phase_change / rage_idle / rage_run / rage_attack / deathの19名称を確認しました。正面19シーケンス、側面10シーケンスをそれぞれ4時刻で撮影（116枚）し、腕・ブレード・膝カバーの接続を確認しました。このプレビューはAIが攻撃を選ぶ検証とは別です。 記載した時刻はサーバーへcueを送った後の撮影予定時刻で、実クライアントへの同期・描画遅延を含みます。クリップ内部の正確な時刻であるとは主張していません。

独立担当が代表36画像を実表示し、正面19名称と側面10名称を確認しました。首/頭の脱落、肩/肘/膝の大きな白い隙間、刃が胴体を横断して突き出す破綻、透明プレートの穴は見つかりませんでした。開腕や曲げ時には黒い内部関節が見えますが接続は保たれています。blade_combo_2で正面の顔前に重なる刃は側面からも確認し、頭部の脱落ではないと判断しました。これは観測した代表フレームの確認で、連続した全フレームの無貫通を保証する主張ではありません。

実画面の例: [charge側面](visuals/apex-reference-match-native-charge.png)、[blade combo](visuals/apex-reference-match-native-blade-combo.png)、[phase change](visuals/apex-reference-match-native-phase-change.png)、[death後半](visuals/apex-reference-match-native-death.png)。最小のcharge補正は実GeckoLib描画でも適用されました。

続いてNoAIを設定しない別個体をSurvivalのプレイヤーに索敵させ、通常CombatControllerが選んだ追跡・近接・charge・feint・blade combo・ranged・shockwave・teleport・mirage・jump/slam・APEX EXECUTIONを確認しました。観測のためプレイヤーにResistanceを付け、段階確認には管理コマンドでHPと被ダメージを調整しました。AI状態を直接強制するコードや新しい攻撃経路は追加していません。

- Phase 1のHP401から10ダメージでPhase 2へ移行し、既存phase_changeが再生されました。Phase 2のHP201から10ダメージでPhase 3へ入り、既存の突入回復で191→251HPになりました。
- Phase 3の継続回復は263→299→300HPへ増え、300で停止しました。APEX EXECUTIONのSPECIAL中はhealing=INACTIVE、終了後にWARNINGへ戻りました。
- 実JUMPの警告・SlamTarget・ActualLanding・DamageCenterは`(11.707,120.000,-11.942)`で一致し、LandingError=0.000でした。別のJUMPでもLandingError=0.000を記録しました。
- 実shockwaveとEXECUTIONの警告中心/ダメージ中心・半径はログ上で一致しました。
- 戦闘開始でRECORDSカテゴリのBGM instance=1が開始し、通常BGM抑制がSUPPRESSになりました。戦闘中に追加のstartはありませんでした。実個体を死亡させた同tickで通常BGMをRELEASEし、instance=1をreason=no_encounterで停止しました。

Save and Quit to Title → Quit Gameで正常終了0、`runClient`はBUILD SUCCESSFUL（10m16s）でした。終了後にoptions・common/client configの3ファイルを元のバイト列へ戻し、開始前とSHA-256が一致しました。

クライアントログ、実入力と各フレームの時刻、通常AIの観測ログ、116枚のraw画像はignoredの`build/reference1152/client/`へ保存しています。今回の無音化/音量/曲ループ仕様を変更した検証ではありません。BGMを完全に1周させた再ループの音響比較やTaCZ実銃射撃は、この外観作業の実クライアント確認とは別です。

## Minecraft実クライアント（TaCZあり）

`./gradlew runClient -PtaczDir=/workspace/.tools/optional-mods -PquickPlayWorld=ApexReference1152TaCZ`を実行しました。TaCZ 1.1.8 hotfix2と既定gunpackを読み込み、既存のoptional GunFire/GunHitリスナー登録が実ログに出ました。私用コピーの同じ条件で正面・側面・背面・45度を撮影し、TaCZなしと同じモデル・細い発光・各関節の接続を実画面で確認しました。19名称の正面クリップと6シーケンスの側面クリップを4回ずつ撮影（100枚）しました。

通常AIの新しい個体でもPhase 1→2→3を実被ダメージで移行させ、Phase 3突入HP191→251、ノーダメージ後の継続回復→300上限、APEX EXECUTION中の回復停止→終了後再開を確認しました。通常AIが選んだcharge、blade combo、shockwave、jump/slam、EXECUTIONがログに出ています。TaCZありのJUMPでも警告中心/着地点/実ダメージ中心が一致し、LandingError=0.000でした。BossBarと回復演出は実ウィンドウで表示されました。

BGMはRECORDS/音量0.85/looping=trueのinstance=1だけが始まり、通常BGMを抑制しました。新しい個体の死亡と同tickでinstance=1を停止し、通常BGM抑制を解除しました。Save and Quit to Title → Quit Gameで正常終了0、runClientはBUILD SUCCESSFUL（5m45s）でした。今回の実銃の発砲操作は行っていません。TaCZの銃イベント・専用サーバーの自動テスト結果は別の全体検証報告に記載します。

## 検証後の復元・証跡

2つのクライアントを正常終了してから、`run/options.txt`、`run/config/apexpredator-common.toml`、`run/config/apexpredator-client.toml`を検証前の完全なバイト列へ復元しました。開始前/終了後のSHA-256は3ファイルとも一致しました。正式`src/main/`と2つの公式bbmodelから保存した120ファイルのSHA-256も、実クライアント確認の前後で全件一致しました。デバッグ観測のための設定変更は残っていません。検証は私用ワールドコピー2つだけで実施しています。

`build/reference1152/client/client-audit.json`に両runClientの正常終了、19名称、Phase 1–3、BGM単一起動/死亡停止、実slamログを保存しました。`product-before.json`/`product-after.json`と`restoration-before.json`/`restoration-after-all.json`が復元の記録です。生の実入力ログ・画像・WAVE音声出力も同じignoredディレクトリにあります。OpenALのWAVE出力は実ゲームが生成した非無音データで、BGM以外のSEも含みます。

必須の`docs/visuals/apex-reference-match-ingame.png`は1280×720の実Minecraft画像で、SHA-256は`a827820be38f321a558a77e92725c2c55748ad847361ec608cf0051d3487154e`です。BlockbenchとMinecraftはいずれも仮想画面・ソフトウェアGPU上の実アプリです。ハードウェアGPUの性能や全連続フレームの無貫通、曲全体1周後の音響比較を保証するものではありません。
