# SpaceMountainTokyoOld — Stage 1・Stage 2 外観試作

東京ディズニーランドの**旧スペース・マウンテン**をMinecraftで再現するための、資料整理と外観生成プロジェクトです。白い切頂円錐・放射リブ・頂部の針・二層の帯・V字状の柱を、実際に閲覧した参考写真に基づいて試作しました。

**今回の成果物は建築schematicです。完成済みライド／`saves`用ワールドではありません。レール・車両・走行処理は一切生成しません。** 内装、待機列、駅の本制作はStage 3以降です。入口と外構は位置検討用の仮造形です。

資料写真の東京版同定は高確度の推定、寸法・裏側・本数は推定です。公式本文とユーザー指定YouTube動画はクラウドのCONNECT403で取得できず、未確認と記録しました。実物の図面を取得した精密復元ではありません。[資料の確信度と出典](docs/sources.md)を参照してください。

## ダウンロードと同梱物

`SpaceMountainTokyoOld-Stage1-2.zip`をWindowsへ保存して「すべて展開」してください。`SpaceMountainTokyoOld`フォルダに生成コード、14個の`.schem`、画像、JSON、資料メモ、テスト結果をまとめています。

| ファイル | 用途 |
|---|---|
| `output/schem/tokyo_old_stage2_site.schem` | 外観＋仮入口＋仮広場の全体試作。最初はこれ1個を使用。 |
| `output/schem/tokyo_old_exterior.schem` | 外観本体のみ。 |
| `output/schem/exterior_north_east.schem`など4個 | 外観を四分割。全体版の代わりに同じ原点へ順に貼付。 |
| `roof_stage2.schem` / `base_stage2.schem` / `crown_stage2.schem` / `foundation_stage2.schem` | 屋根・二層の裾・頂部・基礎の編集用部品。 |
| `entrance_stage2.schem` / `forecourt_stage2.schem` | 入口・外構の仮造形。実施設の動線再現ではない。 |
| `prototype_half_scale.schem` | 小規模な外観確認用。通常版の半分の寸法。別の場所へ貼付する。 |
| `guide_optional.schem` | 任意のマゼンタ色ガラス点線。実レールではない。全体版には含まれない。 |
| `output/preview/` | 生成ブロックから描画した等角・正面・上面・保護領域図。ゲームのスクリーンショットではない。 |
| `output/json/route_guide.json` / `protected_areas.json` | 仮中心線、用途、高度、確信度、建築予約領域。実物のコースではない。 |
| `generator/` / `tools/` / `tests/` | 独自Pythonコード、正式WorldEdit Reader検査、回帰テスト。 |

## WorldEditで建築を読み込む

対象はMinecraft Java **1.21.1 / NeoForge / WorldEdit 7.3.8**。MOD版`worldedit-mod-7.3.8.jar`には当該環境向けの正式配布があることを公開APIで確認しました。[環境情報と配布先](docs/environment_compatibility.md)を参照してください。この建築はバニラブロックのみを使用します。Create / Coasters Simulated / Sableは後から手動でレールを敷くときに使用します。依存MODはこのZIPに同梱していません。

1. 試作用の平坦なクリエイティブワールドを作り、コマンドを許可します。
2. 使用中のMinecraftインスタンスの`config/worldedit/schematics`へ、`output/schem`のファイルをコピーします。ランチャーでインスタンスが異なる場合は、そのインスタンスのゲームフォルダを使います。フォルダはWorldEdit起動後に作られます。
3. 敷地として220×220ブロック、貼付原点から79ブロックの高さを確保します。次は原点をワールド座標`0 64 0`とする例です。

```text
/tp @s 0 64 0
//schem load sponge tokyo_old_stage2_site
//paste -a
```

外観を見る位置の例:

```text
/tp @s 110 70 200
```

貼付位置は実際にはWorldEditの配置位置設定にも依存するため、最初は試作用ワールドで確認してください。Minecraft内でのこの貼付手順は今回実行していません。

分割版を使う場合は、**同じ貼付原点に留まったまま**4個を順に読み込み、毎回`//paste -a`します。外観全体版と四分割版を両方重ねる必要はありません。部品の境界には重複がなく、同一原点へ結合すると外観全体版に一致することをデータ上で検査済みです。

`-a`は空気を貼り付けない指定です。既存の地形を自動で切り抜く用途ではありません。**新規の空いた場所**へ貼付してください。外観の中を元の土や石が埋めている場合、この方法では空洞になりません。

## 原点と寸法

