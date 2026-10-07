# Apex Predator 1.2.0 検証記録

2026-10-08（JST）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17.0.12 / Gradle 8.8 のクラウド環境で実施しました。ログ内の時刻は UTC です。基準の 1.1.0 は `2b721adac0e28fcc799b2c429bdfe7609ef2a2aa`、改修は `feature/apex-appearance-1.2.0` に保持しています。

## 外観変更と保持

添付された APEX コンセプトアートを参照し、geometry と装甲・発光テクスチャを再制作しました。細く尖った小型マスク、重なるクレスト、V 字の胸部外殻、細い胸骨・腹部発光、絞った腰、後方へ流れる肩・背部フィン、曲線状の複合前腕刃、3 本爪、逆関節脚を組み合わせています。

- **290 キューブ / 28 ボーン / 1,740 面**、実変換で高さ **2.700 ブロック**、中立姿勢の幅 **1.249 ブロック**
- 装甲と発光 atlas は各 **512×512**。全 UV の範囲正常、重複 0。発光 964 ピクセル、使用 UV 面積の約 1.50%
- **28 ボーンの名前・順序・parent・pivot・初期回転は完全一致**
- **Java・テスト・37 アニメーション・Particle・SE の保護対象 73 ファイルが 1.1.0 とバイト単位で一致**
- 配布 jar の **67 Java クラスは 1.1.0 と完全一致**。jar 内の差分は manifest、mods.toml、geometry、装甲 PNG、発光 PNG の 5 ファイルだけ

アニメーション JSON の SHA-256:

```text
0218e1e3c8ecaa6dea5cc8bf6913b2a29eeda67c2c5f422df9fab975a9d21744
```

アニメーションの回転・position・クリップ名・時刻に補正は加えていません。攻撃ロジック・判定タイミング・フェーズ・APEX EXECUTION・残像・エフェクト・音・カメラ・TaCZ 連携も変更していません。

## 検証結果

| 検証 | 結果 |
| --- | --- |
| 最終 `./gradlew build` | 成功、終了コード 0。配布用 reobfJar 完了 |
| 既存 JUnit | **22 件成功**、失敗・エラー・スキップ 0。テストコードは変更なし |
| `./gradlew build runGameTestServer` | TaCZ なしの専用サーバー **22 件すべて成功**、終了コード 0 |
| `./gradlew runGameTestServer -PtaczDir=/workspace/.tools/optional-mods` | TaCZ 1.1.8-hotfix2 ありの専用サーバー **24 件すべて成功**、終了コード 0 |
| TaCZ なし `runClient -PquickPlayWorld=ApexVisual120` | 実ワールドでモデル・装甲・発光・37 クリップ・BossBar を確認。GUI 保存・終了、終了コード 0 |
| TaCZ あり `runClient -PtaczDir=/workspace/.tools/optional-mods -PquickPlayWorld=ApexVisual120` | 実ワールド入場、銃イベント連携登録、新モデル・BossBar・代表的なコンボと演出を確認。GUI 保存・終了、終了コード 0 |
| 実フェーズ・死亡・残像 | 新規個体への実ダメージで Phase 1→2→3 を確認。背部展開、床上への実死亡、Apex Core、現在モデルの半透明残像を確認 |
| モデル・姿勢監査 | 19 クリップ系統、289 のキーフレーム・中間姿勢を基準と比較。大きな新規床貫通・関節離脱リスクなし |
| jar 監査 | Java class version 61、1.2.0 メタデータ、GeckoLib 必須依存、全リソース一致、ZIP CRC 正常、外部依存ライブラリ未同梱 |

実クライアントでは 37 クリップの受理後 0.08〜0.70 秒を 5 回ずつ、計 **185 フレーム**撮影しました。idle / walk / run / sprint、3 連コンボ、charge、jump / slam、dodge / backstep、teleport、phase_change、rage、special、death を重点的に確認し、大きなパーツ離脱や持続する深い交差は見つかりませんでした。数値監査と抽出フレームの確認であり、全フレームの自己交差を保証する検査ではありません。

描画確認は Xvfb / Mesa ソフトウェア描画で実施し、途中に tick 遅延が発生しました。TaCZ クライアント確認では、開発用の ignored `run/options.txt` だけを 30 FPS 上限へ変更しました。製品の設定・Java・モーションは変更していません。実機 FPS ベンチマーク、実銃を使った全武器の手動バランステストは未実施です。

複製した確認用ワールドには過去の TaCZ レジストリ・データパック情報が残り、TaCZ なしでは欠落警告が出ました。オフライン開発認証の通信警告もありますが、ワールド入場・描画・保存・終了を妨げませんでした。新規の Gecko モデル／アニメーション解析・描画例外は検出していません。

## 成果物と画像

`build/libs/apexpredator-1.20.1-1.2.0.jar` — 589,843 bytes

同一ファイルを `dist/apexpredator-1.20.1-1.2.0.jar` にコピーしました。

SHA-256:

```text
7334bbc353c4bce4b29fdcd9c3a9e2ff9e8541569d61fbd7be26c558475cb484
```

[正面](docs/visuals/apex-1.2-front.png)・[側面](docs/visuals/apex-1.2-side.png)・[背面](docs/visuals/apex-1.2-back.png)・[四方向図](docs/visuals/apex-1.2-turnaround.png)・[実ゲーム](docs/visuals/apex-1.2-ingame.png)

GeckoLib 4.4.9 の Forge 1.20.1 版が必須、TaCZ は任意です。Apex と GeckoLib の jar を両側の `mods` に配置し、旧 Apex jar と入れ替えます。1.1.0 の配布 jar と [旧検証記録](docs/VALIDATION-1.1.0.md) も保持しています。

## 詳細ログ

- `build/remake/final-build.log` — 最終ビルド
- `build/remake/validation-plain.log` / `validation-tacz.log` — 専用サーバー・既存 GameTest
- `build/test-results/test/` — 既存 JUnit XML、`build/reports/tests/test/index.html` — HTML
- `build/remake/compatibility-audit.json` — 保護ファイル・rig・UV・姿勢の比較
- `build/remake/client-plain.log` / `client-tacz.log` — 実クライアント起動・保存・終了
- `build/remake/client/PLAIN-RESULT.md` / `build/remake/client/` — 37 クリップ、実フェーズ、死亡、残像の撮影

再利用用環境設定には、Java 17・GeckoLib・任意 TaCZ・仮想画面と 1.2.0 の起動手順を保存しています。環境設定の公開と別タスクでの復元検証は未実施です。
