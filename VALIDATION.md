# Apex Predator 1.3.0 — 音量と通常BGM抑止の検証

2026-10-10（JST）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17で実施しました。基準は音量0.45版 `38c1eeb9aeb513277386194666b9b1901d6d3f62`。バージョンは1.3.0のままです。

## 変更と維持

ボスBGMの既定音量を **0.45 → 0.85** へ増量しました。音声信号の振幅は約1.89倍、約+5.5 dBです。マスター・RECORDSカテゴリの音量調整と消音は維持します。

実際の交戦開始時に、既存のMUSICカテゴリを停止します。以降のMUSIC再生要求はクライアントのForge `PlaySoundEvent`で除外します。最後の交戦終了・ローカル死亡・距離離脱・ワールド変更で解除し、MusicManagerの通常の選曲・曲間待ち時間へ戻します。以前の曲を途中から直ちに再開する仕様ではありません。

抑止状態はボスBGMの設定・音量・チャネル復旧から独立しています。`bossMusic=false`やMASTER/RECORDSの消音でも、交戦中に通常BGMが割り込みません。MUSIC以外のカテゴリ、設定画面の音量値、専用曲のループ・ピッチ・複数ボス共有は維持しています。

既存コードの変更は `ApexBossMusic.java`。追加はクライアント専用 `ApexMusicEvents.java` と `VanillaMusicSuppressionPolicy.java`、およびJUnit8件です。AI、攻撃、Phase 3回復、DamageTracker、TaCZ、BossBar、モデル、37アニメーション、音資源・既存効果音は変更していません。

## 自動検証

- `./gradlew test runGameTestServer` **成功**、終了コード0、1分18秒。
- JUnit **72件すべて成功**（既存64件＋追加8件）。失敗・エラー・スキップ0。
- TaCZなしのDedicated ServerでGameTest **63件すべて成功**。クライアント専用イベント登録がサーバーに読み込まれないことも確認。
- 実クライアント検証後の `./gradlew build` **成功**、終了コード0、16秒。直前に成功したJUnit結果を使用しました。

追加JUnitは、交戦開始・最後の対象終了、複数ボス切り替え、長時間交戦中の毎tick停止防止、プレイヤー死亡、ワールドリセット、設定無効・消音・音声エンジン再試行中の通常BGM抑止を検証します。既存テストの削除・変更はありません。

今回はTaCZを入れたサーバー・クライアント試験は再実行していません。TaCZ連携のクラスは旧配布jarとバイト単位で一致し、従来の任意連携記録は[初回BGM検証](docs/VALIDATION-1.3.0-music.md)に保存しています。

## runClientと実音声出力

実際のForgeクライアントを起動し、既存ワールドの複製 `ApexMusicPriority130` で確認しました。元ワールドは保持しました。Save and Quit → Quit Gameで正常終了、終了コード0、`BUILD SUCCESSFUL in 8m 14s`。仮想ディスプレイとOpenAL SoftのWAVE出力を利用しました。

全カテゴリの音量は1.0で、戦闘検証のためにMUSIC・RECORDS・効果音を消音していません。

| 確認 | 結果 |
| --- | --- |
| 生存・非戦闘のボス | 専用曲・通常BGM抑止なし |
| Survival対象を実AIが捕捉 | SUPPRESS、専用曲1インスタンス開始 |
| 再生中の通常BGM | 交戦開始で停止 |
| 交戦中に新たなMUSICを要求 | 実PlaySoundEventで除外 |
| 交戦中のPLAYER/RECORDSカテゴリ音 | 除外対象にならず再生可能 |
| 生存したまま交戦終了 | RELEASE、専用曲停止 |
| 交戦終了後のMUSIC要求 | 除外されず、実音声出力を確認 |
| 再戦 | 新しい専用曲1インスタンスを先頭から再生 |
| Nether移動 | world_reset停止・RELEASE、新MUSIC要求の除外なし |

Native出力と実OGGのPCM照合で、2回のボスBGM開始を検出しました。SEを含む録音からの推定ゲインは約 **0.834／0.839**。0.85という設定値そのものは配布jarの逆アセンブルでも確認しました。戦闘前の通常曲hal3、戦闘終了後のnuance1、再戦直前のcalm3も相関約0.973／0.981／0.994で実出力を識別しました。交戦中に除外された通常曲要求では、対応する音源の開始を検出しませんでした。

Nether移動後はログによる解除と再生要求の非除外を確認しましたが、短い録音窓での曲の実出力識別は成立していません。PCM検証はデジタル出力の確認で、物理スピーカーの試聴ではありません。

今回の2回の開始は別々の交戦であり、EOFループの測定とは区別します。全174.480秒のループ試験は再実行していません。音源・nativeループ設定は維持し、[初回BGM版の全曲ループ記録](docs/VALIDATION-1.3.0-music.md)を保存しています。前回の音量更新記録は[音量0.45版](docs/VALIDATION-1.3.0-volume.md)です。

一時的なデバッグ・クライアント設定は復元し、`run/options.txt` と両configのSHA-256が元の値と一致することを確認しました。

## 配布jarの検証

旧jarの163エントリを全比較しました。新規クラス3エントリ、変更は `ApexBossMusic.class` と生成された `ApexBossMusic$1.class` の2エントリだけです。後者の命令列は完全一致し、差分はソースの行番号65→68のみ。他の **161エントリ**、既存クラス84個と音資源・モデル・アニメーションを含む61資源はバイト単位で一致しました。全89クラスがJava 17形式で、重複エントリ・CRC不良はありません。

`build/libs` と `dist` のjarは完全一致し、SHA-256 sidecarも更新しています。

- `build/libs/apexpredator-1.20.1-1.3.0.jar`
- `dist/apexpredator-1.20.1-1.3.0.jar`
- `dist/apexpredator-1.20.1-1.3.0.jar.sha256`

サイズ: **4,270,955 bytes**。

SHA-256: `5489cf8580e5913de13f947bd5f8d271027ff869e23ec92080fe96524ed6f01c`

OGG SHA-256: `7f6f9e433df7e55e88242e0e950addb572792e7c333acdaf8055df0cc560bfe4`（音源そのものは変更なし）。

GeckoLib 4.4.9 Forge版が必要、TaCZは任意です。通信プロトコルは既存BGM版と同じ3で、今回の変更では同期・サーバー仕様を変更していません。

詳細ログ・jar比較・PCM解析・設定復元記録はignoredの `build/music-priority/` に保存しています。[BGM仕様](docs/BOSS-MUSIC.md)と[導入方法](dist/README.md)を参照してください。
