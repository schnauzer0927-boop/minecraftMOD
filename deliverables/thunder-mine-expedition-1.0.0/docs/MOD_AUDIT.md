# 添付MODの調査記録

調査日: 2026-10-12。添付JARを読み取り専用で開き、`META-INF/neoforge.mods.toml`、Manifest、Jar-in-Jar、ライセンス、クラスヘッダーを確認した。JARの変更・再パック・パッチは行っていない。

## 実際に必要な環境

**Minecraft Java Edition 1.21.1 / NeoForge 21.1.228以上 / Java 21以上**。

これは以前のCoaster EngineeringのMinecraft 1.20.1 / Forge 47.4.10 / Java 17環境とは別環境である。3ファイルすべて`META-INF/neoforge.mods.toml`を持ち、Minecraft `[1.21.1]`を要求する。全トップレベルJavaクラスのmajor versionは65であり、Java 21を必要とする。テスト環境の基準として最小共通対応版NeoForge **21.1.228**を推奨する。さらに新しい21.1版を使用する場合も実行確認が必要。

| 添付ファイル | MOD ID | バージョン | サイズ | 主な必須依存 |
|---|---|---:|---:|---|
| `sable-neoforge-1.21.1-2.0.6.jar` | `sable` | 2.0.6 | 12,986,135 bytes | NeoForge >=21.1.228、Minecraft 1.21.1 |
| `simulatedcoasters-0.1.5.jar` | `simulatedcoasters` | 0.1.5 | 1,892,119 bytes | NeoForge >=21.1.228、Minecraft 1.21.1、Create >=6.0.10、Sable >=2.0.0、クライアントFlywheel >=1.0.6 |
| `create-1.21.1-6.0.10.jar` | `create` | 6.0.10 | 19,123,767 bytes | NeoForge >=21.1.219、Minecraft 1.21.1、Ponder >=1.0.82、クライアントFlywheel >=1.0.0かつ<2.0 |

SableのCreate互換範囲は`[6.0.10,6.1.0)`であり、添付Create6.0.10はその範囲に入る。SableのFlywheel互換範囲は`[1.0.6,)`であり、添付Createに同梱されるFlywheel1.0.6で満たせる。

## 同梱依存ライブラリ

`META-INF/jarjar/metadata.json`と内部JARの実在を確認した。

| 外側のJAR | 内部ライブラリ | バージョン |
|---|---|---|
| Create | Registrate | MC1.21-1.3.0+67 |
| Create | Flywheel | 1.0.6 |
| Create | Ponder | 1.0.82+mc1.21.1 |
| Sable | Sable Companion | 1.6.0 |
| Sable | Sable Rapier | 2.0.6 |
| Sable | Veil | 4.3.2 |

宣言された必須MOD依存に限れば、追加のトップレベルMOD JARは不要。NeoForgeのインストール時にはMinecraft・NeoForge・ライブラリ・Java実行環境を通常どおり用意する。Jar-in-Jar内部の依存を手動で取り出して`mods`へ追加する必要はない。

Sable Rapierには圧縮されたネイティブバイナリ`natives/sable_rapier/sable_rapier_binaries.zip.l4z`が同梱される。ライブラリの存在は確認済みだが、この静的調査のみではOSごとの実行成功を保証しない。Windows実機については別途未検証として扱う。

## 同時に入れないMOD

最小テストでは添付3本と独立したワールド補助MODだけを使用する。

- Sable: ScalableLuxとは非互換。Sodiumを入れるなら`0.8.12-alpha.2+mc1.21.1`以上。Sable Companionは同梱の1.6.0を使用し、1.6.0より新しい別JARを追加しない。
- 同梱Veil: Rubidium、Embeddium、Celeritasは非互換。Sodiumを入れるなら同じく`0.8.12-alpha.2+mc1.21.1`以上。
- Create: Radium、Palladiumとは非互換。Lithiumを併用するなら0.14.7以上。

追加パフォーマンスMODは制作と動作確認の前提にしない。

## Simulated Coastersの公開元と未使用の制限クラス

添付JARには`dev.silvergold.simulatedcoasters.client.PrivateFriendBuildGate`とその専用画面が含まれる。クラス内の表示文字列に以下が存在する。

> Simulated Coasters — private build
>
> Only whitelisted accounts may play with this JAR.

ただし、**クラスの存在だけで制限が有効とは判断できない**。通信設定の復旧後、公式Modrinthの公開版0.1.5を確認したところ、添付JARは公開版とSHA-1・SHA-512が一致し、公開ファイルの再ダウンロードでも全バイト一致した。

