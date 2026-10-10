# Apex Predator 1.5.1 — Blockbench編集と独立クールダウンの検証

2026-10-11（日本時間）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17.0.12で確認しました。基準は1.5.0コミット `296d0276ac33c04ad3d948d84f1527ca170cdf7b` です。[1.5.0の検証記録](docs/VALIDATION-1.5.0.md)を履歴として保存しています。

## 変更と維持

Apex本体とApex Fangの正式なBlockbench編集元、埋め込みPNG、専用書き出しCLI、編集手順を追加しました。Predator Dashだけを既定40tick（2秒）へ短縮し、Apex Slashの200tick（10秒）と独立させました。

既存のボス形状・テクスチャ・37アニメーション、戦闘AI、DamageTracker、フェーズとPhase 3回復、APEX EXECUTION、円形判定とジャンプスラム、TaCZ任意連携、BossBar、召喚儀式、レシピ、ドロップ、BGMを引き継ぎます。BGMの既定音量0.85と通信プロトコル3は維持しています。

## ビルドとテスト

| 実行 | 結果 |
| --- | --- |
| `./gradlew build` | 成功、終了コード0、25秒 |
| JUnit | 82件、失敗・エラー・スキップ0 |
| 本体Blockbench Pythonテスト | 13件成功 |
| Fang Blockbench Pythonテスト | 7件成功 |
| `./gradlew runGameTestServer` | Forge Dedicated Server、必須88件すべて成功、正常終了 |
| `./gradlew test runGameTestServer -PtaczDir=/workspace/.tools/optional-mods` | TaCZ 1.1.8-hotfix2あり、必須91件すべて成功、正常終了 |

最終のTaCZあり・なしは同一の修正後コードで実行しました。従来のJUnit78件にBlockbench資源検証4件、GameTest86件に独立クールダウンと標準表示の試験2件を加えました。TaCZありでは既存の実イベント3件も実行します。既存の召喚・レシピ・ドロップ・通常剣攻撃・修理・エンチャント・壁と崖の試験も通過しています。

## Blockbenchの正式編集元

| 編集元 | 実アプリで確認した構成 |
| --- | --- |
| [apex.bbmodel](models/blockbench/apex.bbmodel) | GeckoLib Entity、28ボーン、333キューブ、37クリップ、5,316キーフレーム |
| [apex_fang.bbmodel](models/blockbench/apex_fang.bbmodel) | Java Block/Item、56キューブ、18グループ、8表示設定 |

公式Blockbench **5.2.2 Linux Desktop**と公式GeckoLib Animation Utils **4.2.5**で、実際の読み込み・保存・Edit・Paint・Display・書き出しを確認しました。本体の512×512通常/発光PNG、Fangの128×128 PNGをプロジェクトへ埋め込み、外部の絶対パスへ依存しません。

検証用コピーで本体のhead pivot・キューブ寸法・UV、Fangの刃先寸法・UV、Paintの1ピクセルを変更し、保存した `.bbmodel` から専用CLIで変更内容が出力されることを確認しました。旧生成器やランタイムJSONを読み直して元の形状へ戻す方式にはしていません。配布するゲーム用モデル・PNGへ検証用編集は反映していません。

Fangの発光は名前付き4グループから9キューブのForge fullbrightを再出力します。通常のBlockbench保存後も発光宣言と左右の一人称・三人称、GUI、ground、fixed、headの8表示設定を維持します。旧Python生成器は正式 `.bbmodel` がある状態での既定上書きを拒否します。

### アニメーション時刻の保持

既存の `apex.animation.json` は変更していません。37クリップ全体の名前・長さ・ループ・全5,316キーフレームの時刻・値・easingを、Blockbenchで再保存したプロジェクトと照合しました。ファイルSHA-256は従来と同じ `0218e1e3c8ecaa6dea5cc8bf6913b2a29eeda67c2c5f422df9fab975a9d21744` です。

Blockbench標準のAnimation Exportはsnappingへ時刻を丸め、chargeの `0.0563` 秒などを変えてしまいます。専用 `export-animations` は保存済みプロジェクトの生の時刻から別ファイルへ出力し、この丸めを避けます。既存ランタイムアニメーションへの直接出力も拒否します。通常のgeometry/PNG書き出しはアニメーションを更新しません。

