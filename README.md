# Apex Predator

Minecraft **1.20.1** / Forge **47.4.10** / Java **17** 向けの、プレイヤーの戦い方に適応するボス MOD。MOD ID は `apexpredator` です。

## 導入・召喚

ビルド済み MOD は [dist/apexpredator-1.20.1-1.0.0.jar](dist/apexpredator-1.20.1-1.0.0.jar) にも置いています。GitHub 上ではファイルを開き **Download raw file** または **Raw** で保存できます。

`build/libs/apexpredator-1.20.1-1.0.0.jar` をクライアントとサーバーの `mods` に配置します。TaCZ・GeckoLib は必須ではありません。

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
| 2 | 400 以下–200 超 | 移動速度上昇、安全な短距離ワープ、6 ブロック衝撃波、弱追尾投射物、フェイント |
| 3 | 200 以下 | 判断・攻撃速度上昇、3 連続ワープ、10 ブロック衝撃波、引き寄せ、粒子の人型幻影、強化突進、必殺技 |

近接連打にはバックステップ・カウンター・押し出し、射撃には横移動・ジグザグ回避・接近、高所にはジャンプ・投射物・ワープ、定点攻撃には側面または背後への位置変更を選びやすくなります。ワープ先はランダムで、読み込み済みチャンク・足場・衝突・液体・ワールド境界を確認し、Forge のテレポートキャンセルイベントを尊重します。ブロックは破壊しません。

必殺技は **50 tick（約 2.5 秒）**の音・粒子による予告後、固定方向の狭い範囲に 42 のダメージを与えます。横へ移動する、18 ブロック以上離れる、遮蔽物に隠れることで回避できます。投射物は遅く、追尾は弱く、壁に当たると消えます。引き寄せ後も距離と隙を残します。

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

`debug=true` はフェーズ・戦闘状態・対象・最後の技・実被ダメージ/DPS を毎秒ログへ出します。`/apex debug` でも近くの個体を確認できます。

巨大人型の仮モデルと独自テクスチャ、発光眼を同梱しています。`AnimationState` に idle / walk / run / attack / charge / jump / hurt / dodge / teleport / special / death の対応を用意し、GeckoLib への将来の描画アダプター追加ができる構成です。現バージョンに GeckoLib の依存はありません。

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

主な責務は `ApexEntity`、`CombatController`、`CombatDecisionPolicy`、`AttackManager`、`DamageTracker`、`PlayerBehaviorTracker`、`ApexPhaseManager`、`ApexSounds`、`ApexParticles`、`TaczCompat` に分けています。

検証した TaCZ のバージョンは **1.1.8-hotfix2** です。追加の必須 MOD はありません。開発環境の Mixin refmap も ForgeGradle の SRG→Mojmap 変換に合わせて設定しており、TaCZ 付きの起動ではその変換タスクを実行します。

各クラウドタスクは既に分離されています。通常の作業は既存チェックアウトを使い、追加の Git worktree は作りません。

検証件数、ログ、jar の SHA-256 は [VALIDATION.md](VALIDATION.md) に記録しています。
