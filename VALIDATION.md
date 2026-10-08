# Apex Predator 1.3.0 検証記録

2026-10-08（JST）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17.0.12 / Gradle 8.8 のクラウド環境で実施しました。ログ内の時刻は UTC です。基準の1.2.0は `a993aa4142e2c23c7e3af3bfccca368cfdeed592`、改修は `feature/apex-1.3.0` に保持しています。[1.2.0の検証記録](docs/VALIDATION-1.2.0.md)も保存しました。

## 外観変更と保持

旧頭部の37キューブをすべて廃止し、46個の新しい形状へ置換しました。細い逆三角形の閉じたマスク、中央稜線、角度の違う額・頬・顎・側頭装甲、暗い装甲の奥にある細い橙色センサー、長い後頭刃を新設しています。首・肩・肘・手首・腰・股関節・膝・足首には内部軸、蛇腹、重なりカバーを追加しました。

- **333キューブ / 28ボーン / 1,998面**。中立姿勢の高さ **2.7002ブロック**、幅 **1.2487ブロック**、背部フィンの幅 **0.4278ブロック**。
- 外側頭部は1.2.0比で幅 **16.40%**、高さ **16.47%** 縮小、前後長 **15.48%** 増加。この外形計測では首の内部に隠れるhead所有の接続軸を除外しています。軸を含む全寸法も `build/art/apex-model-metrics.json` に別記しました。
- 装甲と発光atlasは各 **512×512**。全UVが範囲内、UV重複0。発光は **982 texels**、使用UV面積の約 **1.306%**。
- **28ボーンの名前・順序・parent・pivot・初期回転は完全一致**。許可されたhead / neckのpivot変更も不要でした。
- **37アニメーションの全データが1.2.0とバイト単位で一致**。クリップ名、回転、position、イベント時刻を変更していません。`AttackType.java`の予備動作・隙・クールダウンも完全一致です。
- 保護対象64ファイルのうち、既存の実着地GameTest fixtureを改善した1ファイルを除く **63ファイルが完全一致**。ApexEntity、DamageTracker、PhaseManager、TaCZ連携、BossBar、Particle、SE、カメラ、残像の実装は変更していません。
- 配布jarの既存保護対象 **55 Javaクラスも1.2.0と完全一致**。変更した戦闘制御・地形処理とGameTest、新規クラスは比較対象から区別しています。

アニメーションJSONのSHA-256:

```text
0218e1e3c8ecaa6dea5cc8bf6913b2a29eeda67c2c5f422df9fab975a9d21744
```

## 円形判定とジャンプの修正

旧SHOCKWAVEは予告時のoriginと発動時のApex.position()、その時点のフェーズ別半径を別々に参照していました。円形ダメージも対象の中心点だけを調べていました。新しい不変の`AttackArea`に中心・半径・上下範囲・許容誤差を保存し、予告・衝撃波・ダメージで共有しています。対象AABBと円柱の交差、境界許容0.15ブロック、上下範囲は下1.5・上3.0ブロックです。厳密接線上でも広域検索が対象を取りこぼさないよう数値誤差を共通処理しました。盾・防具・無敵時間・遮蔽物・Forgeイベントは有効です。

旧ジャンプも対象位置は開始時に固定していましたが、初速`min(0.9, 距離/13)`だけでは重力・抗力・着地高度によって変わる滞空時間へ追従できませんでした。さらに着地演出・ダメージは実際のApex位置を参照していました。新しい`SlamTargetResolver`は予告時に局所の衝突形状と本体の足幅から実際の床Yを求めて`slamTarget`を保存します。飛行中は重力と現在の速度から残りの滞空時間を予測し、固定地点への水平速度を補正します。通常移動を止め、被ダメージによる移動反応を着地まで待たせます。ダメージ保護は即時適用し、回避期限も延長しません。着地演出・衝撃波・地形処理・ダメージはすべて保存した地点を参照します。

新たな障害物で大きく阻まれた着地は中止し、地形を無視して位置を強制したり、離れた地点にダメージだけを発生させたりしません。APEX EXECUTIONの方向別斬撃・X字判定・基本ダメージ予算42と判定tickは維持しています。フェーズ変更時の円は従来どおり演出のみです。

