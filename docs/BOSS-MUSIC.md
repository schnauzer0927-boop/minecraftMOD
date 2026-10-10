# Apex Predator — The Stalking Motif

ユーザー提供のSuno製作曲 **The Stalking Motif** を、Apexのボス戦BGMとして組み込みました。Minecraft 1.20.1 / Forge 47.4.10 / Java 17、MOD ID `apexpredator`、バージョン1.3.0を維持しています。改修前の基準は `3a131f97fef2f9cfb6a0cb1d2527f978ff95d4aa`、改修ブランチは `feature/apex-boss-bgm` です。

## 音源と登録

登録名は **`apexpredator:boss_music`**。既存のSoundEventと`sounds.json`の登録名を再利用し、`stream: true`を維持しています。実際に再生するファイルは `src/main/resources/assets/apexpredator/sounds/boss_music.ogg` です。

原本MP3は `audio/source/the_stalking_motif.mp3` に未変更で保存し、JARには収録しません。ffmpeg 7.1.5 / libvorbisでステレオ48 kHz、quality 6のOgg Vorbisへ変換しました。曲の長さは **174.480秒**。先頭の約0.320秒のほぼ無音なエンコーダ残留部分だけを除き、意図したイントロとフェードアウトを維持しています。開始5 ms・終了10 msの小さいフェードで端のクリックを抑えました。音量の正規化・圧縮・ピッチ変更は行っていません。

曲の終わりからイントロへ自然に戻りますが、原曲のイントロ・アウトロを残すため、拍単位で組み替えたループではありません。ピークは−4.79 dBFS、端のサンプル差は−81.12 dBFSです。変換と音源の詳細は [audio/README.md](../audio/README.md) に記録しています。

再変換にはPython 3、NumPy、ffmpegのlibvorbisエンコーダ、ffprobeを使用します。

```bash
python3 scripts/assets/import_boss_music.py
```

必要なツールがない場合、スクリプトは明示的に失敗します。原本のSHA-256を確認し、同じ変換処理で作り直します。旧FX生成スクリプトはこのBGMを上書きせず、回復など追加の音定義も保持します。

## 再生と停止

サーバーはApexの**実際のプレイヤー戦闘対象**をOptional UUIDとして同期します。近くにボスがいるだけ、NoAIの撮影用ボス、非プレイヤーとの戦闘では再生しません。攻撃モーション名だけで戦闘を推測しません。

対象プレイヤーは生存中で、Creative・Spectator以外、同一ディメンション、既存AIの追跡範囲96ブロック以内です。この判定は既存AIを変更しません。クライアントは **64ブロック以内** の交戦中Apexを確認し、生存中でSpectator以外のローカルプレイヤーへ曲を流します。マルチプレイでは別プレイヤーが現在の攻撃対象でも、近くの参加者・Creative観戦者が同じ曲を聞けます。

ボスが複数いても、ローカルクライアントの音源は**1本だけ**です。ボスや戦闘対象が切り替わっても、いずれかの近くのボスが交戦中なら現在の曲を継続します。毎tickの判定は、曲の再生要求を重ねません。

次の条件では停止します。

- 近くの対象ボスすべてが戦闘を終了した。
- 対象ボスが死亡・デスポーン・読み込み範囲外へ移動した。
- プレイヤーが死亡・Spectator化・64ブロックの音響範囲外へ移動した。
- ワールド退出・別ディメンションへの移動でクライアントLevelが変わった。
- `bossMusic=false`、マスター音量0、RECORDS音量0になった。

再戦や消音解除後は新しい音源を1本だけ作り、先頭から再生します。通常の隙・フェーズ変更・自己回復・APEX EXECUTION中も、実際の交戦が続く限り同じ曲を維持します。

## ループと音量

`ApexBossMusic`はMinecraftの`AbstractTickableSoundInstance`です。`looping=true`、`delay=0`、`stream=true`によって、音声エンジンの`LoopingAudioStream`が曲の終端で同じストリームを繰り返します。曲の終了を毎tick監視して、新しい音源を再生する方式ではありません。

