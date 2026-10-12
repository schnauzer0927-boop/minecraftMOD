# 小規模試験用の公式NeoForgeサーバー

Minecraft **1.21.1** / NeoForge **21.1.228** / Java **21.0.12.1**。公式NeoForgeインストーラーを使用して別フォルダのテストサーバーを準備した。開発用の名前変更されたクラス環境に加え、製品版ランタイムで独立した補助MODと添付MODを確認するために使用する。

保存先: `/workspace/thunder-mine-expedition/run-native-test-production`

```sh
# 補助MODのビルド成功後、rootの検証手順で必要なJARをmodsへ追加する。
cd /workspace/thunder-mine-expedition/run-native-test-production
./start-local.sh
```

この準備スクリプト自体はゲームサーバーを起動しない。`scripts/prepare-native-production-server.py`は、既存の設定済みサーバーやワールドを検出した場合、上書きを拒否する。

## インストーラー確認

取得元:

`https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.228/neoforge-21.1.228-installer.jar`

- サイズ: 6,961,386 bytes。
- SHA-256: `5123d7ae23492a512bc6418a6da5d3c765c8346c0558b64a0e1bb47234b87fe9`。
- 同じ公式Mavenの`.sha256`を取得して完全一致。
- TLS証明書検証を維持。
- Javaにはクラウドの既定HTTP(S)プロキシを明示した。別のプロキシやミラーを使用していない。
- 公式インストーラーの終了コード0と`The server installed successfully`を確認。依存ライブラリのチェックサム照合は公式インストーラー自身が行う。

根拠: `toolchain/neoforge-production/server-install-provenance.json`、`evidence/neoforge-production-install.log`。

## サーバー設定

- `127.0.0.1:25576`だけにバインドする。
- コンソールによるローカル検証用の`online-mode=false`。クライアントのユーザー名許可リストを解除する設定ではない。
- ワールド名`Thunder Mine Native Test`、固定シード20261012、草床Y0のスーパーフラット。Minecraft 1.21.1の最下層Y=-64から65層を積むため、最上層は`-64+65-1=0`となる。
- Creative / Peaceful、view-distance6 / simulation-distance4。
- メモリ1〜4GB、RCON/Queryを無効。
- 添付3JARをそのままコピーし、SHA-256とサイズを再確認した。
- Simulated CoastersのJARは公式公開版と全バイト一致し、未使用のprivate-gateクラスに対する変更は行っていない。

固定の日中照明を必要とする試験では、起動後コンソールで以下を実行する。通常のサーバーコンソールコマンドであり、NBTファイルの直接改変ではない。

```text
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doMobSpawning false
time set 6000
weather clear
```

ワールド保存・走行・再読み込みの成否は、実際にrootが行う小規模コースの検証結果へ記録する。インストール成功のみではコース完成と判断しない。
