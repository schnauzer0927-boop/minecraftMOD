# Apex Predator 1.15.2 — HUNTERモデルの再構築と検証

2026年10月11日（日本時間）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17.0.12。MODバージョンは依頼どおり **1.15.2** です。旧基準は1.5.1コミット `3ece45a6f9b6a317bd9c31b5732a3ac9258a0f7e`。[旧版の検証記録](docs/VALIDATION-1.5.1.md)を保存しています。

## 全身モデルと正式編集元

今回のHUNTER設計図の正面・側面・背面・頭・前腕・脚・DASH各パネルを使い、全身のキューブ配置とUVを新規再構築しました。旧333キューブと同じボーン・同じ回転後8頂点を持つ新キューブは0個でした。

| 対象 | 最終構成 |
| --- | --- |
| [正式編集元](models/blockbench/apex.bbmodel) | GeckoLib Entity、498個の意味のある名前付きキューブ、28ボーン、37クリップ、5,316キーフレーム |
| [実行用geometry](src/main/resources/assets/apexpredator/geo/apex.geo.json) | 編集元から専用CLIで書き出し、全寸法・回転・pivot・6面UVを一致検証 |
| [通常装甲](src/main/resources/assets/apexpredator/textures/entity/apex.png) | 1024×1024、黒い内部・ガンメタル・銀の主装甲、陰影・エッジ・細い擦れ |
| [発光](src/main/resources/assets/apexpredator/textures/entity/apex_glow.png) | 1024×1024、目・胸・首・腹部・関節・刃・背骨の細いオレンジ |
| [回復時発光](src/main/resources/assets/apexpredator/textures/entity/apex_healing_glow.png) | 1024×1024、新UVと同じ位置のコア・関節。既存回復描画コードを維持 |

通常・発光・回復PNGの全バイトが埋め込み画像と一致し、発光と回復の画素が通常装甲の透明部分へ出ないことを確認しました。77枚の薄い先細り装甲には三角形のアルファと実際の斜辺geometryを組み合わせ、表裏で閉じた刃にしています。数値の全キューブ包絡は約2.729ブロックです。透明な角を含む包絡と、実際に見える輪郭の精度は区別しています。

頭は銀色の細い中央楔・黒い頬・小さい顎・細いセンサー・多段後頭フィンへ変更。胸は5段のV字装甲、腹部は絞った内部機構、腕は長い3層の湾曲刃、背中は後方へ流れる刃、脚はdigitigradeと3本の前爪へ作り直しました。

首・肩・肘・手首・腰・股関節・膝・足首に親子側の黒い軸、カバー、リング、重なり装甲を配置し、踵の後ろ爪も足裏フレームへ接続しています。28ボーン名と親子関係を維持し、肩・上腕・前腕・刃・手・太腿・下腿・足・左右背部刃の9組、**18か所のpivot**を調整しました。head/neck/face/eyeのpivotは維持しています。

正式編集元がgeometry・PNGの出力元です。旧generatorやruntime JSONから編集後の形を作り直す方式は使いません。通常のビルドもgeneratorを実行しません。[編集・書き出し手順](models/blockbench/README.md)と[リグ検証の詳細](docs/VALIDATION-1.15.2-rig.md)に記載しています。

## 設計図との比較と突進姿勢

初稿を完成扱いにせず、実モデルの3方向を繰り返し表示して修正しました。棒状フィン、矩形刃、フィンの穴、頭頂アンテナ、小さい足、浮く後ろ爪を順に改善しています。[独立レビュー](docs/REFERENCE-DESIGN-1.15.2.md)に判断と修正を記録しました。

- [正面](docs/visuals/apex-reference-match-front.png) / [側面](docs/visuals/apex-reference-match-side.png) / [背面](docs/visuals/apex-reference-match-back.png) / [頭部](docs/visuals/apex-reference-match-head.png) / [45度](docs/visuals/apex-reference-match-45deg.png)
- [旧版との全身比較](docs/visuals/apex-reference-match-before-after.png) / [頭部の同角度・同距離比較](docs/visuals/apex-reference-match-head-comparison.png)
- [既存12ポーズ](docs/visuals/apex-reference-match-animation-poses.png) / [突進角度補正の前後比較](docs/visuals/apex-reference-match-charge-comparison.png)

