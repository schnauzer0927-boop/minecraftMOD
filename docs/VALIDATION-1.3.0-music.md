# 初回BGM版（既定音量0.22）の保存記録

このページはコミット `6d63a2cda546546382e8ee19304e37fad626a688` 時点の記録です。以下の実録音のゲイン・jarハッシュは0.22版のものです。[現在の音量更新版](../VALIDATION.md)とは区別してください。

# Apex Predator 1.3.0 — ボス戦BGMの検証記録

2026-10-10（JST）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17.0.12 / Gradle 8.8で実施しました。ログ時刻はUTCです。基準は `3a131f97fef2f9cfb6a0cb1d2527f978ff95d4aa`、改修は `feature/apex-boss-bgm`、バージョンは1.3.0のままです。[追加前の自己回復検証](VALIDATION-1.3.0-healing.md)と[外観・判定検証](VALIDATION-1.3.0-visuals.md)を保存しました。

## BGMと再生仕様

登録名は **`apexpredator:boss_music`**。ユーザー提供の **The Stalking Motif** をステレオ48 kHz / Ogg Vorbis quality 6へ変換し、既存の`stream: true`登録を使用します。音源は **174.480秒 / 3,657,266 bytes**です。先頭のほぼ無音な0.320秒だけを除き、意図したイントロ・アウトロを保持、開始5 ms・終了10 msの小さいフェードでクリックを抑えました。正規化・圧縮・ピッチ変更はありません。

原本MP3は`audio/source/the_stalking_motif.mp3`へ未変更で保存し、JAR対象外です。変換手順・測定値・原本ハッシュは [audio/README.md](../audio/README.md)、変更ファイル・条件・ループの仕組みは [BOSS-MUSIC.md](BOSS-MUSIC.md)に記録しています。

| 項目 | 実装 |
| --- | --- |
| 戦闘判定 | 実際の生存Survival/Adventureプレイヤー戦闘対象UUIDをサーバーから同期。近接・アニメーション名だけでは開始しない |
| 音響範囲 | 交戦中Apexから64ブロック以内。同一Level、生存・非Spectatorのローカルリスナー |
| 既存AI | 戦闘対象の既存96ブロック追跡を変更しない |
| 複数ボス | 全交戦ボスを共有する1音源。ボス・対象切替でも曲を維持 |
| 再生 | AbstractTickableSoundInstance、RECORDS、音量0.22、固定pitch 1.0、減衰なし |
| ループ | stream=true / looping=true / delay=0でネイティブLoopingAudioStreamが終端から先頭へ繰り返す |
| 停止 | 全交戦終了、死亡、距離、デスポーン、退出、ディメンション、bossMusic=false、MASTER/RECORDS音量0 |
| 再戦・消音解除 | 新しい1音源を先頭から再生 |
| エンジン再読込 | 20 tick開始猶予、失われた要求を止め100 tick後に1本だけ再試行 |
| ポーズ | 既存の一時停止中チャネルを保持し、未停止の新しい曲を作らない |

RECORDSは日本語設定画面の「ジュークボックス／音符ブロック」です。マスター音量とRECORDS音量が適用されます。別プレイヤーが現在の戦闘対象でも、近くの参加者やCreative観戦者は同じ曲を聞けます。NoAI撮影個体・非戦闘の生存ボス・非プレイヤー戦闘では再生しません。

## 保持と音源検査

- CombatController、AttackManager、DamageTracker、PhaseManager、Phase 3HealingController、TaCZ、37クリップ、geometry、テクスチャ、Particle、既存14効果音・回復音の**83保護パスはバイト単位で一致**。
- ApexEntityの変更は音楽対象の同期だけ。既存HP・攻撃・移動・Phase 3回復の処理は変更なし。
- 37アニメーションSHA-256: `0218e1e3c8ecaa6dea5cc8bf6913b2a29eeda67c2c5f422df9fab975a9d21744`。
- 原本MP3 SHA-256: `875e429fcc2b8a28e55d7205e7f32d88e58e3f180a839d32ad0b0f542b54bc76`。
- 配布OGG SHA-256: `7f6f9e433df7e55e88242e0e950addb572792e7c333acdaf8055df0cc560bfe4`。
- OGGピーク−4.787 dBFS、RMS−16.802 dBFS、ループ境界サンプル差−81.116 dBFS。全曲デコード正常、クリッピングなし。
- 旧素材生成スクリプトの隔離検査では提供曲を上書きせず、全15音定義・回復定義を保持。
- 実Minecraft 1.20.1 / Forge 47.4.10のSoundEngineとSoundBufferLibraryを確認。同一ループストリームがEOFを処理し、毎曲新しい再生要求を出さない。

原曲のフェードアウトとイントロは残しています。自然な全曲反復であり、拍を組み替えた完全な循環曲という意味ではありません。

## 自動テスト結果