プロジェクト原点は敷地北西の`(0,0,0)`、東が`+X`、上が`+Y`、南が`+Z`。建物中心は`(110,0,94)`です。全パーツはSponge OffsetとWorldEditのWEOffsetを揃え、同じclipboard origin `(0,0,0)`を使用します。

| 項目 | 設計値 |
|---|---:|
| 敷地検討枠 | 220 × 220 blocks |
| 全体schematicの実際の外接サイズ | 152 × 79 × 187 (X × Y × Z) |
| 全体試作の配置ブロック | 108,014 |
| 屋根主要プロファイル直径 / 頂部直径 (設計パラメーター) | 126 / 28 blocks |
| 屋根裾 / 頂部 / 最高部の高さ | Y=21 / 58 / 78 |
| 表現上の縮尺 | 1 block = 0.5 m (推定設計縮尺) |

リブや軒の厚みを含む実ブロックの外接寸法は、上表の主要プロファイルより大きくなります。各ファイルの外接寸法は`output/reports/generation_report.json`に記録しています。

縮尺とすべての実寸換算は設計推定です。61m径・35m高といった実施設候補値は未確認なので確定値として使っていません。

## レール設置と保護

内部にレールはありません。中央空間、南側の駅候補、仮ガイド周辺に建築を置かない領域を設けました。ガイド周辺の初期予約は両端含め**横9×縦9ブロック**。速度・バンク・車両に必要な追加空間は、実際に使用するMODで後から走行検証してください。JSONの仮中心線は東京旧版の実コース再現ではなく、**内部空間を活用する創作案**です。曲線も物理も未検証です。

ガイドを表示する場合、同じ原点で`guide_optional`を`//paste -a`します。任意のマゼンタガラス点線で、外観に含まれません。ガイドに沿ってレールを敷く前に点線を除去します。ガイドだけを外したい場合は、貼付直後・他のWorldEdit編集をする前に`//undo`すると元の変更をまとめて取り消せます。レール敷設後に過去の`//undo`を戻す操作は避けてください。

`protected_areas.json`は**Python生成の予約領域**です。WorldEditに自動的な保護権限を設定するファイルではありません。再生成して貼り付ける際も同じ原点を使用し、`//paste -a`で空気を省きます。レールが生成時の予約領域内にあることが必要です。

手動レールなどを追加で保護したいときは、再生成前に`user_keepout.json`へプロジェクト相対座標の整数直方体を記入します。

```json
{"coordinate_space":"project_relative_xyz","areas":[
  {"id":"my_rails","min":[90,8,80],"max":[130,35,120]}
]}
```

ワールド原点が`0 64 0`なら、ワールドYから64を引いて記入します。生成中の全ブロック配置でこの保護を守ります。ただし、既に貼付された古いブロックを自動除去する処理はありません。再生成や貼付前にワールドをバックアップしてください。

## Windowsで再生成

既に同梱したschematicを使うだけならPythonは不要です。コードを再生成する場合は、Python **3.11以上**（Windowsの`py`ランチャーを含む）を用意し、`generate_windows.bat`を実行します。最初だけNumPy / Pillowを入れ、`output`へ生成します。

PowerShellからの手動実行:

```powershell
cd .\SpaceMountainTokyoOld
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe generator\main.py
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

`config.json`で屋根半径、頂部半径、傾斜、リブ本数、針の高さ、クリアランスを変更できます。二層の下部と入口の細部の編集は`generator/exterior.py`と`entrance.py`を使います。寸法を変えた後はレール予定空間・接続・プレビューを再確認してください。Windows実行は未検証で、クラウドLinux/Python 3.12で生成と検査を実行しました。

画像生成を省く場合は`--no-previews`を指定します。この場合の生成・NBT検査はPython標準ライブラリだけで動きます。

## 確認したことと次の段階

24件のPythonテスト、14個の独立NBT検査、正式WorldEdit 7.3.8 Readerによる14個の読込比較を実行しました。データ形式、パレット、原点、全非空気ブロックの座標と状態、レール不在、予約領域、屋根の連続性、入口の空間、分割結合を確認しました。[テスト結果](docs/test_results.md)を参照してください。

**未検証**: ゲーム内貼付、ユーザー指定MOD一式での起動、Create系レールの建設・走行、東京実物との実寸一致、内装再現、指定動画の内容。Stage 3～7は未実装です。完成済みワールドとしては扱いません。

次は複数方向の東京版写真と指定動画を確認して形状を補正し、入口・待機列・駅の実資料調査へ進む構成です。参考写真や第三者MOD本体はZIPに同梱せず、URLと取得・検査メタデータを保持します。