これらは実行用geometry・UV・PNG・アニメーションから描いたソフトウェアレンダーです。設計図との比較にはチャットの添付画像を直接視認し、旧版との横並び比較には同じカメラで描いた実モデルを使用しました。BlockbenchとMinecraftの実画面は別の実機記録です。

native確認で元の突進が設計図より直立していたため、依頼で許可された最小角度補正を行いました。

| 対象 | 補正したX回転 | 維持 |
| --- | --- | --- |
| `charge_prepare`後半 | chest 28→40、neck −15→−20 | 開始値・全時刻・easing・他の変形 |
| `charge` | chest 38→53、neck −22→−30 | 全時刻・easing・他の変形 |

変更は**2クリップ・2ボーン・16個のX回転数値**だけです。全37クリップの名称・長さ・loop・全5,316時刻・easing、全position/scale/YZ回転、四肢の周期、残り35クリップの全値を維持しています。実行用ファイルの差分は16行だけで、攻撃発生tickとCombatControllerは変更していません。

旧全時刻、許可したX回転以外の全変形、未変更35クリップの3つの署名を別々に固定し、補正の範囲外の変更を拒否するテストを追加しました。最終animation SHA-256は `c12bf23e69775ab74fa4af038fe0611391a312743bdc3c66bb9b07c771b54a16`。37クリップ全バイト未変更とは報告しません。四肢を含む原図DASHとの完全同一ポーズを主張せず、既存の動きを維持した低い上体への最小補正です。

## ビルド・自動テスト

すべて上記の補正を反映した最終アセットで実行しました。

| 実行 | 結果 |
| --- | --- |
| `./gradlew build` | 成功、終了0、28秒 |
| JUnit | 84件、失敗・エラー・スキップ0 |
| 本体 / 新モデル / Fang Python | 15 + 6 + 7 = 28件、すべて成功 |
| `./gradlew runGameTestServer` | Dedicated Server必須88件、すべて成功、正常終了0 |
| `./gradlew test runGameTestServer -PtaczDir=/workspace/.tools/optional-mods` | TaCZ 1.1.8-hotfix2あり必須91件、すべて成功、正常終了0 |

既存AI・被ダメージ・フェーズ3回復・円形判定・固定slamTarget・専用BGM・儀式・レシピ・Coreドロップ・Fang攻撃・修理・エンチャント・独立Dash/Slash期限・壁と崖・TaCZ実イベント3件の試験を維持しています。今回の追加・更新試験は編集元とruntimeの全UV/寸法/回転/PNG、骨階層、密度、関節支持、37クリップの保護を対象とします。数値のモーション検証だけで、あらゆる見た目の隙間や干渉を保証したとは扱いません。

## 実BlockbenchとMinecraft

実Blockbench 5.2.2 / GeckoLib Animation Utils 4.2.5で、読み込み、再保存、shape/pivot/UV編集、3PNGのPaintと専用書き出しを確認しました。17クリップ×5時刻の85場面で全498キューブの実world matrixが有限でした。最終角度補正後も正式BBを再保存したコピーから、28骨/498キューブ/37クリップ/5,316フレーム/3PNGの一致checkが成功しました。

[実エディター画面](docs/visuals/apex-reference-match-blockbench.png) / [補正後の実突進プレビュー](docs/visuals/apex-reference-match-charge-blockbench.png)。検証専用コピーの編集は正式geometry/PNGへ混入していません。標準Animation Exportの時刻丸めを避け、専用raw exportの別ファイルで確認しています。実アプリ保存の1.69e-15程度の数値丸めは正式resourceへ書き戻していません。

実Minecraftの`runClient`をTaCZなし・ありの両方で実行し、Save and Quit → Quit Gameでいずれも正常終了0、BUILD SUCCESSFULでした。正面・側面・背面・45度に加え、idle / walk / run / sprint / charge / blade combo / slam / dodge / phase change / rage / deathを含む19クリップ名を確認しました。TaCZなしは29シーケンス116画像、ありは25シーケンス100画像です。既存の`/apex animate`による描画確認と、通常AIが選択する実戦の確認を区別しています。

