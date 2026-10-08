# Apex Predator 1.3.0 — Phase 3自己回復

1.3.0のモデル・戦闘を保持して、最終形態へ妨害可能な自己回復を追加しました。バージョン番号は1.3.0のままです。追加前の基準は `f56b9335cb9d95ed78b405cca0d1a35045ead5fd`、今回の変更は `feature/apex-phase3-healing` に保持しています。

## 戦闘中の動作

| 条件 | 初期設定での動作 |
| --- | --- |
| 実際にPhase 3へ移行 | 60 HPを一度だけ回復。最大HPを超えない |
| 最後に実HPが減ってから80 tick | 胸部コアが点滅し、少量の橙色粒子が集まる |
| 最後に実HPが減ってから100 tick | 最初の4 HPを回復 |
| その後 | 20 tickごとに4 HP、300 HPまで回復 |
| 予兆中・回復中に被弾 | 実HP減少を記録した同じ処理で回復と追加発光を停止し、100 tick待ち直す |
| Phase 1 / 2・死亡 | 自己回復しない |
| APEX EXECUTION全体・phase_change・stagger | 継続回復を停止。再開時は条件を調べ、改めて20 tickの予兆を表示 |
| 通常の追跡・近接・移動 | 回復のために停止せず、そのまま戦闘を続ける |

通常の5秒待機には最後の1秒の予兆を含めます。停止中や読み込み前の回復をまとめて取り戻す処理はありません。回復してHP200を超えても、既存の単調増加するPhaseManagerがPhase 3を維持します。突入時回復は継続回復の上限とは別で、最大HPだけを上限とします。

## 設定

`config/apexpredator-common.toml`（開発環境では`run/config/`）に以下を追加しました。

```toml
phase3Healing = true
phase3EntryHeal = 60.0
phase3HealDelaySeconds = 5
phase3HealPerSecond = 4.0
phase3HealCap = 300.0
```

`phase3Healing=false`では突入時・継続回復・回復専用演出をすべて無効化します。既存のPhase 3演出と戦闘は維持します。待機秒数は0〜600秒、各HP設定は0〜600です。待機を0秒にしても、回避可能な読み取り時間として20 tickの予兆は必要です。`phase3EntryHeal=0`は突入回復だけ、`phase3HealPerSecond=0`は継続回復だけを停止します。通常の設定変更はゲーム／サーバーを再起動して適用してください。

## DamageTrackerへの統合

`DamageTracker`に最後の有限かつ正の実HP損失tick、経過時間と回復可能tickの取得を追加しました。従来の20 tickのDPS窓・被弾上限・回避・位置変更の計算は維持しています。短いDPS履歴が削除されても最後の被弾時刻は残ります。`Phase3HealingController`はその時計を参照して予兆と回復間隔だけを管理し、別の被ダメージ時計を持ちません。

`ApexEntity.hurt()`は既存どおり防具・Forgeダメージイベント適用後のHP差を計算し、突入回復より**前**にDamageTrackerへ記録します。これにより60 HP回復が、移行を引き起こした実ダメージやDPSを隠しません。TaCZの実弾ダメージも同じ経路で回復を止めます。銃声やTaCZのPost通知だけでは時計をリセットせず、同じダメージを再加算しません。盾・無敵・キャンセルなどで実HPが減らなかった攻撃は、実被ダメージとして数えません。

サーバーのentity tickで動作するためNoAIでも時計と回復が進み、死体を回復させません。NBTには絶対時刻ではなく最大12,000 tickの経過時間を保存し、再読み込み時に残りの待機を復元します。Phase 3保存個体の突入回復は再実行しません。古い1.3.0のNBTに回復情報がなければ、新しい5秒待機から開始します。

奈落など管理用ダメージの既存の上限バイパスも維持しています。非致死の実HP損失では同じ回復時計だけを更新して回復を停止し、管理用ダメージを新たにDPS・回避・位置変更の計算へ加えません。

Forgeの`LivingHealEvent`を一度だけ発火し、キャンセルを尊重します。回復量が他MODから増幅されても現在の上限へ収めます。イベント内で同じtickに再被弾した場合も、DamageTrackerの実HP損失revisionで検出し、処理中の回復を中止します。回復したHPはBossBarへ即反映します。

## 追加演出

既存の512×512装甲・発光PNGは変更していません。新しい`apex_healing_glow.png`は既存発光の468 texelsだけを使い、胸を強く、首・腰・関節を控えめに強調します。目・前腕刃・背中には追加発光を広げていません。

サーバーが同期する回復状態と開始tickで、突入時の短いコア発光、予兆の点滅、回復中の脈動を描画します。少量の既存`apex_core` / `apex_energy`粒子が移動中の胸へ集まります。被弾で新しい粒子生成と追加発光を止めます。既存の品質設定と最大160粒子／tickの予算を共有しています。

短い`heal_entry`と弱い低音の`heal_hum`を追加しました。humは予兆と継続回復中の控えめな間隔で鳴り、ループ音を残しません。既存13音源・音の定義・カメラ・攻撃Particleは維持しました。素材は `python3 scripts/assets/generate_healing_assets.py` で再生成できます。

## 保持と検証

CombatController、AttackManager、PhaseManager、TaCZ連携、円形判定、固定slamTarget、APEX EXECUTION、37アニメーション、geometry、装甲・通常発光テクスチャの保護対象は追加前とバイト単位で一致します。DamageTrackerとApexEntityには回復用の時計と処理を追加し、既存の瞬殺防止の計算・挙動を維持しています。回復で新しい攻撃モーションや技のタイミングは追加していません。

回復の同期データと新しい効果種類を追加したため、Apexの通信プロトコルを2へ更新しました。マルチプレイではクライアント・サーバー双方を今回の回復追加jarへ入れ替えてください。更新前1.3.0との混在は接続時に拒否し、不明な同期データによる不具合を防ぎます。TaCZは従来どおり任意、GeckoLib 4.4.9 Forge版は必須です。

`/apex debug`と`debug=true`には、HP・回復状態・視覚状態・server tick・lastDamageTick・healingReadyTick・nextHealTickを追加しました。試験結果、配布jarとハッシュ、実クライアント画像は [VALIDATION.md](../VALIDATION.md) に記録します。追加前の外観・戦闘検証は [旧1.3.0記録](VALIDATION-1.3.0-visuals.md) に保存しました。
