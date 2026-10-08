# Apex Predator 1.3.0 — Phase 3自己回復の検証記録

2026-10-08（JST）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17.0.12 / Gradle 8.8で実施しました。ログ時刻はUTCです。回復追加前の基準は `f56b9335cb9d95ed78b405cca0d1a35045ead5fd`、改修は `feature/apex-phase3-healing` に保持しています。バージョンは1.3.0のままです。[旧1.3.0の外観・判定検証](docs/VALIDATION-1.3.0-visuals.md)も保存しました。

## 回復仕様

| 項目 | 初期値・条件 |
| --- | --- |
| 対象 | 生存中のPhase 3だけ |
| 突入時 | 実際のPhase 3移行時に一度だけ60 HP。最大HPまで |
| 継続回復開始 | 最後の実HP損失から100 tick（5秒）経過後 |
| 予兆 | 通常待機の最後20 tick（1秒）に点滅と集まる粒子 |
| 継続量 | 20 tickごとに4 HP |
| 継続上限 | 300 HPと実際の最大HPの小さい方 |
| 被弾 | 正の実HP損失で同じ処理内に即停止し、100 tick待ち直す |
| 一時停止 | APEX EXECUTION全体、phase_change、stagger、死亡 |
| 再開 | 無被弾条件を再確認し、新しい20 tick予兆。停止中の回復をためない |
| 通常戦闘 | 追跡・移動・近接などは回復のために停止しない |
| 完全無効化 | `phase3Healing=false`で突入・継続回復と専用演出を停止 |

設定は `phase3Healing=true`、`phase3EntryHeal=60`、`phase3HealDelaySeconds=5`、`phase3HealPerSecond=4`、`phase3HealCap=300` です。詳細は [Phase 3自己回復](docs/PHASE3-HEALING.md)を参照してください。

## DamageTrackerと互換性

最後の有限・正の実HP損失tickを既存DamageTrackerに追加し、20 tickのDPS履歴が消えても回復待機の時計は保持します。新しいPhase3HealingControllerはこの時計を参照して予兆・回復間隔を管理し、別のダメージ時計を持ちません。既存の被弾上限・実DPS窓・回避・位置変更の計算は維持しています。

移行を引き起こした実ダメージを回復**前**に記録するため、60 HPの回復がDPSを隠しません。TaCZ弾丸の実HP損失も同じ経路で回復を停止します。銃声やPostイベントだけでは時計を動かさず、ダメージを重複計上しません。奈落の非致死ダメージは従来の上限バイパスを維持し、回復時計だけ更新して、従来のDPS・回避に新たに算入しません。

ForgeのLivingHealEventを一度だけ発火し、キャンセル・量の変更を尊重しながら上限を守ります。同tickのイベント内再被弾も実損失revisionで検出し、処理中の回復を中止します。BossBarはHP増加時に即更新します。

NBTには経過時間を保存して、読み込み後の残り待機を復元します。Phase 3保存個体に突入時回復を再実行しません。旧形式のNBTは保守的に新しい待機から開始します。NoAIでもサーバーtickで回復時計を進め、死亡中は回復しません。

追加同期データ・効果種類に合わせてApex通信プロトコルを2へ更新しています。マルチプレイでは両側を今回の追加jarへ更新してください。更新前1.3.0との混在はForgeの接続確認で拒否します。TaCZは任意、GeckoLib 4.4.9 Forge版は必須です。

## 保持した素材・戦闘

- **37アニメーションの全データ、geometry、装甲PNG、通常発光PNGはバイト単位で一致**。28ボーン、333キューブ、512×512 atlas、高さ2.7002ブロックを維持。
- CombatController、AttackManager、AttackType、PhaseManager、円形判定と固定slamTarget、TaCZ連携は変更なし。
- 独立監査の **40保護パスが完全一致**。既存13 OGG、音の各定義、Particle素材、残像・カメラの実装を維持。
- 回復専用の512×512マスクを追加。既存発光の **468 texels**だけを選び、胸を強く、首・関節を控えめに脈動。目・刃・背中へ追加発光を広げない。
- 少量の既存apex_core / apex_energy粒子が現在の胸へ移動。同期状態が解除されると生成停止。既存品質・160粒子／tickの予算を共有。
- 追加SEはheal_entry **0.52秒**、heal_hum **1.15秒**。どちらもmono / 44.1 kHz / Ogg Vorbis。元の音源は変更なし。

