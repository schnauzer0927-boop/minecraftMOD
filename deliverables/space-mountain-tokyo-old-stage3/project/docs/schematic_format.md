# `.schem` 形式・共通原点・検証範囲

この試作は **Sponge Schematic v2 / Minecraft Java 1.21.1 / DataVersion 3955** を出力する。
エクスポータ・独立読込検証には Python 3.12 の標準ライブラリだけを使う。
コース・レールは作らず、writer と reader の両方で rail / powered_rail / detector_rail / activator_rail の Palette 登録を拒否する。

## ファイル構造

gzip 内の名前付き NBT compound `Schematic` に以下を保存する。

| NBT 項目 | 型・値 |
| --- | --- |
| Version | TAG_Int, 2 |
| DataVersion | TAG_Int, 3955 |
| Width / Height / Length | TAG_Short, 各 1〜32767 |
| Offset | TAG_Int_Array, 3要素 |
| Metadata | TAG_Compound, WEOffsetX/Y/Z を含む |
| Palette | TAG_Compound, state string → TAG_Int ID |
| PaletteMax | TAG_Int, Palette の項目数 |
| BlockData | TAG_Byte_Array, 非負 VarInt の連続列 |
| BlockEntities / Entities | 空の TAG_List<TAG_Compound> |

`minecraft:air` の Palette ID は常に 0。他の state は辞書順で ID を付ける。
各セルの順序は **x が最速、次に z、最後に y**。インデックスは
`x + z * Width + y * Width * Length`。
Palette ID 128 以上も複数バイトの VarInt で保存する。
NBT の文字列は Java modified UTF-8 とし、補助平面文字と NUL も処理する。
gzip の時刻・ファイル名を固定し、同じ形状・metadata の出力はバイト単位で再現する。

入力は非airブロックの疎な座標辞書であり、出力寸法は非airだけの最小境界箱にする。
箱の内側でブロックを指定していないセルは air。
寸法の正数範囲・Offset の signed int 範囲・空形状・レール・不正座標・不正 state string を出力前に検査する。
state の正しい Minecraft 登録名・プロパティについては別途公式 WorldEdit reader 検証が扱う。

## 分割ファイルも同じ場所へ貼り付く理由

`export_schem(path, blocks, origin=(0,0,0), metadata=None)` の `origin` は**プロジェクト全体の共通貼付基準**。
ファイルごとの最小座標ではない。
全ファイルで同じ `origin` を渡す。

1. `m` = 非airブロックの最小座標、`o` = 共通入力原点。
2. NBT `Offset` = `m - o`。
3. `Metadata.WEOffsetX/Y/Z` も `m - o`。
4. WorldEdit 7.3.8 は `clipboard origin = Offset - WEOffset` と読むため、全ファイルの clipboard origin は `(0,0,0)`。

貼付先を `P` とすると、入力ブロック座標 `q` は `P + (q - o)` に置かれる。
本体・屋根・入口などの境界箱が違っても、この式は共通なので位置が一致する。
例：本体の `Offset=(-5,4,2)` と屋根の `Offset=(-1,12,5)` は両方とも clipboard origin 0。
どちらも同じプレイヤー位置から `//paste -a` を実行する。
分割を貼る間は立ち位置・向き・回転・反転を変えない。

`//paste -a` は air を除いて貼り付けるため、境界箱内の未指定セルで既存建物や他の分割パーツを消さずに組み立てられる。
通常の `//paste` では未指定セルも air として貼付先を上書きする。
`//paste -o` は clipboard の元位置を使う別の操作であり、この共通貼付基準を使う手順では指定しない。

## 検証と限界

独立 Python reader は exporter を import せず、保存済み gzip / NBT を読み直して検査する。
寸法、PaletteMax、一意で連続した ID、air 0、VarInt の数・範囲・欠落、未知 Palette 参照、YZX 座標、共通 clipboard origin、最小境界箱、空 entity とレール不在を確認する。

```bash
cd /workspace/SpaceMountainTokyoOld
python3 -m unittest discover -s tests -p test_schem.py -v
python3 -m generator.validate_schem output/schem/*.schem
```

最終14ファイルの独立形式検証結果は
[`output/reports/schematic_format_validation.json`](../output/reports/schematic_format_validation.json) に保存した。

形式テストは、Palette ID 128 以上、空気の穴、異なる境界箱の分割貼付、非0入力原点、破損 gzip / NBT / VarInt / Palette、原点のずれを含む。
Python round-trip だけを WorldEdit 実読込とは呼ばない。

追加検証用の `tools/check_worldedit_738.py` は、公式 WorldEdit 7.3.8 Bukkit artifact を取得して hash を照合し、変更していない公式 `ClipboardFormats.findByFile(...).getReader(...).read()` を実行する。
依存 jar / compiled classes は OS の一時ディレクトリ内 `space-schem-we-java` に保存し、モデルの ZIP には含めない。
この Linux 環境では `/tmp/space-schem-we-java`。
取得には HTTPS ネットワーク、実行には compiler module を持つ Java 21 が必要。
classpath の区切りと一時ディレクトリは OS に従うが、Windows での helper 実行は未検証。

