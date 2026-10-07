# Apex Predator 1.1.0 の変更

Minecraft 1.20.1 / Forge 47.4.10 / Java 17。1.0.0 の適応 AI、ダメージ追跡、瞬殺対策、フェーズ、TaCZ 任意連携、BossBar、召喚方法、Apex Core を維持し、専用モデル・アニメーション・戦闘演出を追加しました。

1.0.0 は `work` ブランチの `f547038` に保持し、改修は `feature/apex-visuals-1.1.0` ブランチで進めています。旧配布 jar も保持しています。

## 導入

`build/libs/apexpredator-1.20.1-1.1.0.jar` と **GeckoLib 4.4.9 の Forge 1.20.1 版**を、クライアント・サーバー双方の `mods` に配置してください。GeckoLib は必須依存になりました。TaCZ は引き続き任意です。Apex jar は GeckoLib・TaCZ を同梱しません。旧 Apex jar と重複させないでください。

## モデルとアニメーション

[四方向プレビュー](visuals/apex-model-turnaround.png)・[実ゲームでの斬撃表示](visuals/apex-in-game.png)・[半透明モデル残像](visuals/apex-afterimage.png)

高さ 2.7 ブロック、28 ボーン、193 キューブのオリジナル形状です。黒い金属装甲、尖った頭部、橙色の目と胸部コア、巨大な前腕ブレード、可動する背面フィン、逆関節脚、爪を造形しました。256×256 の装甲テクスチャと、目・コア・首・関節・刃の溝・背面の継ぎ目に対応する発光マスクを収録しています。

今回の添付にコンセプト画像は含まれていないため、文章の外観指定を基に制作しています。

収録する 37 アニメーション:

```text
idle, walk, run, sprint
strafe_left, strafe_right
melee_left, melee_right
blade_combo_1, blade_combo_2, blade_combo_3
charge_prepare, charge, charge_end
jump_prepare, jump, slam
dodge_left, dodge_right, backstep
teleport_start, teleport_end
shockwave_prepare, shockwave_attack
ranged_prepare, ranged_attack
special_prepare, special_attack
phase_change
hurt_light, hurt_heavy
stagger, recovery, death
rage_idle, rage_run, rage_attack
```

体幹のひねり、ブレードの振り抜き、回避時の重心移動、低姿勢ダッシュ、背面装甲の展開を組み合わせています。再生開始・名前・連番をサーバーから同期し、コンボの予兆と接触を攻撃時刻に合わせました。死亡時は 2 秒の崩れ落ちを終えてから個体を除去します。

## 戦闘演出と設定

斬撃弧・火花・拡大リング・地面亀裂・岩片・砂埃・集束する射撃・ワープの煙・半透明の実モデル残像を追加しました。専用 ParticleType は `apex_slash`、`apex_energy`、`apex_smoke`、`apex_spark`、`apex_afterimage`、`apex_shockwave`、`apex_core` の 7 種です。12 種の専用 SE とループ音楽を、差し替え可能な OGG と `sounds.json` で提供しています。

Phase 2 / 3 の移行では攻撃を中断して 36 tick 停止し、背面展開・コア発光・リング・専用 SE・BossBar 色変更を同期します。Phase 3 の常時粒子、強攻撃のカメラシェイク、画面の縁の光も追加しました。短い画面演出によってスローモーション風に見せ、実際のゲーム速度は維持します。

Phase 2 から使用するブレードコンボは 3 連撃です。Phase 3 の **APEX EXECUTION** は半径 15 ブロックを 36 tick 予告し、固定された 3 方向への突進斬撃、中央への安全な復帰、X 字斬撃、遅れて発生する衝撃波を行います。範囲外への退避と遮蔽物が有効です。各個体への受理された基本ダメージの合計を 42 以下に制限し、盾・防具・難易度・通常の無敵時間を維持しています。

| 設定 | 内容 |
| --- | --- |
| `particleQuality` | HIGH 100%、MEDIUM 65%、LOW 35% の装飾粒子。警告範囲は各品質で維持 |
| `cameraShake` | 短い距離減衰付きシェイクの有効・無効 |
| `afterImages` | 高速移動と幻影の残像の有効・無効 |
| `screenEffects` | 画面の縁の光と必殺技表示の有効・無効 |
| `bossMusic` | 近くの生存 Apex に対する専用音楽の有効・無効 |
| `destroyTerrain` | サーバー設定、初期値 false。true でも mobGriefing・Forge 保護イベントを尊重し、着地時の軟らかい表面最大 8 ブロックに限定 |

描画設定は `config/apexpredator-client.toml`、戦闘・地形設定は `config/apexpredator-common.toml` にあります。残像は個体ごと最大 4 個、演出は最大 72 件、粒子は 1 tick 最大 160 個、演出描画は 1 フレーム最大 4096 四角形に制限しています。

権限レベル 2 の `/apex animate <clip>` と `/apex effect <effect>` で素材を確認できます。アニメーション確認には `{NoAI:1b}` で召喚した個体を使ってください。演出確認コマンド自体はダメージを与えません。

## 変更ファイル

以下はリポジトリルートからの相対パスです。同じディレクトリのファイルをまとめて記載しています。

