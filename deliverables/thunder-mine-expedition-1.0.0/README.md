# Thunder Mine Expedition

Create・Sable・Simulated Coasters をそのまま使う、オリジナルの鉱山探検アトラクションです。木と鉄の採掘基地を出発し、荒野、廃坑、巨大地下採掘場、橋と峡谷を巡って駅へ帰還します。Coaster Engineering / VOLCANIC ABYSS の旧 MOD とワールドは変更しません。

**景観付き本番コースは実乗客で2周完走済みです。保存済みワールドを配布します。ZIP展開後のシングルプレイ確認と途中保存からの自動再開には、[検証結果](TEST_RESULTS.md)の制限があります。**

## 必要な環境

| 構成 | 版 |
|---|---|
| Minecraft Java Edition | 1.21.1 |
| ローダー | NeoForge 21.1.228 |
| Java | 21 |
| Create | 6.0.10 |
| Sable | 2.0.6 |
| Create: Coasters Simulated | 0.1.5 |
| 独立した補助 MOD | Thunder Mine Tools 1.0.0 |

Minecraft 1.20.1 / Forge / Java 17 用の旧環境とは別のゲームフォルダを使います。[必要 MOD 一覧](docs/MOD_LIST.md) に、添付 3 本と一致する公式ダウンロード URL とチェックサムを記載しています。Flywheel、Ponder、Veil、Rapier 等の同梱依存を手動で追加する必要はありません。

## 導入と乗車

1. 一覧の 4 本の JAR を専用環境の mods に入れます。
2. CoasterWorld-ThunderMineExpedition.zip を展開し、Coaster World - Thunder Mine Expedition フォルダを saves へ入れます。フォルダ直下に level.dat がある状態にします。
3. シングルプレイでワールドを開き、駅の **木製乗車ボタン（BOARD）**を右クリックします。初回は約 3 秒後に自動発車する設計です。
4. 駅へ帰還して停止したら Shift で降ります。乗り続ける場合は待機後に再発車します。

通常の体験にコマンドや OP 権限は必要ありません。駅の灰色の石製ボタンは手動発車、黒い石製ボタンは緊急停止です。詳細な Windows の保存先・展開方法・復旧時の注意は [導入手順](docs/INSTALL.md) にあります。

## ワールドと走行

コースは既存 MOD のアンカーポイントとベジェ接続で構築した閉じた軌道です。木製車体、車輪、Create の実座席を持つ Sable 車両を使います。補助 MOD は区間ごとの駆動・ブレーキ、駅、建築生成、非破壊の演出、検証を担当し、車両の姿勢と軌道拘束は元の Simulated Coasters / Sable / Rapier で計算します。

目標体験時間は 2 分 30 秒〜3 分 30 秒です。景観追加前の本番試験では、実移動約 1,035.5 ブロック、189.25 秒、最高実速度約 13.47 ブロック/秒を確認しました。最終統合後の計測・保存復元・乗車の結果は [検証結果](TEST_RESULTS.md) に記載します。速度表示や形状計算のみで正常走行とは判定しません。

専用 BGM は同梱していません。効果音・警告音と煙・砂埃などの演出を使用します。音声の実際の聞こえ方、Windows 実機の動作、FPS は未検証です。大規模な建築があるため、追加のシェーダーや性能改善 MOD を入れる前に、必要構成で起動を確認してください。

## 配布物

- CoasterWorld-ThunderMineExpedition.zip：Minecraft に通常保存したワールド。車両の復元に必要な sublevels と data を含みます。
- thunder-mine-tools-1.0.0.jar：独立した補助 MOD。第三者 MOD 本体は含みません。
- ThunderMineExpedition-Source.zip：補助 MOD ソース、コース設計、生成・検証・配布スクリプト、説明と検証記録。
- SHA256SUMS.txt / distribution-manifest.json：ファイルサイズと照合用 SHA-256。

完成 ZIP は最終検証後に GitHub の独立した成果物ディレクトリへ公開し、直接ダウンロードできるリンクを案内します。添付 MOD は改変・再パック・同梱せず、元の JAR または同一の正規配布版を利用してください。

## 調査資料とソースのビルド

- [MOD と依存関係の調査](docs/MOD_AUDIT.md)
- [レール・車両 API](docs/COASTER_API.md)
- [Sable の車両保存形式](docs/SABLE_API.md)
- [本番コース設計](data/mine-route.json)
- [テスト結果](TEST_RESULTS.md)

ソース ZIP に第三者 JAR とキャッシュは含めません。ビルドする場合は、プロジェクト直下に input-mods フォルダを作成し、指定の添付 3 本を入れます。Java 21 の JDK を用意してから、次を実行します。

~~~bash
python3 scripts/extract-compile-libs.py
cd helper
./gradlew --console=plain build
~~~

Windows では gradlew.bat --console=plain build を使用します。通常のビルドで元の MOD JAR を変更したり、補助 JAR に取り込んだりしません。Linux のクラウド試験環境の構築は [クライアント設定記録](docs/CLIENT_SETUP.md) を参照してください。
