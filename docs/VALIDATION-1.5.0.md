# Apex Predator 1.5.0 — 召喚・武器の検証

2026-10-10（UTC）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17で実施しました。基準は1.4.0配布コミット `9ac1a4b` です。[1.4.0の検証記録](VALIDATION-1.4.0.md)も保存しています。

## 変更と維持

Apex Beaconの100tick召喚儀式、Apex Fangの通常剣攻撃・Predator Dash・Apex Slash、作業台レシピ、専用アイテムモデル・テクスチャ、3つの進捗を追加しました。[素材と操作方法](PROGRESSION.md)を参照してください。

既存の戦闘AI・DamageTracker・フェーズと回復・APEX EXECUTION・円形判定とジャンプスラム・TaCZ任意連携・BossBar・BGM・ボスモデル・37アニメーションを維持しています。BGMの既定音量は0.85、通信プロトコルは3です。

## ビルドとサーバー

| 実行 | 結果 |
| --- | --- |
| `./gradlew build` | 成功、終了コード0、16秒 |
| JUnit | 78件、失敗・エラー・スキップ0 |
| `./gradlew runGameTestServer` | Forge Dedicated GameTest Server、必須86件すべて成功、正常終了 |
| `./gradlew test runGameTestServer -PtaczDir=/workspace/.tools/optional-mods` | TaCZ 1.1.8-hotfix2あり、必須89件すべて成功、正常終了 |

最終のTaCZあり・なしは同一の製品コードとテストで実行しました。既存63件の通常GameTestと72件のJUnitを変更せず、新規23件のGameTest・6件の資源JUnitを追加しました。TaCZありでは従来の実イベント3件も実行します。

## 新規GameTestが確認すること

登録されたアイテムの実`useOn`/`use`、RecipeManagerとCraftingMenu、通常のServerPlayer・Player移動処理、金床、ダメージとドロップを使用します。新規fixtureは256ブロック以上離し、製品の64ブロック召喚除外をそのまま検証します。終了・サーバー停止時に所有したエンティティ、ブロック、チャンク読み込み状態、試験専用Forgeリスナーを片付けます。

- レシピ2件: 指定の3×3配置・実素材・実結果、素材不足の拒否、取り出し時の全9スロット消費。
- ドロップ1件: Looting III装備プレイヤーがApexを倒し、実ItemEntityのCoreがちょうど1個。
- Beacon12件: 99tick時点では未成立、100tick後の成功時だけ予約Beacon1個を消費、Creative不消費、持ち替えとインベントリ移動の継続、生存Apex・他儀式・高さ不足の拒否、床破壊・Beacon喪失・ログアウト・次元移動の取り消し、Forgeのスポーンキャンセル、追加エンティティのイベント中に床が破壊された場合の巻き戻しと支払い保護。
- Fang8件: プレイヤーの合計攻撃力16・速度1.6・耐久3000、Netherite修理、普通の剣エンチャントと実金床、実通常攻撃の16ダメージと耐久1、Dash7ブロック・対象ごと1回・耐久4・共有200tickクールダウン、壁・崖で停止、Slash9ブロック・複数対象と対象ごと1回・耐久6・壁と距離境界、非敵対動物の除外、小さな所有者位置補正からの正常終了、補正中でも短い足場切れ目を越えないこと。

テストソース: [ProgressionGameTests](../src/main/java/dev/apexpredator/gametest/ProgressionGameTests.java)、[FangGameTests](../src/main/java/dev/apexpredator/gametest/FangGameTests.java)、[資源検証](../src/test/java/dev/apexpredator/assets/ProgressionAssetTest.java)。

### 移動の実測と修正

実Playerの移動では、ダッシュ後に垂直速度を0にすると、次の移動処理で足元に床があっても`onGround`が解除され、最初の0.875ブロックで終了していました。ダッシュ中だけ小さな下向き速度を保ち、通常の床との衝突で接地を確認します。

実クライアントの位置パケットが遅れたときの差は、直前の確定位置と比較します。補正で大きな一歩になっても、足場を0.2ブロック以下の間隔で経路全体にわたり検査し、最初の足場切れ目で止めます。通常の重力・壁との衝突・接地条件を維持します。

| 最終GameTest実測 | 結果 |
| --- | --- |
| 平坦地Dash / 小さな所有者位置補正 | 各7.000ブロック、8tick、敵2体に各1回 |
| 壁 | 3.200ブロックで停止、壁の向こうの敵は無傷 |
| 崖 / 補正経路の短い足場切れ目 | 各3.201ブロックで停止、接地・通常の重力を維持 |
| 開けたSlash | 9.000ブロック、10tick |
| 壁に向かうSlash | 5.400ブロックで停止、壁の向こうの敵は無傷 |