カテゴリは **RECORDS**（日本語設定画面の「ジュークボックス／音符ブロック」）。デフォルト音量は **0.45**、ピッチは **1.0**、位置による減衰は無効です。ユーザーの増量依頼で初回BGM版の0.22から0.45へ変更しました。音声信号の振幅で約2.05倍（約+6.2 dB）です。旧BGMのフェーズ連動の音量・ピッチ変更は取り除き、ループやボス切り替えのたびに音量・ピッチが変わらないようにしました。マスターとRECORDSの両音量が適用されます。

起動時の非同期音声読み込みには20 tickの猶予を設けます。音声エンジン再読み込みなどでチャネルが失われた場合は、既存の要求を停止し、100 tickの間隔を置いて1本だけ再試行します。消音中の毎tick再生要求や音源の重複を防ぎます。ポーズ中に新しい未停止音源を作りません。

## 保持と互換性

CombatController、AttackManager、DamageTracker、PhaseManager、Phase 3自己回復、TaCZ、37アニメーション、geometry、テクスチャ、既存14効果音・回復音は変更していません。ApexEntityには音楽対象の同期だけを追加しました。旧Particle・SE・カメラ・残像の処理は維持しています。

同期フィールドを増やしたため、通信プロトコルは3へ更新しています。マルチプレイではクライアント・サーバー両側を今回のjarへ更新してください。GeckoLib 4.4.9 Forge版は引き続き必須、TaCZは任意です。

## 変更ファイル

| ファイル | 内容 |
| --- | --- |
| `audio/source/the_stalking_motif.mp3` / `audio/README.md` | 原本と変換記録。原本はJAR対象外 |
| `assets/apexpredator/sounds/boss_music.ogg` | 合成BGMを提供曲のVorbis音源へ置換 |
| `scripts/assets/import_boss_music.py` | 変換・音源検査の再現手順 |
| `scripts/assets/generate_fx_assets.py` / 同README | BGM上書き防止・追加音定義保持 |
| `client/effects/ApexBossMusic.java` | 単一RECORDSループと開始・停止 |
| `client/effects/BossMusicPlaybackPolicy.java` | 重複防止、消音、音声チャネル再試行 |
| `combat/ApexMusicEncounter.java` | 戦闘対象とリスナーの条件 |
| `entity/ApexEntity.java` | 戦闘対象UUIDの同期 |
| `client/effects/ApexClientEffects.java` | 死亡・退出も含む音楽更新の呼び出し |
| `ApexClientConfig.java` / `effect/ApexEffects.java` | 既存BGM設定の説明・プロトコル3 |
| `gametest/BossMusicGameTests.java` | 実サーバーtickの戦闘同期試験 |
| `BossMusicPlaybackPolicyTest.java` / `ApexMusicEncounterTest.java` / `BossMusicAssetTest.java` | ライフサイクル・条件・音資源のJUnit |
| `README.md` / `VALIDATION.md` / `dist/README.md` | 導入・検証・配布記録 |

Javaの相対パスは `src/main/java/dev/apexpredator/`、音資源は`src/main/resources/`、JUnitは`src/test/java/dev/apexpredator/`を起点とします。`sounds.json`とSoundEventは既存登録を利用し、登録ファイル自体は変更していません。

`debug=true`のときだけ、BGM開始・初回チャネル再生確認・停止を`[APEX BGM DEBUG]`としてログへ記録します。instance、カテゴリ、音量、交戦ボス数、停止理由を表示し、毎tickの音楽ログは出しません。

現在の音量更新の検証・完成jarのハッシュは [VALIDATION.md](../VALIDATION.md) に記録します。[初回BGM版の実クライアント検証](VALIDATION-1.3.0-music.md)と[追加前の回復検証](VALIDATION-1.3.0-healing.md)も保持しています。