- 公開元: [Create: Coasters Simulated](https://modrinth.com/mod/create-coasters-simulated)、作者SilverGold、プロジェクトID `W1ZUfWdr`。
- 公開バージョンID `PGhQoSBq`、0.1.5、2026-08-15公開、MIT、Minecraft 1.21.1、NeoForge。
- [公開版0.1.5の取得URL](https://cdn.modrinth.com/data/W1ZUfWdr/versions/PGhQoSBq/simulatedcoasters-0.1.5.jar)。添付ファイルを交換する必要はない。
- JAR内全エントリの参照検査では`PrivateFriendBuildGate`への参照はそのクラス自身と専用画面だけ。ほかのクラス・リソースからの呼出し参照はない。
- `javap`で確認した通常の`SimulatedCoastersClient`初期化・クライアントtickには、制限クラスの登録・呼出しがない。
- 制限クラス自身に自動イベント登録用の可視・不可視アノテーションもない。

したがって、これは公開版に残った**未使用のヘルパークラス**と判断し、通常のクライアント起動を試験する。制限解除用JVMプロパティ、許可ユーザー名の偽装、JARの変更は使用しない。初期調査では存在だけから制限付きビルドの可能性を報告したが、後続の公開元照合・呼出し検査で訂正した。実際の画面起動の成否は実行テスト結果で別途記録する。

MOD本体の公開ソースURLは公式Modrinthの`source_url`がnullであり、確定していない。関連する他人の追加MODリポジトリを、原MODの公開元として記載しない。公開MOD説明ではチェーンリフト・車両連結・ループ等を既存機能、Hydraulic launchesとCoaster stationsを予定機能としているため、駅・発進装置の存在を推測して扱わない。
## 保存・レール・車両の調査範囲

Simulated CoastersのJAR内に、アンカーポイント、ベジェ曲線編集、コースターカート、車両連結、チェーンリフト、クライアントの編集ハンドル、Sableサブレベルへの車両配置の実装クラスが実在する。これらの名前・表示文字列の存在だけでは、機能が実際に成功したとは判断しない。

詳細なレールNBT・車両状態・保存方式・API・駅停止方式は、対応する内部API調査と小規模実行テストの記録で補完する。保存成功・走行成功の判定には実ワールドからの再読込を必要とし、この文書の静的メタデータ確認を代用しない。

## 配布方針と公開元

完成ワールドZIPと独立した補助スクリプト/補助MODを公開し、添付された第三者MODのバイナリはZIPへ同梱しない。手元にある添付ファイルを利用し、必要な場合は正規配布元から取得する。

- **Sable:** `LICENSE.md`はPolyForm Shield License 1.0.0。配布にはライセンスまたはURLと必要な通知の提供を要し、競合用途を除外する条項がある。今回のワールド制作はSableを既存の物理ライブラリとして利用し、Sableの代替物理MODを制作しない。
- **Create:** 添付JARの`LICENSE.md_create-1.21.1`ではコードはMIT、`src/main/resources/assets/`はAll Rights Reserved。コードのMITだけから、アセットを含む元JAR全体の自由な再配布権を推定しない。
- **Simulated Coasters:** JARメタデータと公式ModrinthページがともにMITであり、添付ファイルは公式公開版と完全一致した。元JARをそのまま利用し、配布物では公式取得URLを案内する。

確認できた公開情報:

- [Sableソース](https://github.com/ryanhcode/sable)
- [Sableライセンス](https://raw.githubusercontent.com/ryanhcode/sable/main/LICENSE.md)
- [Sable README](https://raw.githubusercontent.com/ryanhcode/sable/main/README.md)
- [Sable正規配布ページ](https://modrinth.com/mod/sable) / [CurseForge](https://www.curseforge.com/minecraft/mc-mods/sable)
- [Createソース](https://github.com/Creators-of-Create/Create)
- [添付CreateのManifestに記録されたコミットのライセンス](https://raw.githubusercontent.com/Creators-of-Create/Create/ac0c444d9828da3453ae8cc65338e8de063286fb/LICENSE.md)
- [Create正規配布ページ](https://www.curseforge.com/minecraft/mc-mods/create)
- [Create: Coasters Simulated正規配布ページ](https://modrinth.com/mod/create-coasters-simulated)
- [Create: Coasters SimulatedプロジェクトAPI](https://api.modrinth.com/v2/project/create-coasters-simulated)
- [同0.1.5公開バージョンAPI](https://api.modrinth.com/v2/version/PGhQoSBq)

Sableソース・README・ライセンスとCreateの該当コミットライセンスはクラウドからHTTP200で取得した。初期調査時に403だった通信設定は後に復旧し、Modrinth API、GitHub API、Modrinthの公開JARをHTTP200で取得した。公開バージョンと添付ファイルの照合証拠を`evidence/mod-audit/`へ保存した。静的メタデータ確認と実走行確認は区別して記録する。

## 再現手順とチェックサム

`scripts/audit-mod-jars.py`に3つのJARを引数として指定すると、標準ライブラリだけで`data/mod-manifest.json`を再作成できる。ZIP CRCを3本と内部ライブラリすべてで検査済み。

| ファイル | SHA-256 |
|---|---|
| Sable 2.0.6 | `9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2` |
| Simulated Coasters 0.1.5 | `f80b4c3cdf9fa6188804967147761b0373e150a6eadbdc53626e5ac9700b2049` |
| Create 6.0.10 | `ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a` |

3本とも公式Modrinthのversion-file APIでSHA-1から公開バージョンを特定し、公開メタデータのSHA-1・SHA-512を添付ファイルと照合した。

| MOD | 公開バージョンID | 公式ファイルURL |
|---|---|---|
| Sable 2.0.6 | `fg9dTRz9` | [同じSable JAR](https://cdn.modrinth.com/data/T9PomCSv/versions/fg9dTRz9/sable-neoforge-1.21.1-2.0.6.jar) |
| Create 6.0.10 | `UjX6dr61` | [同じCreate JAR](https://cdn.modrinth.com/data/LNytGWDc/versions/UjX6dr61/create-1.21.1-6.0.10.jar) |
| Simulated Coasters 0.1.5 | `PGhQoSBq` | [同じSimulated Coasters JAR](https://cdn.modrinth.com/data/W1ZUfWdr/versions/PGhQoSBq/simulatedcoasters-0.1.5.jar) |

SHA-256は添付ファイルの識別と未改変確認に使用し、公開API側のSHA-1・SHA-512と公開ファイルの実バイト照合を出所確認の追加証拠とする。