アニメーションJSONのSHA-256:

```text
0218e1e3c8ecaa6dea5cc8bf6913b2a29eeda67c2c5f422df9fab975a9d21744
```

## テスト結果

| 検証 | 結果 |
| --- | --- |
| JUnit | **47件すべて成功**。既存30＋追加17、失敗・エラー・スキップ0 |
| `./gradlew test runGameTestServer` | TaCZなしのDedicated Server **56件すべて成功**、終了コード0 |
| `./gradlew runGameTestServer -PtaczDir=/workspace/.tools/optional-mods` | TaCZありのDedicated Server **59件すべて成功**、終了コード0 |
| 追加回復GameTest | **16件成功**。以前の40件もすべて成功 |
| 追加TaCZ回復GameTest | 実登録bullet型／damage sourceで被弾停止、再開、GunFire/Post単独で時計不変を確認 |
| 独立Java・ソース監査 | 正確な80/100/20 tick、同tick損失revision、上限、無効化、clock rollback、NBT上限を確認 |
| TaCZなしの実クライアント | 突入60 HP、予兆、回復、被弾停止・再開、300上限、通常戦闘との併存、死亡を確認。GUI保存・終了、終了コード0 |
| TaCZありの実クライアント | 公式gunpack、実ワールド、回復発光・同期状態、実被弾による停止を確認。GUI保存・終了、終了コード0 |
| 最終 `./gradlew build` | **成功、終了コード0、15秒**。reobfJar完了。JUnitは直前の47件成功出力からUP-TO-DATE |
| 配布jar監査 | **成功**。79 Javaクラスはclass version 61、既存58クラスのバイト一致、37アニメーション一致、追加素材・同期データ・protocol 2、ZIP CRC正常、外部依存ライブラリ未同梱、dist完全一致 |

追加GameTestはPhase 1・2で回復しないこと、Phase 3の一回だけの60 HPとDPS/BossBar、5秒待機・予兆・被弾停止・5秒後再開、300 HP、死亡、無効設定、最大HP、通常CHASE/MELEE、stagger、NBT残り待機・旧形式、Forgeイベント増幅・キャンセル・イベント中被弾、非致死の奈落を検証します。APEX EXECUTIONは既存の94 elapsed tickを実際のAttackManagerで進め、攻撃ダメージと従来終了時刻を維持しながら、全体で継続回復せず、終了後に新予兆から再開することを確認しました。

設定無効化のGameTestは個体の実設定境界をoverrideして、並行テストへ影響するグローバルConfig変更を避けています。正確な100 tickの境界は純粋JUnitで、実サーバーはentity tick／GameTest callback順序を考慮して期限前・期限後と同一hurt内の即停止を確認しています。

最初の専用サーバー実行は55件中54成功、既存PULL境界試験だけが失敗しました。追加テストで変わった試験場配置により、旧対象が12ブロックの領域外の天然deepslateに埋まっていました。同じ距離の対角配置で身体全体を試験場内へ置き、視線の明示assertを追加しました。旧フェーズ半径試験も同様に配置を改善し、判定目的を強化しています。製品の遮蔽物・範囲判定は変更していません。修正後と奈落テスト追加後の最終56/59実行はすべて成功しています。

旧試験の保存HPは突入回復に合わせて260へ更新しました。瞬殺防止試験は同一tickのDamageTracker増分で実ダメージを測り、回復によるHP差を誤って被ダメージから差し引かないよう修正しました。ダメージ上限・回避中の上限・生存・攻撃有効性・回避期限のassertは維持しています。

## 実クライアントと描画

保持した撮影ワールドから検証専用の`ApexHealing130`を複製し、実際の`runClient`で確認しました。NoAI個体では、HP201から8ダメージで193へ減った同じPhase 3移行で60回復し、HP253になりました。最後の被弾から81 tickの時点で予兆・visual=2、100 tickを超えた観測で最初の4 HPとvisual=3を確認しました。100 tickちょうどの境界はJUnitで検証しています。