```bash
python3 tools/check_worldedit_738.py output/schem/*.schem
```

今回の14ファイルはこの公式 reader で実際に読み込んだ。
結果・各ファイル SHA-256・取得 artifact の URL/hash は
[`output/reports/worldedit_reader.json`](../output/reports/worldedit_reader.json) に保存した。
独立 Python reader と、寸法・最小/最大点・origin・体積・非air数・各 canonical state の個数・全非air座標と state の hash を比較し、全件一致した。
Palette を公式 BlockFactory でも厳密に解析するので、未知 state が air に置き換えられる fallback を合格として扱わない。
別途4つの `exterior_*` を独立読込後に結合し、本体 `tokyo_old_exterior.schem` と座標/stateが完全一致し、分割間の重複がないことを確認した。

公式 `BundledBlockRegistry` はプロパティ定義を提供しないため、検証 harness の standalone platform は Minecraft 1.21.1 の公開 mcmeta から block ID / property / default を登録する。
実際の NBT・Palette・VarInt reader は公式 WorldEdit のまま。
この検証は **WorldEdit 7.3.8 のファイル認識・読込・座標と原点**を確認する。
**Minecraft/NeoForge 内での `//paste`、実際の設置結果、描画・物理・mod registry はゲーム実機未検証**。

## 公開出典

- [Sponge Schematic Specification v2](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md) — NBT root、寸法、Offset、Palette、VarInt、entity 仕様。
- [WorldEdit 7.3.8 BuiltInClipboardFormat](https://github.com/EngineHub/WorldEdit/blob/7.3.8/worldedit-core/src/main/java/com/sk89q/worldedit/extent/clipboard/io/BuiltInClipboardFormat.java) — gzip / NBT v2 読込と形式検出。
- [WorldEdit 7.3.8 SpongeSchematicV2Reader](https://github.com/EngineHub/WorldEdit/blob/7.3.8/worldedit-core/src/main/java/com/sk89q/worldedit/extent/clipboard/io/sponge/SpongeSchematicV2Reader.java) — v2 DataVersion と v1 共通ブロック読込。
- [WorldEdit 7.3.8 SpongeSchematicV1Reader](https://github.com/EngineHub/WorldEdit/blob/7.3.8/worldedit-core/src/main/java/com/sk89q/worldedit/extent/clipboard/io/sponge/SpongeSchematicV1Reader.java#L91) — root Offset = clipboard minimum、origin = minimum − Metadata WEOffset。
- [WorldEdit 7.3.8 SpongeSchematicV2Writer](https://github.com/EngineHub/WorldEdit/blob/7.3.8/worldedit-core/src/main/java/com/sk89q/worldedit/extent/clipboard/io/sponge/SpongeSchematicV2Writer.java#L72) — `WEOffset=min-origin`、root Offset、YZX 順序。
- [WorldEdit 7.3.8 ReaderUtil](https://github.com/EngineHub/WorldEdit/blob/7.3.8/worldedit-core/src/main/java/com/sk89q/worldedit/extent/clipboard/io/sponge/ReaderUtil.java#L168) — VarInt の Palette 読込と minimum を加えた座標復元。
- [WorldEdit 7.3.8 PasteBuilder](https://github.com/EngineHub/WorldEdit/blob/7.3.8/worldedit-core/src/main/java/com/sk89q/worldedit/session/PasteBuilder.java#L153) — clipboard origin を基準とする ForwardExtentCopy と ignoreAirBlocks mask。
- [WorldEdit 7.3.8 公式配布 metadata](https://api.modrinth.com/v2/version/ecqqLKUO) / [jar](https://cdn.modrinth.com/data/1u6JkXh5/versions/ecqqLKUO/worldedit-bukkit-7.3.8.jar)。jar SHA-512:
  `ca8c25d1c2d894b7c3293617875abf23af1cb1c0a6a4e142ee514e7d06536a67d91664009ce8f54902c8f4fd05e32f8235f43cf52bfbd0a6c5622d6bef4f1c6e`
- [WorldEdit 7.3.8 dependency versions](https://github.com/EngineHub/WorldEdit/blob/7.3.8/gradle/libs.versions.toml) — Guava 32.1.3-jre、failureaccess 1.0.1、Gson 2.10.1、fastutil 8.5.12、Log4j 2.22.1、ANTLR runtime 4.13.1、SnakeYAML 2.0、Rhino runtime 1.7.13。
- [Minecraft 1.21.1 mcmeta block summary](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-summary/blocks/data.json) — standalone 検証用 vanilla property/default schema。

公開参照取得・読込試験日：2026-10-13。画像・実物の外観参照とは別の、ファイル形式検証資料。
