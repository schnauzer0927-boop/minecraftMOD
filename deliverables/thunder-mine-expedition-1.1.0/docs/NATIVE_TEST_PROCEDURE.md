# 最小周回試験の実行手順

この手順は **試験の手順であり、走行成功の記録ではありません**。対応NeoForge依存の取得と補助MODビルドは完了しました。添付コースターJARは作者のModrinth公開版と一致し、private版用クラスの存在だけから制限ありとした初期判断を訂正しています。JARの変更や制限回避設定は使用しません。

## ビルド準備

```bash
cd /workspace/thunder-mine-expedition
source scripts/environment.sh
python3 scripts/extract-compile-libs.py
cd helper
./gradlew --console=plain build
```

Gradle 9.2.1は公式配布SHA-256で検証する設定です。NeoForgeを21.1.228に固定し、Parchment等の追加マッピングを使わず標準Mojangマッピングを使用します。第三者JARはファイル依存とし、補助JARへ埋め込みません。

## 専用ワールド

別の新規ワールドを使用します。補助コマンドは、通常世界・ワールド名 `Thunder Mine Native Test` に限定し、既存のコース・Sableサブレベル・配置位置の占有がある場合は建設を拒否する設計です。

専用サーバーなら、起動前に `level-name=Thunder Mine Native Test`、`level-type=minecraft:flat` を設定します。ゲームのEULAを確認した上でサーバーを起動し、Doneの後に管理者権限で次を実行します。サーバーコンソールでは先頭の `/` を省きます。

```text
/mine_test build
/mine_test status
/mine_test start
```

建設は4アンカー・4曲線の閉合レール、元MODの実車両、Createの座席と乗車足場だけです。生成と走行の成功は別々に判定します。構築に失敗した場合は予約記録を保持し、勝手に既存データへ上書きして再建設しません。

実走行を始めたら `MINE_NATIVE_SAMPLE` を確認します。測定値はSableの実姿勢・実速度・通過位置から取得します。実車両を毎tickの座標代入で動かしません。2周の実移動・レール拘束・減速・駅の3秒停止を検証し、成功なら `MINE_NATIVE_TEST_PASSED` を記録する設計です。ログの存在と値を確認するまでは合格扱いにしません。

## 保存・終了・再読込

```text
/mine_test status
/save-all flush
/stop
```

通常の停止完了後にワールドをバックアップします。`region/` だけでなく `data/` と `sublevels/` の `.slvlr`・`.slvls` を含めてコピーします。同じ構成で再起動し、建設コマンドを再実行せず `mine_test status` を確認します。

`MINE_NATIVE_RELOAD` の車両UUID、保存時の姿勢との差、速度、2周の結果を照合し、同じ車両で `mine_test start` を再実行します。再読込だけ、またはUUIDの文字列の復元だけでは再走行の合格になりません。途中終了した検証走行は自動再開しない設計です。

## 乗車試験

許可されたクライアントで、駅足場（目安: `28 77 1`）から車両のCreate座席を右クリックして乗車します。最低1周の実乗車と駅停止、降車を確認し、保存後の再乗車も確認します。実際のゲーム画面とログを `evidence/` に保存します。音声出力がないクラウドの試験では、聞こえ方を合格扱いにしません。

## 本番制作へ進める条件

- 実Minecraftで補助MODと添付3本が正常起動する。
- 全アンカーの接続次数が2で、車両が2周して駅に停止する。
- 通常保存・終了・再起動後も同じ車両が再走行する。
- 使用可能なクライアントで実乗車と降車を確認する。

満たせない項目は、失敗理由または未検証を記録します。条件を満たす前に大規模な建築を完成品として生成・配布しません。