HP300で継続回復・追加発光が停止し、20ダメージ後は280のまま5秒待機して再開しました。回復中に再度実ダメージを与えた後はHP272・INACTIVE・visual=0を確認し、その後の観測では280、284、288、292、296、300へ4 HPずつ再開して、上限で再び停止しました。予兆から実回復までの被弾待機はserver tickとHPログで照合しています。同じhurt処理内の即停止はGameTestでも検証しています。`special_prepare`の描画確認中は回復を停止し、実際に死亡させた個体も回復しませんでした。必殺技全体の攻撃・終了時刻と回復停止は実AttackManagerを使うDedicated Server試験で検証しています。

通常AIの個体では、Survivalの`target=Dev`を維持したCHARGE準備・突進とRECOVERY中に`healing=HEALING / healingVisual=3`を記録し、HP184→188→192の増加を確認しました。移動・通常攻撃と回復が同時に進みます。観察対象には耐性効果を付けています。初期の小さい撮影台ではノックバック後にLOSを失ったため、検証ワールドだけ足場を広げました。製品の索敵・視線・ノックバックは変更していません。

[回復の予兆](docs/visuals/apex-phase3-healing-warning.png)・[回復中の追加発光](docs/visuals/apex-phase3-healing-active.png)・[被弾による停止](docs/visuals/apex-phase3-healing-hit-stop.png)は無加工の実ゲーム撮影です。3枚の静止画だけから停止時刻や脈動を断定せず、時間と状態はログ・試験結果で確認しています。

TaCZクライアントも公式gunpackを読み込んで同じワールドへ入り、HP288・HEALING・visual=3を[無加工撮影](docs/visuals/apex-phase3-healing-tacz.png)しました。その後の20ダメージ後の観測ではHP280・INACTIVE・visual=0を確認しました。クライアントの被弾確認は`/damage`のgeneric damageです。TaCZ銃の実damage sourceとbullet型による回復停止・再開は59件のDedicated Server試験で検証しています。全銃種の手動バランス試験は実施していません。

両クライアントともSave and Quit→Quit Gameで正常終了し、終了コード0です。移行後の5つの回復初期値を含むcommon configをバックアップと完全同一に戻し、`debug=false`を保持しました。Xvfb / software Mesa / 30 FPS、仮想音声を使用しました。実PCのFPS計測やSEの聴感検査は行っていません。

[通常モデルの実ゲーム画像](docs/visuals/apex-1.3-ingame.png)・[頭部の実ゲーム画像](docs/visuals/apex-1.3-head-ingame.png)・[1.2対1.3比較](docs/visuals/apex-1.2-vs-1.3-head-comparison.png)

## 配布jar

生成先: `build/libs/apexpredator-1.20.1-1.3.0.jar`

同一jarの配布先: `dist/apexpredator-1.20.1-1.3.0.jar`

**668,418 bytes**。build/libsとdistはバイト単位で一致し、`.sha256`も更新しました。

SHA-256:

```text
83ccbb1a5eb7a115b78db8e06f896fd37cfb23b9cda8e94f2eb20ac9fbf4897a
```

GeckoLib **4.4.9 / Forge 1.20.1** が必須です。ApexとGeckoLibを両側のmodsへ配置し、旧Apex jarを取り除きます。TaCZは任意です。旧版の成果物はGit履歴と従来の1.0／1.1／1.2 distへ保持しています。

## ログ・再現手順

- `build/healing/validation-plain.log` — 初回55件と試験場配置の失敗
- `build/healing/validation-plain-final.log` — 最終56件と47 JUnit
- `build/healing/validation-tacz.log` — 最終59件、任意連携登録
- `build/healing/final-build.log` / `jar-audit.json` — 最終jar
- `build/test-results/test/` / `build/reports/tests/test/index.html` — JUnit XML / HTML
- `build/healing/audit-review.md` / `audit-final.json` / `fixture-terrain-proof.json` — 独立監査と地形証拠
- `build/healing/client/RESULTS.md` / `RESULTS.json` — 両クライアントの結果・範囲・終了コード、HP・server tick、無加工撮影の証拠

詳細ログは開発環境のignored出力です。再利用用環境設定はJava 17・GeckoLib・任意TaCZ・仮想画面と、この回復追加版の起動／検証手順を対象とします。環境設定の公開・別タスクでの復元検証は未実施です。
