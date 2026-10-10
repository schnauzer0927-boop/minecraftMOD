# Apex Fang の Blockbench 編集

正式な編集元は [`models/blockbench/apex_fang.bbmodel`](../models/blockbench/apex_fang.bbmodel) です。Blockbench 5.x の **Java Block/Item** プロジェクトとして直接開けます。Minecraft の形式は「1.9–1.21.5」（`java_block_version: 1.9.0`）を選びます。これは Minecraft 1.20.1 用のキューブ・回転制限に対応する設定です。

初期モデルは 56 キューブ、18 グループ、128×128 の埋め込みテクスチャで構成しています。1.5.0 の大型ブレード、尖った主刃と補助刃、9 箇所の発光溝、8 種類の表示設定をそのまま移したため、編集前のゲーム内形状は変わりません。

主なグループは `root`、`handle`、`grip`、`pommel`、`guard`、`blade_base`、`blade_main`、`blade_upper`、`blade_tip`、`blade_lower`、`auxiliary_tip`、`layered_armour`、`energy_channel`、`core`、`accent_left`、`accent_right` です。装甲・刃・柄・細い発光パーツを分けて編集できます。

## 編集と書き出し

1. Blockbench で `models/blockbench/apex_fang.bbmodel` を開きます。
2. Edit モードでキューブの形状、位置、回転、pivot、面ごとの UV を変更します。Paint モードで埋め込みの `apex_fang.png` を塗り替えます。
3. Display モードで一人称、三人称の左右両手、GUI、地面、額縁、頭への装着を確認します。
4. **File → Save Project**（Ctrl+S）で `.bbmodel` を保存します。埋め込みテクスチャを有効にしてください。
5. リポジトリのルートで以下を実行します。

```bash
python3 scripts/blockbench/apex_fang.py export \
  --project models/blockbench/apex_fang.bbmodel \
  --output src/main/resources/assets/apexpredator/models/item/apex_fang.json \
  --textures-dir src/main/resources/assets/apexpredator/textures

python3 scripts/blockbench/apex_fang.py check
./gradlew build
```

この書き出しは、保存したプロジェクトの実際のキューブ、UV、表示設定、塗り替えた埋め込み PNG を読みます。既存のランタイム JSON をテンプレートとしてコピーする処理ではありません。`--textures-dir` を付けると、テクスチャ変更もゲームが読む `textures/item/apex_fang.png` に反映します。Python 3 の標準ライブラリだけで動作します。

Blockbench の通常保存で表示角度の +180° が −180° に変わる場合も、同じ向きとして書き出します。保存だけでモデルの向きが変わったり、一致確認が失敗したりしません。

同梱の [`models/blockbench/textures/apex_fang.png`](../models/blockbench/textures/apex_fang.png) は外部ペイント用の初期画像です。外部アプリで編集した場合は、Blockbench のテクスチャ一覧から画像を置き換え、プロジェクトを保存してから上記のコマンドで書き出してください。正式なプロジェクトは埋め込み画像を使うため、他の PC へ移しても絶対パスを要求しません。

## Forge の発光

`energy_channel`、`core`、`accent_left`、`accent_right` のグループ名は発光の宣言です。これらの配下にあるキューブを専用エクスポーターが `shade: false` と Forge 1.20.1 の `forge_data`（block/sky light 15、ambient occlusion 無効）として書き出します。空グループは許容しますが、この 4 つの名前は保持してください。誤って名前を変えた場合は明示的なエラーになります。新しい細い発光キューブもこれらの配下へ追加できます。

Blockbench の標準 Java JSON 書き出しだけでは Forge 固有の発光情報を維持できません。`.bbmodel` を保存した後に、上記の専用エクスポーターを使用してください。未知の独自プロパティを `.bbmodel` に足す方式を使わないため、通常の Blockbench 保存で発光の宣言が消えません。発光はアイテム面の明るさであり、周囲のブロックに光源を置く機能ではありません。

## Minecraft 1.20.1 の制限

- キューブ座標は −16〜32。キューブの回転は 1 軸のみ、0°・±22.5°・±45°です。
- 面ごとの UV と、Blockbench の 128 ピクセル基準の UV 座標を維持します。書き出し時に Minecraft の 16 単位基準へ変換します。
- 回転したグループ、複数軸回転、メッシュ、stretch、範囲外の座標を黙って補正しません。書き出しを止め、対象を示します。グループを回転する代わりに個々のキューブを回転してください。
- エネルギー溝は細いパーツとして装甲から分離しています。発光グループへ大きな装甲を移すと、その装甲全体が発光します。

編集後は一人称・三人称・GUI・地面の各表示と、発光パーツの重なりを実クライアントで確認してください。モデルの変更は武器のダメージ、クールダウン、特殊能力のロジックを変更しません。

編集元の一致確認と、形状・UV・pivot・表示・発光・PNG の編集反映テストは次のコマンドでも実行できます。

```bash
python3 -m unittest discover -s tests/blockbench -p 'test_apex_fang.py' -v
```