## 検証結果

| 検証 | 結果 |
| --- | --- |
| JUnit | **30件成功**、失敗・エラー・スキップ0。既存22件と追加8件 |
| `./gradlew test runGameTestServer` | TaCZなしのDedicated Server **40件すべて成功**、終了コード0。既存22件と追加18件 |
| `./gradlew runGameTestServer -PtaczDir=/workspace/.tools/optional-mods` | TaCZ 1.1.8-hotfix2ありのDedicated Server **42件すべて成功**、終了コード0。実イベント連携2件を追加確認 |
| 実移動によるジャンプテスト | 静止・予告後の逃走・円内ヒット・半ブロック・階段・段差など8ケースと通常Controllerの連射中ジャンプ1ケース。両サーバー実行とも **9件すべてLandingError=0.000** |
| TaCZなし `runClient -PquickPlayWorld=ApexVisual130` | ワールド入場、全37クリップ、BossBar、実Phase 2・3、死亡・Apex Core、残像、新頭部・関節を確認。GUI保存・終了、終了コード0 |
| 通常AIによる実クライアントの逃走ジャンプ | 予告後に対象が円外へ逃走しても保存された2ブロック高い床へ着地。WarningCenter / SlamTarget / ActualLanding / DamageCenterすべて `(4.500,102.000,0.500)`、**LandingError=0.000**、逃走後distance=12.70、InsideHitbox=false |
| TaCZあり `runClient -PtaczDir=/workspace/.tools/optional-mods -PquickPlayWorld=ApexVisual130` | 公式gunpack読込、実ワールド、新モデル・コンボ・sprint・円形FX・実Phase 3移行を確認。GUI保存・終了、終了コード0 |
| 最終 `./gradlew build` | **成功、終了コード0**。reobfJar完了、13秒。JUnitは直前の30件成功出力からUP-TO-DATE |
| 配布jar監査 | **成功**。Java class version 61、1.3.0メタデータ、GeckoLib依存、40リソースのソース一致、55保護クラス一致、37アニメーション一致、ZIP CRC正常、外部依存ライブラリ未同梱 |

追加テストは円中央・半径50%・AABBの端・許容境界・円外・厳密接線・対角線・上下範囲・高所除外、衝撃波の固定中心、APEX EXECUTION最終円を確認しました。ジャンプでは移動した対象を再追尾しないこと、実際の半ブロック・階段・段差・空中対象の床選択、別階層や低い天井での不適切な着地点の拒否、取消時の重力復元も確認しています。

実着地fixtureはバニラの移動・重力・衝突を使用し、`setOnGround(true)`で着地を偽装していません。通常Controllerのテストは実際の35ダメージ×3ヒットでも固定着地点を維持し、着地後に回避し、元の回避期限が延長されないことを確認しました。実クライアントの逃走観察には安全のため耐性効果を使用しており、ダメージ量とヒット／回避の成否は耐性に依存しないDedicated ServerのGameTestで検証しています。

最初の追加GameTest実行では、円境界の対象をテスト領域外へ置いたため自然地形のdeepslateが視線を遮り、1件が失敗しました。同じ距離の対象を領域内の対角方向へ配置して修正しました。製品の遮蔽物判定は弱めていません。最新の接線・下段階段テストを含めた上表の最終実行では失敗はありません。

## モデルと実描画の確認

数値監査では全37クリップ×3フェーズの **1,425キーフレーム／中間姿勢**を同時刻の1.2.0と比較し、新しい床貫通を検出しませんでした。185姿勢×17接続の **3,145回の回転キューブ接触検査**はすべて成功し、headのpitch±22° / yaw±35°の4つの最大追跡姿勢でも首との接続を保っています。首・頭・face_plate・目の55キューブはひとつの接続成分です。既存モーションの短い床への潜り込みはそのまま残るため、この検査は全フレームの自己交差がないことを保証するものではありません。

