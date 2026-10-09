# Apex Predator 1.3.0

その後の[BGM改修](BOSS-MUSIC.md)では、提供曲 **The Stalking Motif** をOGGへ変換し、実際の交戦中にだけ流れる単一RECORDSループへ更新しました。バージョンは1.3.0のままです。[改修前の回復検証](VALIDATION-1.3.0-healing.md)と[現在の検証](../VALIDATION.md)を保持しています。

Minecraft 1.20.1 / Forge 47.4.10 / Java 17。1.2.0 のモデルと戦闘を基に、頭部・関節の外観、円形予告とダメージ、ジャンプ着地の位置合わせを修正しました。1.2.0 はコミット `a993aa4142e2c23c7e3af3bfccca368cfdeed592`、今回の作業は `feature/apex-1.3.0` に保持しています。

後続の1.3.0更新では、ここで制作したモデル・戦闘を保持して [Phase 3自己回復](PHASE3-HEALING.md) を追加しました。[外観・判定改修時の検証記録](VALIDATION-1.3.0-visuals.md)も保存しています。最新のBGM・回復検証と配布jarはルートのVALIDATION.mdを参照してください。

## 頭部と関節

旧頭部の37キューブをすべて廃止し、46の新キューブへ置換しました。閉じた逆三角形のマスク、中央稜線、額・頬・顎・側面の異なる角度の装甲、3段の先細り側頭装甲、湾曲した後頭刃を組み合わせています。目は暗い装甲の隙間にある細い橙色のセンサースリットです。鼻状の大きな柱、広い頬と露出した箱型側頭部を取り除きました。

外側の頭部シルエットは幅16.40%・高さ16.47%縮小し、前後長は15.48%増加しています。比較は同じ角度・距離・縮尺の実メッシュで行いました。この寸法には首の内部に隠れるhead所有のピボット軸を含めず、その軸を含む全geometryの寸法も制作元の計測JSONに別記しています。

首・肩・肘・手首・腰・股関節・膝・足首に黒い内部軸、蛇腹、薄いカバーと重なり装甲を追加しました。首の軸はchest側とhead側にも重なり、頭の回転や死亡姿勢で空洞が開く問題を防ぎます。V字の胴体、前腕ブレード、背部展開、逆関節脚の方向性は1.2.0を維持しています。

| 素材 | 1.3.0 |
| --- | --- |
| 高さ | 約2.7002ブロック |
| ボーン / キューブ / 面 | 28 / 333 / 1,998 |
| 装甲・発光アトラス | 各512×512 |
| 発光 | 982 texels、使用UVの約1.306% |
| UV重複 | 0 |

関節のため、1.2.0の290キューブから43増加しています。無制限な細分化は行っていません。

## 円形攻撃

旧SHOCKWAVEは開始時のoriginで予告し、発動時のApex.position()でダメージを判定していました。半径も発動時のフェーズから再計算していました。さらにプレイヤー中心点だけを調べるため、円と身体の端が重なる場合に外れていました。

不変の`AttackArea`を開始時に保存し、予告・衝撃波・ダメージ・押し出しで同じcenterとradiusを参照します。通常SHOCKWAVEは6、Phase 3は10、JUMP/SLAMは5、PULLは10、APEX EXECUTIONは15ブロックです。プレイヤーAABBの最も近いXZ点と円柱の交差で調べ、境界に0.15ブロックの許容を設けています。上下範囲は下1.5・上3.0ブロックで、半ブロック・階段・1ブロック段差・通常ジャンプを扱い、離れた高所は除外します。厳密なAABBクエリが接線上の対象を取りこぼさないよう、共通の微小誤差も処理します。

遮蔽物、盾、防具、バニラの無敵時間、Forgeダメージイベントは有効です。APEX EXECUTIONの方向別斬撃・X字の隙間・判定tick・基本ダメージ予算42も維持しました。フェーズ移行の円は従来から演出のみであり、そこへ新しいダメージは追加していません。

## ジャンプスラム

旧JUMPも対象位置自体は開始時に固定していました。しかし水平初速を`min(0.9, 距離/13)`で設定するだけで、実際の重力・抗力・接地高度による滞空時間の変化に追従していませんでした。着地演出とダメージを実際のApex位置に置くため、予告円とのずれがそのまま判定のずれになっていました。

