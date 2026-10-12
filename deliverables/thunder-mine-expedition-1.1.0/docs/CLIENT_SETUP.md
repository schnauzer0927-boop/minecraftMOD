# 実機クライアント検証用環境

既存の Coaster Engineering 2.0 環境とは別に、以下の公式クライアントを構築しました。

- Minecraft Java Edition 1.21.1
- NeoForge 21.1.228
- Java 21
- 添付の Create 6.0.10 / Sable 2.0.6 / Simulated Coasters 0.1.5

保存先は `/workspace/thunder-mine-expedition/run-native-client-production` です。ゲームディレクトリはその中の `game`、シングルプレイの保存先は `game/saves` です。

## セットアップの再現

```bash
cd /workspace/thunder-mine-expedition
python3 scripts/prepare-native-production-client.py
```

Mojang の公式バージョン JSON・クライアント・ライブラリ・アセットを、公式メタデータの SHA-1 と照合します。NeoForge は公式インストーラーを SHA-256 と照合してから `--installClient` で実行します。添付 JAR は変更せず、コピー前後の SHA-256 を照合します。

記録ファイル：

- `run-native-client-production/client-install-provenance.json`
- `evidence/neoforge-production-client-install.log`
- 起動時に生成する `run-native-client-production/launch-evidence.json`

## 検証用の起動

```bash
bash scripts/start-native-production-client.sh
```

ローカル試験サーバーへ直接接続する場合：

```bash
bash scripts/start-native-production-client.sh --multiplayer 127.0.0.1:25576
```

保存済みワールドを直接開く場合：

```bash
bash scripts/start-native-production-client.sh --singleplayer 'Thunder Mine Native Test'
```

補助 MOD の更新時は、停止中のクライアントの `game/mods` へ検証対象 JAR をコピーしてから起動します。スクリプトは補助 MOD のビルドやコピーを自動実行しません。

このクラウドでの画面検証は Xvfb / Mesa を使います。`Dev` はローカル開発用のユーザー名で、オンライン認証・Microsoft アカウントの接続試験は行いません。Simulated Coasters の private-build 制限を変更するプロパティは指定しません。音声バックエンドは通常の OpenAL の null 出力を使うため、ゲーム内音声の実際の聞こえ方は未検証です。

## セットアップ結果

公式 NeoForge クライアントインストーラーは正常終了しました。Linux 用 Mojang ライブラリ 56 ファイル、Minecraft アセット 3,888 オブジェクト、添付 JAR 3 ファイルの照合が成功しました。

この記録は環境構築の成功を示します。走行・乗車・ワールド保存・再読込の結果は `TEST_RESULTS.md` に別途記録します。