| 検証 | 結果 |
| --- | --- |
| JUnit | **64件すべて成功**。既存47＋追加17、失敗・エラー・スキップ0 |
| `./gradlew test runGameTestServer` | TaCZなしDedicated Server **63件すべて成功**。既存56＋追加7、終了コード0、1分30秒 |
| `./gradlew runGameTestServer -PtaczDir=/workspace/.tools/optional-mods` | TaCZありDedicated Server **66件すべて成功**、終了コード0、1分17秒 |
| 設定画面の先読み判定修正後 `./gradlew test` | **64件すべて成功**、終了コード0、18秒。変更はクライアントのポーズ判定だけ |
| 実クライアント | **TaCZなし・あり両方正常終了（終了コード0）**。実交戦で再生・停止、録音内の提供曲一致を確認。全曲EOF・複数ボス・各停止条件・音量・修正版の設定画面往復はTaCZなしで確認 |
| 最終 `./gradlew build` | **成功**、終了コード0、28秒。最終クライアント確認後に実行。JUnitは直前の64件成功を再利用（UP-TO-DATE） |
| 配布jar監査 | **成功**。検証済みOGG・登録・Java 17・依存・プロトコル3・37クリップ・旧クラス保持・配布コピー一致を検査 |

追加JUnitは長時間の重複防止・ネイティブループ中の保持、複数ボス、再戦、設定・死亡・距離・ディメンションなどの停止、MASTER/RECORDS消音と解除、音声チャネル消失・再試行、対象とリスナーの別範囲、実OGG Vorbisヘッダ・stream登録を確認します。

追加GameTestは実サーバーentity tickでの戦闘対象UUIDの設定・解除、NoAI、死亡、Creative/Spectator/非プレイヤー対象の除外、正確な64/96境界、別ディメンション、他プレイヤーを対象とする複数ボス、デスポーン、実Phase 3突入時の60 HP回復維持を確認しました。距離条件のfixtureはAIだけを抑えた通常AI属性の個体で、位置を安定させるためテスト個体だけ重力を無効にします。生産コードの物理を変更しません。

既存JUnit・GameTestに変更はなく、回復・瞬殺防止・APEX EXECUTION・円形判定・実ジャンプ着地・TaCZの既存試験も維持します。

## 実クライアント

実際の`runClient`がOpenAL Softの`Wave File Writer`を開き、ゲームの音声ミキサー出力をステレオPCMとして記録しました。全曲174.480秒の実OGGを使用し、短い代替曲に差し替えていません。NoAI個体に近づいただけでは開始せず、Survivalの実対象`Dev`を持った通常AIの交戦でBGMが開始しました。

通常戦闘の1本の音源`instance=2`が203秒以上継続し、その間START/PLAYINGは最初の1回だけ、STOP・再作成なしでした。録音内の原曲冒頭の一致位置は481.461406秒と655.950476秒、間隔は**174.489070秒**。原曲174.480秒との差9.07 msは実デコーダ・ミキサーのサンプル境界を含む測定値です。相関は0.980665 / 0.973983、推定ゲインは0.215715 / 0.214261で、約0.67%の測定差はあるものの二重音源のゲイン増加はありません。コードの音量0.22・ピッチ1.0は固定です。

最初の録音区間は121秒で交戦が終了し、正しく`reason=no_encounter`で停止しました。対象が足場の縁へ押され、ボスが17ブロック下へ落ちて視線を失ったためです。この区間はEOF確認に含めません。2回目は検証ワールドの81×81床で対象を中央へ定期的に戻し、同じ生存Survival対象・通常AI・同じ音源を維持して全曲を検証しました。製品のAI・ノックバック・音声は変更していません。

生存中のボスをNoAI状態にした15:41:46には同じ音源2が停止し、通常AIへ戻して交戦を再開した15:42:16には新しい音源3が先頭から開始しました。2体の通常AIボスが同じSurvival対象を追う場面では、一方を倒した15:42:51に音源3を保持し、最後の1体を倒した15:42:56に停止しました。

音量設定では、GUIでRECORDS=0.0 / MASTER=1.0にした後、通常ゲームへ戻った4秒の出力がRMS−96.348 dBFS・peak 1 LSBでした。MASTER=0.0 / RECORDS=1.0でもRMS−96.303 dBFS・peak 1 LSBで、OpenALの量子化ディザだけとなり曲は停止しました。消音解除後は新しい音源を先頭から開始します。

200.5ブロック先へ離れた15:52:01に停止し、戻った15:52:36に再開しました。実プレイヤー死亡の15:52:41には`player_unavailable`で停止し、実際のYou Died画面を確認しました。プレイヤーのNether移動では15:54:15に`world_reset`、ボスのNether移動では15:55:46に`no_encounter`で停止しました。