通常AIでPhase 1→2→3、Phase 3突入の191→251HP回復、毎秒4HPと300HP上限、APEX EXECUTION中の回復停止・終了後再開を観測しました。両クライアントで実ジャンプスラムのLandingErrorは0.000、警告とダメージ中心が一致しました。BossBar、1個のRECORDS BGM instance、通常BGM抑止、ボス死亡と同tickの停止・抑止解除も確認しました。TaCZの実銃イベント3件は別途Dedicated Server試験で検証しています。

[実Minecraft画像](docs/visuals/apex-reference-match-ingame.png) / [charge](docs/visuals/apex-reference-match-native-charge.png) / [blade combo](docs/visuals/apex-reference-match-native-blade-combo.png) / [phase change](docs/visuals/apex-reference-match-native-phase-change.png) / [death](docs/visuals/apex-reference-match-native-death.png)。独立担当も代表36画像を実表示し、大きな関節離脱・刃の突き抜け・透明プレートの穴を認めませんでした。撮影時刻は同期・描画遅延を含み、連続全フレームの完全無貫通を主張するものではありません。

実機検証後はoptionsとcommon/client configの3ファイルを元のバイト列へ戻し、開始前後のSHA-256を一致確認しました。正式モデル・資産と全src/mainファイルも不変です。詳細は[実機確認記録](docs/VALIDATION-1.15.2-client.md)。画面検証は仮想画面・ソフトウェア描画で、ハードウェアGPUのFPS測定ではありません。

## 既存コードと配布jarの監査

基準135ファイル中125ファイルは完全一致、10ファイルだけ変更、削除0。変更は本体編集元、モデル、3PNG、許可された16角度、mod情報、編集説明、資産テスト2ファイルです。**src/main/javaの全ファイルが完全一致**しており、AI、瞬殺防止、フェーズ3回復、TaCZ、BGM、攻撃判定、Beacon/Core/Fangは維持しています。

旧jarと新jarはともに203エントリ。196エントリが全バイト同一、変更7エントリはManifest、mod情報、geometry、3PNG、限定animation補正だけ。全`.class`、データ、音源、Fang資源は同一です。追加・削除0、CRC正常、jar内の全assetsは最終ソース資源と一致しています。

## 最終配布ファイル

- `build/libs/apexpredator-1.20.1-1.15.2.jar`
- `dist/apexpredator-1.20.1-1.15.2.jar`
- `dist/apexpredator-1.20.1-1.15.2.jar.sha256`

両jarは完全一致、**4,703,599 bytes**、SHA-256 `91dfd4dd7352a76c75bb3115f6efee778cf221f69c91822a9911e9dfe5e10042`。GeckoLib 4.4.9 Forge版が必要、TaCZは任意です。

編集用`dist/apexpredator-blockbench-1.15.2.zip`は正式本体/Fangプロジェクト、3PNG、専用CLIと検証、手順、実モデル比較画像の28ファイルを収録しています。サイズ3,402,207 bytes、SHA-256 `30f3e1fa75f211627da2b2e7f6279f1efde0bce3c3d746cb3e9d736f1a86ece3`。独立2回の生成が同一で、単独展開先で28Pythonテスト、geometry/3PNG/Fangの出力、37クリップの全値、CRC、元ソース全バイト一致、保護先の上書き拒否を確認しました。Minecraftの`mods`に入れるのはjarで、編集用ZIPではありません。再ビルドには[タグのソース一式](https://github.com/schnauzer0927-boop/minecraftMOD/archive/refs/tags/v1.15.2.zip)が必要です。

生ログ・基準ファイル・ハッシュ監査はignoredの`build/reference1152/`へ保存しています。ビルド`build-final-angles.log`、サーバー`gametest-plain-final.log` / `gametest-tacz-final.log`、リグ`rig-audit.json`、16値差分`charge-candidate/promotion-audit.json`、ソース/jar`source-jar-audit.json`、正式モデル`final-model-hashes.json`、実機`native/` / `client/`を参照してください。
