# Apex Predator 1.5.1 — 実クライアント確認

2026-10-10（UTC）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17.0.12で確認しました。完成した同一の製品コードを使い、TaCZなし・TaCZ 1.1.8-hotfix2ありの両方で`runClient`を起動しました。確認中に製品コード・テストコード・モデル・テクスチャ・アニメーションを変更していません。

| 実行 | 結果 |
| --- | --- |
| `./gradlew runClient -PquickPlayWorld=ApexBlockbench151Plain` | ワールドへ入場、実入力確認後Save and Quit → Quit Game、終了0、`BUILD SUCCESSFUL in 8m 3s` |
| `./gradlew runClient -PtaczDir=/workspace/.tools/optional-mods -PquickPlayWorld=ApexBlockbench151TaCZ` | 同じ確認と正常保存終了、終了0、`BUILD SUCCESSFUL in 5m 39s` |

過去の開発ワールドを別の2フォルダへ複製しました。操作は実際のMinecraftウィンドウへのX11キーボード・マウス入力です。準備用の床・アイテム配置、位置や耐久値の確認にはゲーム内コマンドを使用し、能力とBeaconは右クリック・スニーク入力で発動しました。

## Predator DashとApex Slash

両クライアントでSurvivalの実プレイヤーを使用しました。

- 最初のDashのログは`cooldown=40`。8tickで7.000ブロック移動し、足元Y120を維持しました。
- 発動後0.9〜0.95秒で別のFangスタックへ持ち替えて右クリックしても再発動しませんでした。標準ホットバーのクールダウン表示と拒否メッセージを確認しました。2.35〜2.4秒後はその別スタックで再び7.000ブロックのDashが成立しました。
- Shift + 右クリックは`APEX_SLASH`、`cooldown=200`、10tick、斬撃波9.000ブロック、プレイヤーの移動0.000ブロックです。
- Slashの残り175tick（TaCZでは174tick）がある状態でも、Shiftを離してreadyのDashを発動できました。2秒後にもう一度Dashを使用しても、元のSlashの期限は延長されませんでした。
- ShiftでSlashを選ぶと残りのSlash用表示、Shiftを離すとDash用表示へ切り替わりました。Dashのサーバー用ゲートが期限切れになっても、選択中のSlashの標準表示は残りました（TaCZのログ: `dashRemaining=0 slashRemaining=85 nativeGate=0 overlay=85`）。
- TaCZでは、Dashがready・サーバー用ゲート0の状態で、10秒前のSlash再入力が拒否されることも確認しました。その後10秒経過後にSlashを再使用できました。
- TaCZの実NBT確認では、最初のFangの耐久消費は4、別スタックは24（Dash3回×4 + Slash2回×6）、最終位置は`[28.5,120.0,0.5]`でした。拒否された入力による追加消費はありませんでした。

GUIの秒数は実入力時刻による確認です。厳密な39/40tick・199/200tickの境界は、専用の実サーバーGameTestで確認しています。ホットバー表示は実際のMinecraftのItemCooldownsで、描画を加工していません。

画像: [Dashの拒否と標準表示](visuals/apex-1.5.1-dash-cooldown.png)・[readyのDashがあってもSlashは拒否](visuals/apex-1.5.1-slash-cooldown.png)・[Slash待機中のDash ready](visuals/apex-1.5.1-dash-ready-during-slash-cooldown.png)。

## アイテムとボスの描画

ゲーム内でFangの右手・左手の一人称、右手・左手・両手の三人称、インベントリ、実際にQキーで落としたItemEntityの描画を確認しました。TaCZありでも両手表示とインベントリは同じ専用モデルを描画しています。ツールチップは16 Attack Damage / 1.6 Attack Speed、Dash 2秒 / Slash 10秒です。

画像: [一人称右](visuals/apex-1.5.1-fang-first-person-right.png)・[一人称左](visuals/apex-1.5.1-fang-first-person-left.png)・[三人称右](visuals/apex-1.5.1-fang-third-person-right.png)・[三人称左](visuals/apex-1.5.1-fang-third-person-left.png)・[両手](visuals/apex-1.5.1-fang-third-person-both.png)・[インベントリとツールチップ](visuals/apex-1.5.1-fang-inventory.png)・[地面](visuals/apex-1.5.1-fang-ground.png)・[ゲーム内ApexとFang](visuals/apex-1.5.1-ingame.png)。

静止写真のApexは実際の召喚と戦闘の観測後に、複製ワールド内の個体だけをNoAIにしました。ボスとFangの写真はリスポーン後のCreative、能力と儀式の成立確認はSurvivalです。37アニメーション全体の再制作や、新しい頭部・ボス形状の変更はしていません。

## Beacon、BossBar、既存BGM

両方で右クリックした地点`(0.5,120,2.5)`を固定して儀式を開始し、100tick後にBeaconを1個消費してApexが出現しました。警告円・到着時粒子・BossBarを実画面で確認しました。

- TaCZなし: 16:34:42に専用BGMの1インスタンスが開始。RECORDS、音量0.85、pitch 1.0、looping / playing / channel_active、通常音楽のSUPPRESSを確認しました。通常戦闘でプレイヤーが死亡した16:34:49に`reason=player_unavailable`で停止し、通常音楽をRELEASEしました。
- TaCZあり: 16:42:15に同じ専用BGMの1インスタンスが開始。戦闘の観測後に写真用NoAIを設定した16:42:19に`reason=no_encounter`で停止し、通常音楽をRELEASEしました。この短い演出確認だけはプレイヤーにResistance Vを付けて撮影時間を確保しました。能力確認には追加していません。

実OpenALのWAVE出力も記録しました（stereo / 44.1kHz、plain 438.08秒、TaCZ 293.66秒、各非ゼロ音声）。これは実クライアントの音声出力の確認であり、今回は変更していない曲の全長ループ試験を繰り返していません。以前の[全曲ループ検証](VALIDATION-1.3.0-music-priority.md)を引き継ぎます。

画像: [召喚儀式](visuals/apex-1.5.1-beacon-ritual.png)・[召喚成立](visuals/apex-1.5.1-beacon-arrival.png)・[実プレイヤー死亡](visuals/apex-1.5.1-player-death.png)・[TaCZ付きタイトル](visuals/apex-1.5.1-tacz-title.png)。TaCZの任意gunfire / gun-hitリスナー登録も起動ログで確認しました。今回のGUIでは実銃の再射撃試験はせず、Dedicated GameTestの既存TaCZ実イベント3件で確認しています。

## 復元と記録

`run/options.txt`、`run/config/apexpredator-common.toml`、`run/config/apexpredator-client.toml`を、開始前の内容へバイト単位・SHA-256一致で復元しました。plain終了時にも復元し、TaCZ正常終了後にも最終復元しています。元の音量設定を変更せず、commonの`debug=false`を戻しました。

実画面のPNGは加工せず、同じSHA-256で`docs/visuals/`へコピーしました。仮想画面・ソフトウェア描画の検証なので、ハードウェアGPUでの滑らかさの測定ではありません。

生ログ、入力時刻、PNG原本、音声と復元の記録はignoredの`build/blockbench151/client/`に保存しています。`client-plain.log` / `client-tacz.log`、各`*-dash-sequence.log` / `*-independent-sequence.log`、`native-timeline.jsonl`、`gui-verification.json`、`screenshots.json`、`audio.json`、`restoration-after-plain.json` / `restoration-final.json`を参照してください。
