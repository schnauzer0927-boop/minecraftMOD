# Stage 2 外観schemとユーザー環境の互換確認

調査日: **2026-10-13 UTC**。対象Minecraftバージョンは **1.21.1 / NeoForge**。この文書は配布情報と今回実施した検査の区別を記録する。ユーザーの全MOD構成で実機起動・取り込みを確認したという意味ではない。

## WorldEdit 7.3.8のNeoForge対応配布は存在する

Modrinth公式プロジェクトの公開APIを実際にHTTP 200で読み取り、`game_versions=["1.21","1.21.1"]`、`loaders=["fabric","neoforge"]` を持つ7.3.8を確認した。Minecraft 1.21.1とNeoForgeを指定したAPI検索にも同じversion IDが返った。

| 項目 | NeoForge/Fabric実行用配布 | Bukkit系配布 |
|---|---|---|
| 表示名 | WorldEdit 7.3.8 (NeoForge/Fabric for MC 1.21-1.21.1) | WorldEdit 7.3.8 (Bukkit for 1.20.2-1.21.1) |
| project ID | `1u6JkXh5` | `1u6JkXh5` |
| version ID | `WTAFvuRx` | `ecqqLKUO` |
| version_number | `7.3.8` | `7.3.8` |
| game_versions | `1.21`, `1.21.1` | `1.20.2`, `1.20.3`, `1.20.4`, `1.20.5`, `1.20.6`, `1.21`, `1.21.1` |
| loaders | `fabric`, `neoforge` | `bukkit`, `paper`, `spigot` |
| 公開日時(UTC) | 2024-10-21T11:01:40.791602Z | 2024-10-21T11:02:30.754694Z |
| ファイル | `worldedit-mod-7.3.8.jar` | `worldedit-bukkit-7.3.8.jar` |
| API記載サイズ | 6,222,854 bytes | 5,926,715 bytes |
| 公開API | https://api.modrinth.com/v2/version/WTAFvuRx | https://api.modrinth.com/v2/version/ecqqLKUO |
| 公式配布URL | https://cdn.modrinth.com/data/1u6JkXh5/versions/WTAFvuRx/worldedit-mod-7.3.8.jar | https://cdn.modrinth.com/data/1u6JkXh5/versions/ecqqLKUO/worldedit-bukkit-7.3.8.jar |

プロジェクトページ: https://modrinth.com/plugin/worldedit 。公開APIのプロジェクト情報は `source_url=https://github.com/EngineHub/WorldEdit/` とGPL-3.0-onlyを記載する。NeoForge 1.21.1での実行に選ぶのは **worldedit-mod-7.3.8.jar**。Bukkit版はBukkit/Paper/Spigot向け配布であり、NeoForgeの実行用MODとして同一扱いしない。

両方の配布URLへHEADを実行し、HTTP 200 / `application/java-archive` とAPI記載と同じContent-Lengthを確認した。これは配布先への到達確認であり、ユーザー環境でのMOD起動テストではない。今回の調査では実行用NeoForge JARのダウンロード・インストール・起動を行っていない。

### API記載の配布ファイルハッシュ

- NeoForge/Fabric版SHA1: `924fe181a29bd66be6783f6d589968ab0ee02250`
- NeoForge/Fabric版SHA512: `e039492df0b486e7ce76d0eaf8cb11eadad2e78220600b8498ab8eef4642a29e310a6bcb4378257553b3639c6d0bc1ebf0f73df3a7a677c6af6baf86716b0bc7`
- Bukkit版SHA1: `75ac9e214a3dd8ebd53b7cb1e6f27d9a4d0479f0`
- Bukkit版SHA512: `ca8c25d1c2d894b7c3293617875abf23af1cb1c0a6a4e142ee514e7d06536a67d91664009ce8f54902c8f4fd05e32f8235f43cf52bfbd0a6c5622d6bef4f1c6e`

ここに記載した値はAPI提供値であり、この調査担当がNeoForge実行用JARをダウンロードして計算した値ではない。Bukkit解析担当の取得・ハッシュ照合・Reader結果は、その実際の検査結果に従う。

## standalone Reader検査と実機起動を区別する

今回の `WorldEditReader` 担当はBukkit 7.3.8 JARのライブラリをJava standalone解析に使う。これはWorldEditによってschemを読み取れるかを検査する用途であり、Bukkitサーバーを起動することやNeoForgeでのインポート成功を表さない。Bukkit配布のversion IDとNeoForge実行用配布のversion IDを混同しない。

今回正式Readerで14個のschemを読み込み、形式・サイズ・パレット・原点・非airブロックの座標と状態を確認した。詳細は`test_results.md`と`output/reports/worldedit_reader.json`に保存した。Minecraftクライアント起動、ユーザーMOD組み合わせ、シェーダー描画、実際の`//paste`は別の検証が必要であり、今回は未検証。

## Sodium / Iris / レール用MOD

| 対象 | 現時点の確認範囲 |
|---|---|
| Sodium `0.8.13` | ユーザー指定のバージョン名。今回JAR添付なし。実ファイル、対象Minecraft/loader、その指定バージョンでの起動は未確認。WorldEditとの互換を断定しない。 |
| Iris `1.8.14-beta.1` | ユーザー指定のバージョン名。今回JAR添付なし。実ファイル、対象Minecraft/loader、その指定バージョンでの起動とシェーダー描画は未確認。WorldEditとの互換を断定しない。 |
| Create / Sable等 | 今回の外観schemはバニラのブロックだけを使う設計。外観取り込み自体はCreate/Sableの独自ブロックを必要としない。 |
| 後のレール設置・乗車 | 今回範囲外。対象レールMODの実ファイル、Minecraft/loader、動作環境はその段階で確認する。外観schemの検査を走行互換の証拠にしない。 |

WorldEditの配布メタデータがNeoForge/1.21.1を含むことは確認済みだが、上記Sodium/Irisを含む全構成で起動できることは未検証である。ユーザーの既存環境を変更したり、未添付のJARを取得して勝手に組み合わせてはいない。

## 取得記録

実際のHTTP結果、URL、取得日時、API応答は次に保持する。配布JAR自体はこの文書や外観schem ZIPに同梱しない。

- `references/worldedit_modrinth_project.json`
- `references/worldedit_modrinth_neoforge_1_21_1.json`
- `references/worldedit_mod_version.json`
- `references/worldedit_bukkit_version.json`
- `references/worldedit_mod_download_head.json`
- `references/worldedit_bukkit_download_head.json`

全バージョン一覧の取得原文は `references/local_analysis/api_snapshots/worldedit_modrinth_all_versions.json` に保持し、配布用記録は対象バージョンに絞った上記メタデータを用いる。