`SlamTargetResolver`が予告時に局所の衝突形状を調べ、実際の足場Yを含む`slamTarget`を保存します。ハーフブロックと階段はApexの足幅も考慮します。読み込み済みチャンク、境界、液体、Apex全身の空きスペースを確認し、heightmapや地下までの検索は使いません。取得できる足場がない場合は攻撃を中止します。

飛行中は対象を再追尾せず、現在の垂直速度とForgeの重力から残りの飛行時間を予測し、固定地点への水平速度を補正します。通常navigationと移動入力を止め、被ダメージによる回避・位置変更の動作は着地まで待たせます。ダメージ上限と短い無敵の適用は即時のままで、回避期限も延長しません。

着地時のSLAM、衝撃波、任意の地形処理、ダメージは保存したslamTargetを使用します。新しい壁などで本体が大きく阻まれた場合は、遠くの予告地点へダメージだけを発生させず中止します。キャンセル時は変更した重力設定を復元します。

`debug=true`と`/apex debug`には、攻撃名、WarningCenter、DamageCenter、radius、playerDistance、YDifference、InsideHitbox、SlamTarget、ActualLanding、LandingErrorを追加しました。

## 保持したもの

全28ボーンの名前・親子関係・pivot・初期回転、37アニメーションの全データ、AttackTypeの予備動作・隙・クールダウンは1.2.0と一致しています。頭／首のアニメーション補正も不要でした。

アニメーションJSONのSHA-256:

```text
0218e1e3c8ecaa6dea5cc8bf6913b2a29eeda67c2c5f422df9fab975a9d21744
```

ApexEntity、DamageTracker、ApexPhaseManager、CombatDecisionPolicy、TaCZ連携、BossBar、既存Particle・SE・カメラ・残像の実装は変更していません。CombatControllerの変更はジャンプ中の移動反応の待機と診断表示に限定しています。

## 変更ファイルと確認用画像

| 分類 | ファイル |
| --- | --- |
| モデル・テクスチャ | `assets/apexpredator/geo/apex.geo.json`、`textures/entity/apex.png`、`textures/entity/apex_glow.png` |
| 制作元 | `scripts/art/generate_apex_model.py`、`scripts/art/README.md` |
| 攻撃位置・判定 | `combat/AttackManager.java`、`combat/AttackArea.java`、`combat/SlamTargetResolver.java` |
| 移動待機・診断 | `combat/CombatController.java` |
| 地形処理の共通中心 | `effect/ApexTerrainEffects.java` |
| 追加テスト | `combat/AttackAreaTest.java`、`gametest/CircularAttackGameTests.java`、`gametest/SlamGameTestSupport.java` |
| 既存テストの強化 | `gametest/VisualGameTests.java`の地形保護テストを実重力・衝突で実行 |
| バージョン・導入・記録 | `gradle.properties`、`META-INF/mods.toml`、`README.md`、`VALIDATION.md`、`dist/README.md` |

Javaのパスは`src/main/java/dev/apexpredator/`、JUnitは`src/test/java/dev/apexpredator/`、assetsは`src/main/resources/`配下です。

[正面](visuals/apex-1.3-front.png)・[側面](visuals/apex-1.3-side.png)・[背面](visuals/apex-1.3-back.png)・[頭部正面](visuals/apex-1.3-head-front.png)・[頭部側面](visuals/apex-1.3-head-side.png)・[頭部45度](visuals/apex-1.3-head-45deg.png)・[同条件の頭部比較](visuals/apex-1.2-vs-1.3-head-comparison.png)・[全身比較](visuals/apex-1.2-vs-1.3-front.png)・[turnaround](visuals/apex-1.3-turnaround.png)・[実ゲーム全身](visuals/apex-1.3-ingame.png)・[実ゲーム頭部](visuals/apex-1.3-head-ingame.png)

最終検証、着地誤差とjarハッシュは[VALIDATION.md](../VALIDATION.md)、旧版の記録は[1.2.0](VALIDATION-1.2.0.md)にあります。導入には引き続きGeckoLib 4.4.9のForge 1.20.1版が必要で、TaCZは任意です。