## 実クライアント

仮想画面と実キーボード・マウス入力で、複製した開発ワールドを使って確認しました。TaCZなしの初回と、TaCZありの最終版で`runClient`が起動し、Save and Quit → Quit Gameで正常終了しました。サーバー試験の擬似入力だけに頼らず、実所有者の位置パケットを伴う移動も確認しています。

- TaCZなし: Fangの一人称・三人称・インベントリ表示、ツールチップ、実右クリックDash、Beaconの100tick成立と1個消費、BossBarと専用BGM。
- TaCZあり最終版: 平坦地Dash7.000ブロック・8tickと途中のF3座標、壁で3.200ブロック・4tick停止、崖で3.201ブロック・4tick停止してY120を維持。
- 実壁試験の手前の敵は20 → 8 HP（防具0、12ダメージを1回）、壁の向こうの敵は20 HPを維持。
- スニークを0.3秒保持して右クリックすると実`APEX_SLASH`となり、10tick・波の距離9.000ブロック。8ブロック先の敵は20 → 10 HP、プレイヤーはその場に留まりました。橙色の斬撃弧と実被弾を撮影しています。
- 最終版Beaconは同じ保存地点で100tick後に成立し1個消費。BossBar、通常MUSICのSUPPRESS、専用曲のRECORDS・音量0.85・ループ・実チャネル再生を確認し、交戦解除時のRELEASE/stopも確認しました。
- `options.txt`、common設定、client設定は、それぞれの確認前の内容へSHA-256とバイト単位で復元。音量設定を維持し、最終common設定は`debug=false`です。

実ゲーム画像: [Fang一人称](visuals/apex-1.5-fang-first-person.png)・[三人称](visuals/apex-1.5-fang-third-person.png)・[インベントリ](visuals/apex-1.5-fang-inventory.png)・[Dash](visuals/apex-1.5-predator-dash.png)・[壁](visuals/apex-1.5-dash-wall.png)・[崖](visuals/apex-1.5-dash-cliff.png)・[真のSlash](visuals/apex-1.5-apex-slash.png)・[被弾](visuals/apex-1.5-apex-slash-hit.png)・[召喚儀式](visuals/apex-1.5-beacon-ritual.png)・[召喚成立](visuals/apex-1.5-beacon-arrival.png)・[ゲーム内Apex](visuals/apex-1.5-ingame.png)・[TaCZ付きタイトル](visuals/apex-1.5-tacz-title.png)。

## 既存ファイルの監査

1.4.0の106個の既存Java/資源ファイルとSHA-256を比較しました。変更はメインクラスへのクリエイティブタブ登録、登録クラスへの2アイテム追加、mod情報、英語・日本語の追加文言の5ファイルだけです。**保護対象101ファイルはバイト単位で一致**します。

配布jarも1.4.0の全166エントリと比較しました。変更はManifest・mods.toml・2言語ファイル・メインクラス・登録クラスの6エントリだけで、**他160エントリは完全一致**します。新規36エントリを加え、合計202エントリです。CRC正常・重複なし、GeckoLib/TaCZの依存クラスを同梱していません。

既存アニメーションは37クリップ。ファイル全体のSHA-256は従来と同じ `0218e1e3c8ecaa6dea5cc8bf6913b2a29eeda67c2c5f422df9fab975a9d21744` です。

## 配布jar

- `build/libs/apexpredator-1.20.1-1.5.0.jar`
- `dist/apexpredator-1.20.1-1.5.0.jar`
- `dist/apexpredator-1.20.1-1.5.0.jar.sha256`

サイズ: **4,381,770 bytes**。`build/libs`と`dist`は完全一致します。

SHA-256: `04613fa9fb8fb346966c0f93cfc641775e44581d31c718dae45e1fca13232399`

GeckoLib 4.4.9 Forge版が必要、TaCZは任意です。[導入とダウンロード](../dist/README.md)を参照してください。

生ログとjar監査結果はignoredの`build/progression150/`に保存しています。最終ログは`plain-server-callback-final.log`、`tacz-server-callback-final.log`、`final-build-native.log`、`jar-audit.json`です。GUIは`client/client-first.log`と`client/client-tacz.log`、実測と復元は`client/gui-verification.json`・`client/restoration.json`に分けて保存しています。画面確認はソフトウェア描画を使用し、BGM全曲のループ記録は[既存の検証](VALIDATION-1.3.0-music-priority.md)を引き継ぎます。
