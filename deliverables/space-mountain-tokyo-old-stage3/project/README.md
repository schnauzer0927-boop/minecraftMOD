# SpaceMountainTokyoOld — Stage 3

既存外観を維持し、入口内部、待機列、乗り場、帰還通路を追加しました。白・灰・青の清潔な宇宙旅行施設として、導入→折返し整列→星図ギャラリー→明るい乗り場の流れを設計しています。

**レール・車両は生成しません。** Create: Coasters Simulatedで後から手動設置する建築データです。実物の内部写真は未確認なので、内部意匠・配置は依頼に基づく推定・創作補完です。他パーク版の内装を採用していません。

## Windowsから導入

`SpaceMountainTokyoOld-Stage3.zip`を保存して「すべて展開」。同梱schematicを使うだけならPythonは不要です。

1. ワールドをバックアップします。
2. `output/stage3/schem`のファイルを、使用中のMinecraftインスタンスの`config/worldedit/schematics`へコピーします。
3. **Stage2と同じ貼付原点・回転・向き**に戻って内装を追加します。

```text
//schem load sponge stage3_interior_combined
//paste -a
```

`-a`は空気を省く指定です。Stage3領域に既に別の内装・レールを作っている場合は上書きされる可能性があるため、先に保護を確認してください。JSONはWorldEditの自動保護設定ではありません。

原点が不明なら、Stage2通常版の中央の最も高い突起ブロックをF3で確認します。その実座標から設計座標`(110,78,94)`を引くと貼付原点が分かります。回転・改変済みや半縮尺試作には使いません。

**空いた場所へ初めて建築する場合のみ**、外観込みの全体版を使用します。原点`0 64 0`の例:

```text
/tp @s 0 64 0
//schem load sponge tokyo_old_with_stage3
//paste -a
```

この原点で入口前は`/tp @s 107 65 178`、左ホームは`/tp @s 104 69 136`。青い床案内をたどって駅へ進めます。Stage2で外観本体だけを貼った場合は、旧`entrance_stage2`と`forecourt_stage2`も同じ原点へ追加してください。

旧`guide_optional`を貼っている場合は、手動レール敷設前にマゼンタガラスを外します。Stage3が旧ガイドを自動除去することはありません。

## 分割で貼り付ける場合

内装全体ファイルの代わりに、同じ原点で次の4個を順に`//paste -a`しても同じ内装になります。

| 順 | ファイル | 内容 |
|---:|---|---|
| 1 | `stage3_entrance_interior.schem` | 階段、導入ロビー、白いフレーム、航路案内 |
| 2 | `stage3_queue.schem` | 曲がる待機列、折返し、星図、発車前の導入 |
| 3 | `stage3_station.schem` | 左右ホーム、管制室、発車フレーム、照明、乗降開口 |
| 4 | `stage3_return_path.schem` | 右ホームからの別帰還通路と下り階段 |

`stage3_station_platforms.schem`はホーム部品の編集用、`stage3_guide_optional.schem`は取り外し可能なガラス点線。全体内装に重ねる必要はありません。

## レール後付けと編集

ホーム床Y4、人物の足元Y5、レール中心候補Y6、天井Y20。中央X106～114 / Z120～143 / Y5～15を幅9・高さ11の空間として予約しました。左ホームが乗車、右ホームが降車候補、発車方向は北(-Z)。車両の実サイズ・座席高に合わせた調整と乗車検証は必要です。

最新の計画は`output/stage3/json/updated_route_guide.json`と`updated_protected_areas.json`。Stage2の中央ライド空間とユーザー保護は保持し、旧暫定駅とガイドだけを具体化しました。実施設のコース図や走行可能性を保証するガイドではありません。

再生成時の追加保護は`user_keepout.json`へプロジェクト相対座標で記入します。WorldEditがそのJSONを自動適用することはありません。共通原点と`//paste -a`を守り、貼付先の追加建築を確認してください。

## Pythonで再生成

Python3.11以上とWindowsの`py`ランチャーを用意し、`generate_stage3_windows.bat`を実行。手動実行は:

```powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe generator\stage3_main.py
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

床・天井・余白は`stage3_config.json`、生成はエリア別モジュール。床Y5への変更も検査しました。詳しくは`docs/stage3_notes.md`。Windows上でのBAT実行は未検証です。Stage2の14個のschematicと生成コードは保持、旧説明書は`docs/stage1_2_readme.md`です。

## 検証と資料

Python44テスト、正式WorldEdit Readerによる8ファイルの読込が成功。さらにMinecraft1.21.1 / NeoForge21.1.228 / 正式WorldEdit7.3.8の実サーバーで、119,124ブロックの貼付一致、保護空間、216地点の人物サイズ当たり判定と床支持を検査しました。

**未検証:** 人が操作する歩行、レール敷設・走行・駅停止、Windows、全指定MOD構成、実物の内部との一致。指定YouTube動画は今回も未視聴です。OLC公式本文で終了日を確認しましたが、内装形状の資料ではありません。

`output/stage3/preview`の5画像は実ブロックからの断面・等角・人物目線の描画であり、Minecraftのスクリーンショットではありません。

[設計](docs/stage3_notes.md) / [変更点](docs/stage3_changes.md) / [検査結果](docs/stage3_validation.md) / [資料区分](docs/stage3_sources.md) / [WorldEdit対応版](docs/environment_compatibility.md)。外部MOD JAR・参考写真・検査ワールドは同梱しません。検査専用MODをユーザーが導入する必要はありません。
