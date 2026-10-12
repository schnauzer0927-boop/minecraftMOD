# 本番コース用の独立サーバー

小規模試験に用いたワールドを保持し、本番の鉱山コースを別ワールドへ構築するためのサーバー。製品版Minecraft **1.21.1** / NeoForge **21.1.228** / Java **21.0.12.1**を使用する。

保存先: `/workspace/thunder-mine-expedition/run-mine-production`

`scripts/prepare-mine-production-server.py`は、成功済みの公式NeoForgeサーバーインストールから変更しない依存ライブラリを参照する。新規サーバー設定と元の3MOD JARだけを別フォルダへ用意し、元の試験ワールドをコピー・変更しない。既存の設定やワールドがある対象フォルダは上書きしない。

準備時の仕様:

- ワールド名: `Coaster World - Thunder Mine Expedition`。
- 固定シード: `20261012`。
- スーパーフラットの草床: **Y0**。最下層Y=-64に65層を積む設定である。
- 地形を追加する前に、最低Y44の本番コースが既存MODの物理で走破できることを、障害物のない空間で確認する。
- バインド先: `127.0.0.1:25577`。小規模試験サーバーの25576と区別する。
- コンソール中心のローカル検証用`online-mode=false`。元MODの制限解除やJAR修正は行わない。
- view-distance6、simulation-distance4、メモリ1〜6GB、Creative / Peaceful。
- 元のSable2.0.6、Create6.0.10、Simulated Coasters0.1.5をSHA-256一致でコピー。
- 独立した補助MODの追加・サーバー起動・コース建設は、ビルド成功後の実行検証で行う。準備スクリプトは自動起動しない。

起動方法:

```sh
cd /workspace/thunder-mine-expedition/run-mine-production
./start-local.sh
```

`libraries`は、公式インストール済みの`run-native-test-production/libraries`を参照するシンボリックリンク。そのためクラウドでこのサーバーを動かす間は、参照元のライブラリを削除・更新しない。ユーザー向け完成ワールドZIPへ、このクラウド専用リンクを含める必要はない。

依存ファイル102件のパス・サイズ・SHA-256は`toolchain/neoforge-production/mine-production-library-manifest.json`へ記録。インストール元の公式NeoForgeインストーラーSHA-256、リンク先、元JARの未改変証拠は`toolchain/neoforge-production/mine-production-environment.json`へ記録した。

草床Y0は建設前の試験条件であり、鉱山の完成地形を意味しない。本番の地形生成、通常保存・再読み込み、乗車、周回、駅帰還の結果はテスト記録へ別途記載する。