詳細: [正式編集手順](models/blockbench/README.md)・[Fang編集と発光](docs/BLOCKBENCH-FANG.md)・[Blockbench実機記録と画像](docs/VALIDATION-1.5.1-blockbench.md)。

## DashとSlashの判定

Dashは2秒（40tick）、Slashは10秒（200tick）の個別期限をサーバー側でプレイヤーUUIDごとに管理します。別のFangへ持ち替えても期限は残り、片方の発動は他方の期限を変更しません。common config `apexFangDashCooldownTicks` は既定40、設定範囲1〜1200です。既存の能力実行中ロックは維持し、同時発動しません。

標準ホットバーは通常時にDash、スニーク時にSlashの残り時間を表示します。バニラのサーバー用item gateは2期限の短い方を使い、最終受付は選択した能力の期限で判定します。標準 `ClientboundCooldownPacket` と `ItemCooldowns` を使用し、安定したカウントダウンを毎tick再送しません。短いサーバー用gateの終了が、選択中の長いSlash表示を消さないことも確認しています。

GameTestは実ServerPlayerとnative `ServerPlayerGameMode.useItem` を使い、**Dashの39tick拒否・40tick受付、Slashの199tick拒否・200tick受付**を厳密に確認します。拒否時の耐久不消費、持ち替え、独立期限、各対象への1回命中、標準表示の選択変更・終了・清掃も確認しています。移動距離7ブロック、Dashの8tick動作、Slashの9ブロック/10tick、ダメージ・耐久・衝突処理は変更していません。

### fixtureの同一tick順序を修正

初回のTaCZあり試験では91件中90件が成功し、40tick後のDash再受付だけ失敗しました。GameTestは `onEachTick` と `runAfterDelay` を同じhash mapで実行し、同一tick内の順序を保証しません。開始はnative `doTick()` 後、再受付は `doTick()` 前になると、world時刻は40tick進んでもnativeクールダウンは39tickしか進まず、item gateが先に拒否していました。

fixtureにgameTime単位の実Player tickガードを追加し、通常tickとitem-useの前から同じ `advancePlayer` を呼びます。毎world tickにつき実 `player.doTick()` は1回だけです。物理処理・Forgeイベント・nativeクールダウンを維持し、39/40・199/200の条件を緩めず、クールダウンを手動解除せずに修正しました。製品の能力コードはこのfixture修正のために変更していません。その後、TaCZあり91件・なし88件の全件が成功しました。

テスト: [FangGameTests](src/main/java/dev/apexpredator/gametest/FangGameTests.java)・[fixture](src/main/java/dev/apexpredator/gametest/ProgressionGameTestArena.java)・[Blockbench資源JUnit](src/test/java/dev/apexpredator/assets/BlockbenchAssetTest.java)。操作と性能は[進行・武器ガイド](docs/PROGRESSION.md)を参照してください。

## 実Minecraftクライアント

TaCZなし・ありで、それぞれ実 `runClient`、仮想画面へのキーボード/マウス入力、Save and Quit → Quit Gameを実施し、終了コード0を確認しました。複製した開発ワールドでSurvivalの能力・儀式を確認しています。

- 両方でDashは40tick設定、8tickで7.000ブロック、足元Y120を維持。約0.9秒後の別Fangでの再入力は拒否、約2.4秒後は受付。
- Slashは200tick設定、10tick・波9.000ブロック・プレイヤー移動0.000ブロック。Slash待機中もreadyのDashを使え、Dash再使用でSlash期限は延長しません。
- Shiftによる標準表示の切替、Slashの早期拒否と10秒後の再使用、拒否時の耐久不消費を確認。
- Fangの一人称左右・三人称左右と両手・インベントリ・ツールチップ・実ItemEntityを描画。Beaconは100tick後に1個消費して召喚し、BossBarと既存BGMが開始。
- BGMはRECORDS、音量0.85、1インスタンス、実チャネル再生、通常音楽SUPPRESSを確認。プレイヤー死亡または交戦解除で停止し、通常音楽をRELEASE。
- `options.txt` とcommon/client設定は確認前へバイト単位・SHA-256一致で復元し、`debug=false`を戻しました。

