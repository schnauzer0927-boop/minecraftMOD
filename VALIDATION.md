# 検証記録

2026-10-07、現在のクラウド環境で実施。Minecraft 1.20.1、Forge 47.4.10、Java 17.0.12、Gradle 8.8。

| 検証 | 結果 |
| --- | --- |
| `bash scripts/setup-cloud.sh --with-display` | 成功。JDK・Gradle・プロキシ・署名付き Debian パッケージによる仮想画面を準備。再実行も成功 |
| `./gradlew build runGameTestServer` | 成功、終了コード 0。最終ソースのコンパイル・テスト・reobfJar・実サーバー検証 |
| JUnit | 19 件成功、失敗・エラー・スキップ 0 |
| TaCZ なし GameTest | 16 件すべて成功 |
| `runGameTestServer -PtaczDir=/workspace/.tools/optional-mods` | 公式 TaCZ 1.1.8-hotfix2 導入、18 件すべて成功、終了コード 0 |
| `runClient -PquickPlayWorld=ApexSmoke` | TaCZ なしで実ワールド入場・召喚・モデル・APEX BossBar・アイコン・デバッグを確認。GUI 保存・終了、終了コード 0 |
| jar 検査 | Java class version 61、MOD メタデータ・独自テクスチャ・戦利品・LICENSE を確認。TaCZ・JUnit・GeckoLib のライブラリを同梱しない |

GameTest は属性・スポーンエッグ、通常防具、35 のヒット上限、同一弾の分割ダメージ、Forge イベントでのダメージ増幅、100 ダメージ時の回避、120 tick の極端な連射、回避の終了、NoAI 時の短い無敵の終了、400/200 フェーズ境界と NBT 保存、Core の実ドロップ、近接の予備動作・隙・クールダウン、対象変更での攻撃中断、突進、必殺技の横回避・遮蔽物、ワープの安全性とキャンセル、召喚・デバッグコマンドを検証しています。

TaCZ の追加 2 件は、実際のイベントクラスを Forge EventBus へ配信し、銃声検知・クライアント側イベント除外・銃判定・HP/DPS の重複計上防止を確認します。実銃を手動で撃って全武器のバランスを比較するプレイテストは行っていません。

## 成果物

`build/libs/apexpredator-1.20.1-1.0.0.jar` — 111,658 bytes

SHA-256:

```text
8e9c417f0ff2a8ddefadc11912f36c2753612a5b4332be57867b2c33ead50407
```

## 詳細ログ・画面

- `build/final-validation.log` — 最終ビルド・通常 GameTest
- `build/test-results/test/` — JUnit XML、`build/reports/tests/test/index.html` — HTML レポート
- `build/gametest-tacz.log` — TaCZ 起動・連携 GameTest
- `build/client-world-smoke.log` — 実クライアント起動・コマンド・保存・終了
- `build/client-Apex.png` — Apex、BossBar、素材・卵アイコン
- `build/client-Apex-debug.png` — 召喚・デバッグ表示

開発用のオフライン認証キー取得は通信エラーを記録しましたが、ローカルワールド・GameTest の実行を妨げませんでした。クライアント描画検証用ワールドは TaCZ 付きテストワールドから複製したため、削除された TaCZ のレジストリ警告も発生しました。最終の TaCZ なし GameTest は別に作成した新しいワールドで成功しています。オンライン認証・外部サーバー接続・全ての他 MOD との組み合わせはこの記録の対象外です。

再利用用 `install_script`・`start_skill`・必要な通信ドメインは環境設定ドラフトへ保存済み。環境の公開および別タスクでの復元検証は行っていません。
