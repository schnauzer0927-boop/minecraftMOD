# 元MOD・旧成果物・配布ソースの保護

このワールドはMinecraft **1.21.1 / NeoForge 21.1.228 / Java 21**用であり、以前のCoaster Engineering / VOLCANIC ABYSSのForge1.20.1環境とは別プロジェクトとして制作した。

## 元ファイルの確認

`scripts/audit-release-preservation.py`で読み取り専用の検査を実行できる。ビルド・Minecraft起動・ワールド編集は行わない。

- 添付3JARのサイズと、最初の監査で記録したSHA-256を再照合。
- 小規模試験サーバー・本番サーバー・クライアントの計9コピーも同じSHA-256を照合。
- 旧`release-2.0.0/SHA256SUMS`に記載された15成果物を照合。旧MOD JAR、火山ワールドZIP、旧ソース・資料を含む。
- 新補助JARには`dev.thundermine`の独立コードだけが含まれ、Create/Sable/Simulated Coasters/Minecraftのクラスや元MOD JAR・元アセットを取り込んでいないことをZIPエントリから確認。
- コース生成スクリプトとJSONのSHA-256を記録し、補助MODリソース内のコースJSONと一致することを確認。

実行結果は`evidence/audit/preservation-and-source-audit.json`に保存する。これはファイル保護と配布構成の検査であり、実コース完走の代替ではない。

## ソースZIPの内容

`scripts/package-mine-world.py`は配布対象を明示して選択する。含めるのは独立した補助MODの`helper/src`、そのビルド設定、オリジナルのコースJSON、生成・検証スクリプト、説明書、明示した実行証拠である。Gradle Wrapperはビルド用ツールとしてライセンス付きテンプレートとともに保持する。

元MODの調査で使った逆コンパイル内容、`analysis/`、ビルドキャッシュ、`input-mods/`、ダウンロードした第三者JAR、`toolchain/`、実行環境の`run-*`全体はソースZIPへ含めない。明示する証拠ファイルにも逆コンパイル済みJavaや`*.javap.txt`を追加しない。

配布済みソースZIPを検査するときは次のように指定する。

```sh
python3 scripts/audit-release-preservation.py \
  --source-zip dist/release-1.0.0/ThunderMineExpedition-Source.zip \
  --helper-jar dist/release-1.0.0/thunder-mine-tools-1.0.0.jar \
  --output evidence/audit/final-distribution-integrity.json
```

この検査は、禁止した調査・実行環境ファイルや第三者MODバイナリの混入とZIP CRCを確認する。ソース内容の権利や安全性を名前空間だけから全面的に保証するものではない。

## 走行証拠の境界

小規模コースの4回の2周完走・Create座席への乗車・保存後の同一UUID復元と、本番景観追加前の189.25秒完走・正常停止・再起動復元を記録した。鉱山景観は2,509,803回のブロック配置を完了した。

景観生成の成功だけで完成ワールドを合格と扱わず、景観を含む実走行・演出・通常保存・再読込後の再走行を最後に確認する。その結果は最終[TEST_RESULTS.md](../TEST_RESULTS.md)とcommissioning記録を優先する。`evidence/audit/current-proof-summary.json`は最終統合試験前の状態を固定した監査スナップショットであり、完成判定ではない。