実クライアントでは全37クリップのコマンド受理と **213フレーム**の正常な1280×720画像を確認しました。idle / walk / run / sprint、blade_combo、charge、jump / slam、dodge、teleport、phase_change、rage、special、deathの代表フレームを直接確認し、首・肩・肘・腰・股関節・膝の離脱や新しい明確な貫通は見つかりませんでした。長いクリップは0.95〜2.75秒の遅い時刻も追加で撮影しています。全213画像を一枚ずつ目視審査したという意味ではありません。

側面は追加9クリップ・58枚、TaCZクライアントは4クリップ・26枚を撮影しました。両クライアントともSave and Quit → Quit Gameで正常終了し、開発用debug設定は元の`false`のバックアップとバイト単位で同一へ戻しました。

静止プレビューと1.2対1.3比較は**実際のJSON geometryとテクスチャ**から生成しました。同じ角度・距離・縮尺の頭部比較で、旧頭部とは明確に異なる形状を確認しています。`apex-1.3-ingame.png`と`apex-1.3-head-ingame.png`は実ゲームの撮影です。

描画確認はXvfb / Mesaのソフトウェア描画で実施し、ignoredの`run/options.txt`に30 FPS上限を設定しました。実機FPSベンチマーク、実銃を使った全武器の手動バランステストは未実施です。複製したワールドの過去のTaCZレジストリ情報によるTaCZなし時の欠落警告とオフライン開発認証の通信警告は、入場・描画・保存・終了を妨げませんでした。仮想音声環境で音の実聴は行っていません。音素材・イベント実装のバイト一致とゲーム内処理を確認しています。

## 成果物と画像

**629,739 bytes**。build/libsとdistのjarはバイト単位で一致しています。

SHA-256:

```text
b68a9d4f444549d0371411419171639970bfd55fa7c884473a5d7117d8d5dfd9
```

生成先: `build/libs/apexpredator-1.20.1-1.3.0.jar`

配布先: `dist/apexpredator-1.20.1-1.3.0.jar`

[正面](docs/visuals/apex-1.3-front.png)・[側面](docs/visuals/apex-1.3-side.png)・[背面](docs/visuals/apex-1.3-back.png)・[四方向図](docs/visuals/apex-1.3-turnaround.png)

[頭部正面](docs/visuals/apex-1.3-head-front.png)・[頭部側面](docs/visuals/apex-1.3-head-side.png)・[頭部45度](docs/visuals/apex-1.3-head-45deg.png)・[同条件の1.2対1.3頭部比較](docs/visuals/apex-1.2-vs-1.3-head-comparison.png)・[全身比較](docs/visuals/apex-1.2-vs-1.3-front.png)

[実ゲーム全身](docs/visuals/apex-1.3-ingame.png)・[実ゲーム頭部](docs/visuals/apex-1.3-head-ingame.png)

GeckoLib **4.4.9 / Forge 1.20.1** が必須、TaCZは任意です。ApexとGeckoLibのjarをクライアント・サーバー両側の`mods`に配置し、旧Apex jarと入れ替えます。1.2.0以前の配布jarも保持しています。

## 詳細ログ

- `build/remake-1.3/final-build.log` — 最終ビルド
- `build/remake-1.3/validation-plain.log` / `validation-tacz.log` — Dedicated Server / GameTest
- `build/test-results/test/` — JUnit XML、`build/reports/tests/test/index.html` — HTML
- `build/remake-1.3/jar-audit.json` — 配布jarと保護クラスの比較
- `build/remake-1.3/audit/geometry-audit.json` / `compatibility-review.md` / `combat-review.md` / `client-review.md` — 独立監査
- `build/remake-1.3/client-plain.log` / `client-tacz.log` — 実クライアント
- `build/remake-1.3/client/` — 全クリップ撮影、通常AIのジャンプ、各クライアントの確認記録

詳細ログは開発環境のignored出力です。必要な検証結果と比較画像は本記録・追跡されたdocsへ保存しています。再利用用環境設定にはJava 17・GeckoLib・任意TaCZ・仮想画面・1.3.0の起動と確認手順を保存します。環境設定の公開と別タスクでの復元検証は未実施です。
