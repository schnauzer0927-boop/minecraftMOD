# Apex Predator

Minecraft **1.20.1** / Forge **47.4.10** / Java **17** 向けの、プレイヤーの戦い方に適応するボス MOD。MOD ID は `apexpredator` です。

## 導入・召喚

ビルド済み MOD は [dist/apexpredator-1.20.1-1.3.0.jar](dist/apexpredator-1.20.1-1.3.0.jar) にも置いています。GitHub 上ではファイルを開き **Download raw file** または **Raw** で保存できます。

1.3.0 は `build/libs/apexpredator-1.20.1-1.3.0.jar` と **[GeckoLib 4.4.9 の Forge 1.20.1 版](https://cdn.modrinth.com/data/8BmcQJ2H/versions/RBA7lJaW/geckolib-forge-1.20.1-4.4.9.jar)** をクライアントとサーバーの `mods` に配置します。GeckoLib は必須、TaCZ は任意です。以前の Apex Predator jar と重複させないでください。

```mcfunction
/summon apexpredator
/summon apexpredator ~ ~ ~
/summon apexpredator:apex ~ ~ ~
/give @s apexpredator:apex_spawn_egg
/apex debug
```

召喚・デバッグコマンドには権限レベル 2 が必要です。自然スポーンは登録していません。スポーンエッグはクリエイティブのスポーンエッグタブ、Apex Core は素材タブにあります。死亡時に **Apex Core を 1 個**ドロップします。

## 戦闘

初期属性は HP 600、通常攻撃 14、移動速度 0.34、索敵 64、防御 18、ノックバック耐性 0.8。攻撃はサーバー側の単一 `CombatController` が管理し、技ごとに予備動作・隙・クールダウンを設けています。実際のプレイヤー被ダメージにはバニラの難易度、防具、盾が適用されます。

| フェーズ | HP 境界 | 技と変化 |
| --- | --- | --- |
| 1 | 600–400 超 | 近接、横移動、14 tick の予備動作付き突進、ジャンプ着地衝撃波 |
| 2 | 400 以下–200 超 | 移動速度上昇、安全な短距離ワープ、6 ブロック衝撃波、弱追尾投射物、フェイント、3 連ブレードコンボ |
| 3 | 200 以下 | 判断・攻撃速度上昇、3 連続ワープ、10 ブロック衝撃波、引き寄せ、半透明のモデル幻影、強化突進、必殺技 |

近接連打にはバックステップ・カウンター・押し出し、射撃には横移動・ジグザグ回避・接近、高所にはジャンプ・投射物・ワープ、定点攻撃には側面または背後への位置変更を選びやすくなります。ワープ先はランダムで、読み込み済みチャンク・足場・衝突・液体・ワールド境界を確認し、Forge のテレポートキャンセルイベントを尊重します。標準設定ではブロックを破壊しません。

必殺技 **APEX EXECUTION** は **36 tick（1.8 秒）**停止して半径 **15 ブロック**を予告します。固定された 3 方向への突進斬撃、中央への安全な復帰、X 字斬撃、12 tick 遅れの衝撃波を行います。範囲外へ逃げるか遮蔽物に隠れることで回避できます。受理された各攻撃の基本ダメージは個体ごとの合計で 42 以下に制限し、通常の難易度・防具・盾・無敵時間を維持します。投射物は遅く、追尾は弱く、壁に当たると消えます。引き寄せ後も距離と隙を残します。

Phase 3突入時には **60 HP** を一度だけ回復します。その後は実HPが減る被弾から **5秒** 経過すると、通常戦闘を続けながら **毎秒4 HP**、**300 HPまで** 自己回復します。通常の待機では最後の1秒にコアの点滅と吸収粒子が出るため、攻撃して回復を妨害できます。被弾時は即停止し、再び5秒待ちます。APEX EXECUTION、フェーズ移行、stagger、死亡中は継続回復を停止します。回復してもPhase 3を維持し、HP600まで完全復活する継続回復はありません。

## 銃・高 DPS 対策

- 通常の 1 ヒット上限は 35。防具計算は維持します。
- 同じ弾丸が同 tick に発生させる通常／貫通などの分割ダメージは、合計で 35 の予算を共有します。
- 防具・Forge イベント適用後の**実際の HP 減少**を 20 tick の窓で記録し、100 以上で 50 tick の回避モードに入ります。
- 回避中だけ 1 ヒット上限を 14 にし、被弾に伴う 6 tick の短い粒子付き無敵で、通常の無敵時間を解除する銃の連射にも対応します。回避モード自体を被弾で延長し続けることはありません。
- 累計 75 の実ダメージで位置変更を要求。連続ヒットには回避 AI を強化します。常時の固定割合カットはありません。
- `/kill` と奈落は通常の管理・環境処理を維持します。

TaCZ が存在する場合だけ、`GunFireEvent` と `EntityHurtByGunEvent.Post` を実行時に検出します。銃声の地点を調査し、射手・射撃元・連射傾向を観察します。イベントからダメージを再加算しません。API が異なる場合は連携のみを無効化し、通常の戦闘制御・ダメージ保護は継続します。

## 設定・モデル

`config/apexpredator-common.toml` に以下の設定が生成されます。設定変更後はゲーム／サーバーを再起動してください。

| 設定 | 初期値 |
| --- | --- |
| `maximumHitDamage` | 35 |
| `burstDamageThreshold` | 100 |
| `repositionDamageThreshold` | 75 |
| `evasionMaximumHitDamage` | 14 |
| `evasionTicks` | 50（40–60） |
| `debug` | false |
| `destroyTerrain` | false（着地の地形破壊を無効化。true でも mobGriefing・Forge 保護イベントを尊重し、軟らかい表面最大 8 ブロックに限定） |
| `phase3Healing` | true。falseで突入時・継続回復とその演出をすべて無効化 |
| `phase3EntryHeal` | 60 HP。実際の最大HPまで |
| `phase3HealDelaySeconds` | 5秒。最後の実HP減少からの待機時間 |
| `phase3HealPerSecond` | 4 HP。20 tickごとに回復 |
| `phase3HealCap` | 300 HP。継続回復の上限（最大HPも超えない） |

`debug=true` はフェーズ・戦闘状態・対象・最後の技・実被ダメージ/DPS を毎秒ログへ出します。円形攻撃の WarningCenter / DamageCenter / radius / playerDistance / YDifference / InsideHitbox、ジャンプの SlamTarget / ActualLanding / LandingError も表示します。攻撃時には `[APEX AREA DEBUG]` / `[APEX SLAM DEBUG]` を記録し、`/apex debug` でも近くの個体を確認できます。

回復時計は既存の`DamageTracker`へ統合しています。20 tickのDPS窓から古い被弾を削除しても、最後の実ダメージ時刻は回復判定のため保持します。TaCZの弾丸による実HP減少も同じ経路で回復を停止し、銃声やPostイベントだけではダメージを二重計上しません。回復の詳細は [Phase 3自己回復](docs/PHASE3-HEALING.md) に記録しています。

1.3.0 は 1.2.0 の外観を基に、頭部を新規の細い装甲マスクへ作り直し、首・肩・肘・手首・腰・股関節・膝・足首へ内部軸と重なりカバーを追加しました。装甲と発光アトラスは 512×512。V 字の胸部外殻、複合前腕刃、後方へ流れる背部フィン、逆関節脚の方向性を維持しています。

37 アニメーションを維持し、攻撃時刻・フェーズ・瞬殺防止・残像・Particle・SE・カメラ・TaCZ 連携は従来どおりです。円形攻撃とジャンプの位置合わせは以下のように修正しています。

- 予告開始時に不変の `AttackArea` を保存し、予告・衝撃波・ダメージで同じ中心と半径を使用します。通常衝撃波は 6、Phase 3 は 10、ジャンプは 5、APEX EXECUTION は 15 ブロックです。
- 円とプレイヤーの BoundingBox の重なりで判定し、境界誤差は 0.15 ブロック。地面攻撃の上下範囲は中心から下 1.5・上 3.0 ブロックで、段差と通常ジャンプを扱いつつ高所を除外します。遮蔽物・防具・盾・無敵時間は有効です。
- ジャンプの `slamTarget` は予告時に足場の衝突形状から取得します。対象が移動しても更新せず、実際の重力と空中速度から毎 tick 補正します。着地演出・地形処理・ダメージも保存した地点を使用します。地形で遠くへ阻まれた場合は攻撃を中止します。
- フェーズ変更時の円は従来から演出のみで、ダメージを追加していません。

歩行・走行・低姿勢ダッシュ、左右横移動・近接・回避、3 段コンボ、突進の準備／発動／終了、ジャンプ／スラム、ワープ前後、衝撃波・射撃・必殺技の準備／発動、フェーズ移行、被弾・よろめき・復帰・死亡・Phase 3 の荒々しい姿勢など、**37 クリップ**を収録。サーバーの技進行と同期して切り替え、近接はフェーズの攻撃速度に追従します。死亡モデルは 2 秒の崩れ落ちを終えてから消えます。

橙色の斬撃弧、拡大リング、警告範囲、地面亀裂、火花、岩片・砂埃、集束する射撃、ワープの煙、高速移動時の実モデル残像を描画します。残像は各個体最大 4 個、描画負荷に上限を設け、警告範囲は品質を下げても維持します。フェーズ移行は 36 tick 攻撃を中断し、背部展開・コア発光・専用 SE・BossBar 色変更で通知します。実際のゲーム速度は変更せず、短い縁のフラッシュで演出します。

`config/apexpredator-client.toml` の設定は描画だけに作用し、サーバーの攻撃判定は変えません。

| 設定 | 初期値・内容 |
| --- | --- |
| `particleQuality` | HIGH。MEDIUM は 65%、LOW は 35% の装飾粒子 |
| `cameraShake` | true。短い距離減衰付きシェイク |
| `afterImages` | true。高速移動と幻影の半透明モデル |
| `screenEffects` | true。画面の縁の光と必殺技の表示 |
| `bossMusic` | true。近くでプレイヤーと交戦中のApexに専用ループ音楽 |

専用BGMはユーザー提供の **The Stalking Motif**。`apexpredator:boss_music`としてステレオOGGをストリーミングし、RECORDSカテゴリ（「ジュークボックス／音符ブロック」）で音量を調整できます。マスターとRECORDSの両方を反映し、初期音量は0.45です（初回BGM版の0.22から増量）。曲は最後まで再生すると同じ音源でループし、複数のApexが交戦していても1曲だけ流します。

近くにいるだけの非戦闘ボスやNoAI個体では流れません。戦闘終了、ボス・プレイヤーの死亡、64ブロックの音響範囲からの離脱、デスポーン、ワールド退出・ディメンション移動、消音で停止します。近くの別ボスとの交戦が続く場合は同じ曲を保持します。再戦・消音解除時は先頭から再生します。[BGMの仕組み・変更ファイル](docs/BOSS-MUSIC.md)に詳細を記録しています。

マルチプレイでは両側を今回のBGM追加jarへ更新してください。戦闘対象の同期フィールド追加により通信プロトコルを3へ更新しており、追加前1.3.0との混在は接続時に拒否します。

[正面](docs/visuals/apex-1.3-front.png)・[側面](docs/visuals/apex-1.3-side.png)・[背面](docs/visuals/apex-1.3-back.png)・[四方向プレビュー](docs/visuals/apex-1.3-turnaround.png)・[頭部比較](docs/visuals/apex-1.2-vs-1.3-head-comparison.png)・[実ゲーム全身](docs/visuals/apex-1.3-ingame.png)・[実ゲーム頭部](docs/visuals/apex-1.3-head-ingame.png)・[変更ファイル一覧](docs/CHANGES-1.3.0.md)を公開しています。

モデル・アニメーション・エフェクト素材の構成と再生成方法は [scripts/art/README.md](scripts/art/README.md) に記載。`assets/apexpredator/sounds/` の専用 OGG と `sounds.json` はリソースパックで差し替えられます。

見た目の確認用に、権限レベル 2 の `/apex animate <animation>` を追加しました。`/summon apexpredator:apex ~ ~ ~ {NoAI:1b}` で召喚した個体に使用すると、戦闘で上書きされずに各クリップを確認できます。

## ビルド・テスト

Java 17 が利用できるローカル環境では次を実行します。

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runClient
```

このクラウド環境では、Java 17 と通信プロキシの設定を次で再現できます。

```bash
bash scripts/setup-cloud.sh --with-display
source scripts/environment.sh
./gradlew build
./gradlew runGameTestServer
```

GUI のないクラウド環境でクライアントを動かす場合は、準備済みの仮想画面を起動します。

```bash
bash scripts/start-display.sh
source scripts/display-environment.sh
export ALSOFT_DRIVERS=null
./gradlew runClient
```

TaCZ 1.1.8-hotfix2 を使う開発検証では、公式の jar を置いたディレクトリを指定します。通常のビルドには追加されず、MOD jar にも同梱しません。

```bash
./gradlew runGameTestServer -PtaczDir=/workspace/.tools/optional-mods
./gradlew runClient -PtaczDir=/workspace/.tools/optional-mods
```

単体テストはダメージ窓・フェーズ境界・プレイヤー別観測・技選択を検証します。GameTest は登録、実ダメージ、防具、連射、フェーズ保存、ドロップ、攻撃の予告と隙、遮蔽物、ワープのキャンセルを実ゲーム上で検証します。

主な責務は `ApexEntity`、`CombatController`、`CombatDecisionPolicy`、`AttackManager`、`AttackArea`、`SlamTargetResolver`、`DamageTracker`、`PlayerBehaviorTracker`、`ApexPhaseManager`、`ApexSounds`、`ApexParticles`、`TaczCompat` に分けています。

TaCZ の検証対象は **1.1.8-hotfix2** です。開発環境の Mixin refmap も ForgeGradle の SRG→Mojmap 変換に合わせて設定しており、TaCZ 付きの起動ではその変換タスクを実行します。GeckoLib 4.4.9 の Forge 版は Modrinth の不変バージョン ID `RBA7lJaW` で取得し、通常ビルドにも正しい依存関係を追加しています。Apex jar に GeckoLib・TaCZ 自体を同梱しません。

各クラウドタスクは既に分離されています。通常の作業は既存チェックアウトを使い、追加の Git worktree は作りません。

検証件数、ログ、jar の SHA-256 は [VALIDATION.md](VALIDATION.md) に記録しています。