初回の設定画面操作時には交戦中の音声チャネル消失も観測しました。`Minecraft.isPaused()`の更新前でも非公開シングルプレイの一時停止画面・overlayを先読みする最小のクライアント判定を追加し、64 JUnitを再実行しました。同時刻に3.44秒／4.557秒の処理停止もあり、4秒分の音声バッファの枯渇も候補なので、チャネル消失の原因を画面フラグの時刻差だけとは断定していません。通常のpause/resumeは音声所有情報を削除しないことを独立監査で確認しました。音源・ループ・ゲイン・サーバーの実装はこの修正で変更していません。

修正版のクライアントでは16:03:01に開始した音源3が、実際のPause → Music/Sounds → RECORDSを正確に0.5へ変更 → ゲーム再開の間も同じ音源のまま継続しました。画面操作によるSTOP・再STARTはありません。初期fixtureの実死亡と、その後の長時間の押し出しによる交戦終了は正しい停止として別に記録し、ポーズ保持・消音検査の成功区間には含めません。

RECORDS=0.5 / MASTER=1.0の定常区間2秒では、原曲とのPCM相関0.988140、実ゲイン0.108536（設定上の期待0.11）を測定しました。ゲーム復帰直後の4秒に含まれた過渡的なバッファ区間は、この数値の根拠から除外しています。音量変更による音源の再作成はありません。

TaCZ 1.1.8-hotfix2入りの最終クライアントでも公式gunpackの読み込みと既存イベント連携の登録を確認しました。実Survival交戦で16:10:55に1本の音源が開始・再生され、実プレイヤー死亡の16:11:03に`player_unavailable`で停止しました。録音中の原曲冒頭位置29.341315秒、8秒PCM相関0.975972・推定ゲイン0.214699で、提供曲が実ミキサーから出力されていることを確認しました。TaCZ入りでは174秒の全曲反復を再検証しておらず、全曲EOFの実証は上記TaCZなしの同一音源実装を対象とします。

最初のTaCZなしクライアント、修正版TaCZなしクライアント、TaCZありクライアントはすべてSave/Quitの通常GUI操作で終了し、各Gradleの終了コードは0でした。一時変更した`run/options.txt`と共通・クライアントconfigの3ファイルは、それぞれのバックアップとSHA-256が一致する状態へ復元しました（`debug=false`、`bossMusic=true`）。

Xvfb / software Mesa / 30 FPSで実行しており、実PCのFPS測定・物理スピーカーによる聴感検査とは区別します。

[実クライアントの撮影画像](visuals/apex-boss-music-ingame.png)も保存しています。チャットを閉じた実ゲーム画面のキャプチャであり、モデル・エフェクトは追加前のままです。

## 配布jar

生成先: `build/libs/apexpredator-1.20.1-1.3.0.jar`

同一jar配布先: `dist/apexpredator-1.20.1-1.3.0.jar`

サイズ: **4,267,477 bytes**。両jarと`dist`のSHA-256 sidecarが一致します。

SHA-256: `33bc7d0e391e69d2855c7ec76b81d7498cfe78c0b5cf00fdebee9806490f0f48`

独立したjar監査ではCRC、Java 17 class major 61、全音資源・15登録、検証済みOGGのハッシュ、37クリップ、333キューブ・28ボーン、旧クラスの無断変更・削除なしを確認しました。既存73クラスはバイト単位で一致し、変更・追加は音楽関連の許可範囲のみです。MP3・WAV・録音・変換用ツールはjarに含まず、外部GeckoLib・TaCZも同梱しません。

GeckoLib **4.4.9 / Forge 1.20.1** が必須、TaCZは任意です。マルチプレイでは両側を今回のBGM追加版へ更新してください。戦闘対象の同期フィールド追加によりプロトコル3へ更新し、追加前1.3.0との混在を接続時に拒否します。

## ログと再現手順

- `build/music/validation-plain.log` / `validation-tacz.log` — Dedicated Serverと既存・追加試験。
- `build/music/validation-pause-fix.log` — 最小クライアント補正後の64 JUnit再実行。
- `build/test-results/test/` / `build/reports/tests/test/index.html` — JUnit XML / HTML。
- `build/music/audio-audit.json` / `generator-guard-audit.json` — 音源測定・素材再生成保護。
- `build/music/audit-review.md` / `source-audit.json` — 独立音声実装・保持監査。
- `build/music/client-plain.log` / `client-plain-final.log` / `client-tacz.log` — 実クライアントの状態・正常終了。
- `build/music/client/` — BGM状態、実音声、再生時間・設定復元の証拠。
- `build/music/final-build.log` / `jar-audit.json` — ビルド・パッケージ照合。

詳細ログ・音声採取・ツールは開発環境のignored出力です。再利用用環境設定はJava 17・GeckoLib・任意TaCZ・仮想画面と、このBGM追加版の起動・検証手順を対象とします。環境設定の公開・別タスクでの復元検証は未実施です。