| 分類 | ディレクトリ | 変更・追加ファイル |
| --- | --- | --- |
| ビルド・登録 | ルート | `build.gradle`, `gradle.properties` |
| MOD メタデータ | `src/main/resources/META-INF/` | `mods.toml` |
| MOD 初期化・設定 | `src/main/java/dev/apexpredator/` | `ApexPredator.java`, `ApexConfig.java`, **追加** `ApexClientConfig.java` |
| Entity | `src/main/java/dev/apexpredator/entity/` | `ApexEntity.java`, `ApexBoltEntity.java`, `AnimationState.java` |
| 戦闘 | `src/main/java/dev/apexpredator/combat/` | `AttackManager.java`, `AttackType.java`, `CombatController.java`, `CombatDecisionPolicy.java`, `CombatState.java` |
| 登録・コマンド | `src/main/java/dev/apexpredator/` | `registry/ApexRegistries.java`, `command/ApexCommands.java` |
| モデル描画 | `src/main/java/dev/apexpredator/client/` | `ApexModel.java`, `ApexRenderer.java`; **追加** `ApexAfterimageLayer.java`, `ApexBoltRenderer.java`, `ApexRenderTypes.java` |
| クライアント演出 | `src/main/java/dev/apexpredator/client/effects/` | **追加** `ApexBossMusic.java`, `ApexClientEffects.java`, `ApexEffectRenderer.java`, `ApexParticle.java`, `ApexParticleRegistration.java` |
| サーバー演出・音 | `src/main/java/dev/apexpredator/effect/` | `ApexParticles.java`, `ApexSounds.java`; **追加** `ApexEffectPacket.java`, `ApexEffects.java`, `ApexTerrainEffects.java`, `EffectKind.java` |
| GameTest | `src/main/java/dev/apexpredator/gametest/` | `ApexGameTests.java`, `AttackGameTests.java`; **追加** `VisualGameTests.java` |
| JUnit | `src/test/java/dev/apexpredator/assets/` | **追加** `ApexAssetIntegrityTest.java` |
| モデル形状 | `src/main/resources/assets/apexpredator/geo/` | **追加** `apex.geo.json` |
| アニメーション | `src/main/resources/assets/apexpredator/animations/` | **追加** `apex.animation.json`（37 クリップ） |
| 装甲・発光 | `src/main/resources/assets/apexpredator/textures/entity/` | `apex.png`; **追加** `apex_glow.png` |
| 言語・字幕 | `src/main/resources/assets/apexpredator/lang/` | `en_us.json`, `ja_jp.json` |
| 音イベント | `src/main/resources/assets/apexpredator/` | **追加** `sounds.json` |
| 素材生成 | `scripts/art/` | **追加** `generate_apex_model.py`, `generate_animations.py`, `README.md` |
| 演出素材生成 | `scripts/assets/` | **追加** `generate_fx_assets.py`, `README.md` |
| 配布 | `dist/` | **追加** `apexpredator-1.20.1-1.1.0.jar`, `apexpredator-1.20.1-1.1.0.jar.sha256`; `README.md` |
| プレビュー | `docs/visuals/` | **追加** `apex-model-turnaround.png`, `apex-in-game.png`, `apex-afterimage.png` |
| 説明書・検証報告 | ルート・`docs/` | `README.md`, `VALIDATION.md`; **追加** `docs/CHANGES-1.1.0.md` |

旧 `src/main/java/dev/apexpredator/client/ApexPhaseOverlay.java` は削除し、画面演出を `client/effects/ApexClientEffects.java` に統合しています。

追加した各粒子について、次の 7 ファイル名それぞれに `src/main/resources/assets/apexpredator/` 配下の `particles/<名前>.json` と `textures/particle/<名前>.png` を収録しています。

```text
apex_afterimage, apex_core, apex_energy, apex_shockwave,
apex_slash, apex_smoke, apex_spark
```

追加した音源は `src/main/resources/assets/apexpredator/sounds/` の次の 13 ファイルです。

```text
blade_hit.ogg, blade_swing.ogg, boss_music.ogg,
charge_dash.ogg, charge_start.ogg, death.ogg,
phase_change.ogg, rage.ogg, shockwave.ogg, slam.ogg,
special_attack.ogg, special_charge.ogg, teleport.ogg
```

## 検証と確認範囲

JUnit **22 件**、TaCZ なしの専用サーバー GameTest **22 件**、TaCZ 1.1.8-hotfix2 ありの GameTest **24 件**が成功しています。既存の防御・連射・フェーズ保存・召喚・ドロップ・攻撃テストを維持し、骨階層・UV・アニメーション・発光マスク・同期・コンボ・必殺技の退避と遅延衝撃波・死亡・地形保護を追加検証しました。

ビルド、実際のクライアント起動・表示、ログ、jar のハッシュなどの実測結果は [1.1.0 の検証記録](VALIDATION-1.1.0.md) に記録しています。FPS ベンチマークと実銃を使った手動バランステストは未実施です。

素材の再生成には Python と制作用ライブラリが必要ですが、ゲーム実行時には不要です。詳細は [モデル・アニメーション制作手順](../scripts/art/README.md) と [演出素材制作手順](../scripts/assets/README.md) を参照してください。