[クライアント記録と全画像](docs/VALIDATION-1.5.1-client.md)に実測を記載しています。[Fang一人称](docs/visuals/apex-1.5.1-fang-first-person-right.png)・[三人称](docs/visuals/apex-1.5.1-fang-third-person-both.png)・[標準クールダウン](docs/visuals/apex-1.5.1-dash-cooldown.png)・[召喚](docs/visuals/apex-1.5.1-beacon-arrival.png)・[ゲーム内Apex](docs/visuals/apex-1.5.1-ingame.png)を参照してください。

今回は実銃のGUI再射撃と変更していない曲の全長ループ試験は繰り返していません。TaCZの既存実イベント3件と[以前の全曲ループ検証](docs/VALIDATION-1.3.0-music-priority.md)を引き継ぎます。画面検証は仮想画面・ソフトウェア描画です。

## 既存ファイルとjarの監査

1.5.0の既存Java/資源130ファイルを比較し、122ファイルがバイト単位で一致しました。変更はconfig、Fang能力、Fangの説明コメント、GameTestとfixture、mod情報、英語・日本語の文言の8ファイルです。既存の戦闘AI・DamageTracker・Phase/回復・TaCZ・BGM・モデル・PNG・アニメーションは維持しています。

配布jarは旧202エントリから203エントリになり、191エントリが完全一致、11エントリが変更、`PredatorWeaponAbilities$Cooldowns.class` の1エントリが追加、削除0です。変更はManifest・mod情報・文言・config・能力・GameTest関連に限定されます。CRCは正常で、`build/libs` と `dist` のjarは完全一致しました。

## 最終jar

- `build/libs/apexpredator-1.20.1-1.5.1.jar`
- `dist/apexpredator-1.20.1-1.5.1.jar`
- `dist/apexpredator-1.20.1-1.5.1.jar.sha256`

サイズ: **4,389,041 bytes**。SHA-256: `1a22ecad06799cd8daed8f5dddaafa216bd26bf71dcbf3640abaadad2da7d4f9`。

編集用ZIPは `dist/apexpredator-blockbench-1.5.1.zip`、**641,877 bytes**、SHA-256: `b8954c37f12e6451776d5b71d4edb82684154a70aa08e4ba4bf122266c8833b6` です。本体/Fangの編集元・専用CLI・手順を収録し、Minecraftへ入れるjarとは分けて配布します。

**公開済み: [1.5.1 jarをダウンロード](https://github.com/schnauzer0927-boop/minecraftMOD/releases/download/v1.5.1/apexpredator-1.20.1-1.5.1.jar) / [Blockbench編集用ZIP](https://github.com/schnauzer0927-boop/minecraftMOD/releases/download/v1.5.1/apexpredator-blockbench-1.5.1.zip)**。両方を認証なしの公開リンクから実際に取得し、HTTP 200・`Content-Disposition: attachment`・上記サイズとSHA-256・CRC正常・`dist`との完全一致を確認しました。

[Apex Predator 1.5.1 Release](https://github.com/schnauzer0927-boop/minecraftMOD/releases/tag/v1.5.1)へ両ファイルと各SHA-256を添付しました。[配布ジョブ](https://github.com/schnauzer0927-boop/minecraftMOD/actions/runs/38069256563)は固定SHA・サイズ・CRC・MODバージョンと編集元収録を検証し、全手順が成功しました。Releaseのタグ`v1.5.1`は配布コミット`f608dce47699eb9fbe46b5c5b52848eb58418091`を指します。

再ビルドには[1.5.1ソース一式](https://github.com/schnauzer0927-boop/minecraftMOD/archive/refs/tags/v1.5.1.zip)を使用します。ソース・画面・配布ファイルは`feature/apex-blockbench-1.5.1`へ保存し、mainへはマージしていません。公開リンクの実ダウンロード記録は`build/blockbench151/download-check/release-download.json`です。

GeckoLib 4.4.9 Forge版が必要、TaCZは任意です。[導入案内](dist/README.md)を参照してください。Blockbench編集ファイルはゲーム用jarとは別に配布します。

生ログはignoredの `build/blockbench151/` に保存しています。最終サーバーログは `gametest-tacz-ordered.log` / `gametest-plain-ordered.log`、ビルドは `final-build.log`、Blockbench実機記録は `native/`、クライアント実測と設定復元は `client/` です。
