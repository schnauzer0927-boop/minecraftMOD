# Apex Predator 1.1.0 検証記録

2026-10-07、Minecraft 1.20.1 / Forge 47.4.10 / Java 17.0.12 / Gradle 8.8 のクラウド環境で実施しました。1.0.0 は `work` ブランチの `f547038` と旧配布 jar に保持し、改修は `feature/apex-visuals-1.1.0` に保存しています。

## 結果

| 検証 | 結果 |
| --- | --- |
| 最終 `./gradlew build` | 成功、終了コード 0。コンパイル・JUnit・配布用 reobfJar 完了 |
| JUnit | **22 件成功**、失敗・エラー・スキップ 0 |
| `./gradlew build runGameTestServer` | 成功、終了コード 0。TaCZ なしの専用サーバー **22 件すべて成功** |
| `./gradlew runGameTestServer -PtaczDir=/workspace/.tools/optional-mods` | 成功、終了コード 0。公式 TaCZ 1.1.8-hotfix2 ありの専用サーバー **24 件すべて成功** |
| `./gradlew runClient -PquickPlayWorld=ApexVisual110` | TaCZ なしで実ワールド入場、召喚、専用モデル・発光テクスチャ・BossBar・Phase 2/3 を確認。GUI で保存・終了、終了コード 0 |
| `./gradlew runClient -PtaczDir=/workspace/.tools/optional-mods -PquickPlayWorld=ApexVisual110` | TaCZ ありで実ワールド入場、連携リスナー登録、モデル・発光・BossBar・残像を確認。GUI で保存・終了、終了コード 0 |
| 37 アニメーション | 全プレビューコマンドをゲーム内で実行。37 クリップ分を撮影し、新しいモデル描画／アニメーション解析エラーなし |
| 戦闘演出 | 実画面で橙色の斬撃弧、拡大衝撃波、ワープ柱、必殺技の警告リング・方向線・X 斬撃を確認 |
| 死亡と残像 | 実際の死亡による床上への崩れ落ち、Apex Core ドロップを確認。位置移動後の半透明モデル残像と消失を確認 |
| jar | Java class version 61、1.1.0 メタデータ、37 クリップ、28 ボーン、発光・粒子・音源・戦利品・LICENSE を確認。GeckoLib・TaCZ・JUnit を同梱しない |

既存のダメージ上限、弾丸の分割ダメージ、Forge イベントによる増幅、100 ダメージ時の回避、極端な連射、短い無敵の終了、400/200 フェーズ境界と保存、Core ドロップ、近接の予兆と隙、対象変更、突進、必殺技の退避・遮蔽物、ワープの安全性とキャンセル、召喚・デバッグのテストを維持しました。追加テストは骨階層・UV・発光マスク・アニメーション参照、同期連番・開始時刻、フェーズ停止、3 連コンボ、APEX EXECUTION の予兆・ダメージ上限・遅延衝撃波、2 秒の死亡、標準設定での地形保護を検証します。

TaCZ 追加 2 件は実際のイベントクラスを Forge EventBus へ配信し、銃声検知・クライアント側イベント除外・銃判定・HP/DPS の重複計上防止を確認します。実銃を手動で撃つ全武器のバランステスト、実機の FPS ベンチマークは未実施です。描画確認は Xvfb / Mesa ソフトウェア描画、音声は仮想出力で実施しました。

実戦の短い確認ではジャンプ・着地攻撃を確認しましたが、カメラが足場下へ落ちました。残像の確認は別途、TaCZ クライアントで視点を固定して行っています。短い近接・被弾クリップの一部は撮影時には復帰へ進んでいましたが、再生コマンドの受理と解析・描画の成功を確認しています。

## 配布ファイル

`build/libs/apexpredator-1.20.1-1.1.0.jar` — 511,356 bytes

同一ファイルを `dist/apexpredator-1.20.1-1.1.0.jar` に配置しました。

SHA-256:

```text
f6d153831d282c21440c4d7421bfe531e7272e436793d3f7816324bec740c8a8
```

ゲームでの使用には **GeckoLib 4.4.9 の Forge 1.20.1 版が必須**です。Apex と GeckoLib の 2 個の jar をクライアント・サーバー双方の `mods` へ入れます。TaCZ は任意です。旧 Apex jar は重複させないでください。

## ログと画像

- `build/visual-final-build.log` — 最終ビルド
- `build/visual-validation.log` — 初回ビルド・TaCZ なし GameTest
- `build/visual-gametest-tacz.log` — TaCZ あり GameTest
- `build/test-results/test/` — JUnit XML、`build/reports/tests/test/index.html` — HTML レポート
- `build/visual-client-plain.log` / `build/visual-client-tacz.log` — 実クライアント起動・ワールド入場・保存・終了
- `build/visual-smoke/SMOKE.json` / `build/visual-smoke/` — 37 クリップ、演出、死亡、残像の撮影記録
- [モデル四方向](docs/visuals/apex-model-turnaround.png)・[実ゲームの斬撃](docs/visuals/apex-in-game.png)・[実モデル残像](docs/visuals/apex-afterimage.png)

最初の開発クライアントで GeckoLib の SRG refmap と Mojmap の不一致が発生し、全開発 run に refmap 再マッピングと `createSrgToMcp` の依存を設定して修正しました。その後の両クライアントは正常に終了しています。配布 jar の利用者がこの開発用設定を追加する必要はありません。

オフライン開発用の認証・更新確認の通信警告は、ローカルワールドとテストの実行を妨げませんでした。今回の添付にはコンセプト画像が含まれなかったため、専用モデルは文章の外観指定から制作しています。

再利用用の環境設定ドラフトに、セットアップスクリプト、Java 17・GeckoLib・任意 TaCZ・開発 refmap の起動手順、必要な通信ドメインを保存しています。環境設定の公開および別タスクでの復元検証は未実施です。
