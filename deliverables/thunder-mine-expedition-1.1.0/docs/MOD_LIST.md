# 必要 MOD と対応環境

| 項目 | 使用する版 |
|---|---|
| Minecraft Java Edition | 1.21.1 |
| ローダー | NeoForge 21.1.228 |
| Java | 21 |

NeoForge は [公式配布ページ](https://neoforged.net/) から入手します。今回の構成は Minecraft 1.20.1 / Forge 47.4.10 / Java 17 用ではありません。

## `mods` に入れる 4 本

| MOD | 版 / MOD ID | ファイルと正規配布元 |
|---|---|---|
| Create | 6.0.10 / `create` | [create-1.21.1-6.0.10.jar](https://cdn.modrinth.com/data/LNytGWDc/versions/UjX6dr61/create-1.21.1-6.0.10.jar) · [公開ページ](https://modrinth.com/mod/create) |
| Sable | 2.0.6 / `sable` | [sable-neoforge-1.21.1-2.0.6.jar](https://cdn.modrinth.com/data/T9PomCSv/versions/fg9dTRz9/sable-neoforge-1.21.1-2.0.6.jar) · [公開ページ](https://modrinth.com/mod/sable) |
| Create: Coasters Simulated | 0.1.5 / `simulatedcoasters` | [simulatedcoasters-0.1.5.jar](https://cdn.modrinth.com/data/W1ZUfWdr/versions/PGhQoSBq/simulatedcoasters-0.1.5.jar) · [公開ページ](https://modrinth.com/mod/create-coasters-simulated) |
| Thunder Mine Tools | 配布版 1.0.0 / `thunder_mine_tools` | このワールドと一緒に配布する `thunder-mine-tools-1.0.0.jar` |

第三者 MOD の JAR はワールド ZIP とソース ZIP に同梱しません。添付された 3 本をそのまま使用できます。正規公開版は、公式 Modrinth のバージョン API の SHA-1 / SHA-512 と照合して添付ファイルと一致することを確認しました。元 JAR のパッチ、ユーザー名偽装、制限解除プロパティは使用しません。

Thunder Mine Tools は駅操作・既存のレールに拘束された実車両の駆動とブレーキ・建築生成・演出・検証を補助します。車両と軌道の物理は元の Simulated Coasters と Sable を利用します。完成品として扱う条件は実走行・通常保存・再読込後の再走行であり、最終結果は [TEST_RESULTS.md](../TEST_RESULTS.md) を参照してください。

## 同梱される依存

| 添付 JAR | 同梱ライブラリ |
|---|---|
| Create 6.0.10 | Flywheel 1.0.6、Ponder 1.0.82+mc1.21.1、Registrate MC1.21-1.3.0+67 |
| Sable 2.0.6 | Sable Companion 1.6.0、Sable Rapier 2.0.6、Veil 4.3.2 |

これらのライブラリを単独で `mods` へ追加する必要はありません。

Sable の Create 互換範囲は `6.0.10 以上 6.1.0 未満` です。最初の起動では一覧の同じ版を使用してください。最新版への一括変更はこの組合せの検証結果に含みません。

## 追加 MOD の注意

このワールドの必要構成に追加の性能改善 MOD は含めません。Sable / Veil は ScalableLux、Rubidium、Embeddium、Celeritas と非互換です。Sodium を追加する場合は `0.8.12-alpha.2+mc1.21.1` 以上が必要です。Sable Companion の別版を追加しないでください。Create の Radium / Palladium 非互換など、詳細な宣言は [MOD_AUDIT.md](MOD_AUDIT.md) と `data/mod-manifest.json` に記録しています。

## 添付ファイルの SHA-256

```text
ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a  create-1.21.1-6.0.10.jar
9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2  sable-neoforge-1.21.1-2.0.6.jar
f80b4c3cdf9fa6188804967147761b0373e150a6eadbdc53626e5ac9700b2049  simulatedcoasters-0.1.5.jar
```

独立した補助 JAR とワールド ZIP の最終 SHA-256 は、成果物の `SHA256SUMS.txt` に記載します。
