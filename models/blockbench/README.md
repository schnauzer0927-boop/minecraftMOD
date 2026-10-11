# Apex Predator 1.15.2 — Blockbenchでモデルを編集する

正式な編集元は [Apex本体](apex.bbmodel) と [Apex Fang](apex_fang.bbmodel) です。両ファイルにPNGを埋め込んでいるため、別のPCで直接開けます。MODは従来のGeckoLib geometry・アニメーションJSONとMinecraft Java item JSONを読みます。Blockbench自体やPythonはゲームの実行に必要ありません。

本体にはBlockbench 5.xと公式 **GeckoLib Models & Animations / GeckoLib Animation Utils** プラグインが必要です。Fangは標準の **Java Block/Item** 形式で開けます。1.15.2ではHUNTER設計図をもとにApex本体の形状・UV・テクスチャを全面再構築しました。既存28ボーンの名前・親子関係、37アニメーションの名前・攻撃時刻を維持しています。

| 編集元 | 形式 | 構成 | テクスチャ |
| --- | --- | --- | --- |
| `apex.bbmodel` | GeckoLib Animated Model / Entity | 28ボーン、498キューブ、37クリップ | `apex.png` / `apex_glow.png` / `apex_healing_glow.png`、1024×1024 |
| `apex_fang.bbmodel` | Java Block/Item | 56キューブ、18グループ、8表示設定 | `apex_fang.png`、128×128 |

以下のコマンドはリポジトリのルートで実行します。`.bbmodel`を編集・保存し、専用の書き出しツールでMODのresourceへ反映してから`./gradlew build`を実行します。旧Python生成スクリプトによる上書きを防ぐ仕組みも用意しています。

## Apex本体を編集する

正式な編集元は `models/blockbench/apex.bbmodel` です。Blockbench 5以降に公式の **GeckoLib Models & Animations** プラグインを入れ、**GeckoLib Animated Model / Entity** として開いてください。自由形式へ変換する必要はありません。個別に編集できる498キューブ、既存の28ボーン、37アニメーションの5,316キーフレームを保存しています。ボーン名・親子関係とタイムラインの時刻・easingを維持しています。新しい形状に合わせたpivot、各面のUV・回転は、対応する実行用geometryと照合します。

`apex.png`・`apex_glow.png`・`apex_healing_glow.png`は、1024×1024のPNGをプロジェクト内に埋め込んであります。テクスチャ一覧で `apex.png` を選ぶと通常の装甲、`apex_glow.png` を選ぶと発光マスクを確認・Paint編集できます。回復用の`apex_healing_glow.png`も一覧からPaint編集できます。`--textures-dir`指定の専用書き出しは、埋め込まれた3枚すべてを出力します。Blockbenchの通常プレビューではGeckoLibのゲーム内発光レイヤーやフェーズ3回復用の追加発光を合成しません。Glow側が黒く/透明に見える装甲部分は正常です。発光マスクを選んだだけで材質やUVを別形式へ変換しないでください。

1. `apex.bbmodel` を開き、Editでボーンを展開してキューブやpivotを編集します。例えば `head` / `face_plate`、`left_forearm` / `left_blade`、`back_spines` / `back_blade_left` / `back_blade_right` を個別に選べます。
2. UVやPaintを変更する場合は、該当する通常/発光PNGを選びます。通常・発光・回復の3枚で同じ1024×1024アトラス上の対応を維持してください。
3. `.bbmodel` を保存します。**Embed Textures** を有効にして埋め込みPNGを残してください。テクスチャはこの正式元に保存し、MOD側へ書き出すまでランタイムPNGを別編集しません。
4. リポジトリのルートで次のコマンドを実行し、まず `build/blockbench/` へ確認用geometryとPNGを書き出します。Python 3標準ライブラリだけで実行できます。

   ```bash
   python3 scripts/blockbench/apex_body.py export \
     --project models/blockbench/apex.bbmodel \
     --output build/blockbench/apex.geo.json \
     --textures-dir build/blockbench/textures
   ```

5. 差分を確認後、同じ編集元からMODのリソースへ反映します。形状だけを変更した場合は `--textures-dir` を省略できます。

   ```bash
   python3 scripts/blockbench/apex_body.py export \
     --project models/blockbench/apex.bbmodel \
     --output src/main/resources/assets/apexpredator/geo/apex.geo.json \
     --textures-dir src/main/resources/assets/apexpredator/textures
   python3 scripts/blockbench/apex_body.py check
   ./gradlew build
   ```

6. Minecraftでidle、walk/run、blade_combo、charge、slam、dodge、teleport、phase_change、rage、death、special_attackなどを確認します。pivotや親子関係の変更はモーションにも影響するため、まず形状だけを調整することを勧めます。ボーン名の変更・削除はCombatControllerと37アニメーションの互換性を壊します。

書き出しスクリプトはBlockbench/Bedrockの座標変換を行い、X座標、回転のX/Y、up/down面のUV方向を反映します。**書き出し時に既存Python生成スクリプトや元のgeometryを参照して再生成しません。** `.bbmodel` 内で編集した形状・pivot・UV・埋め込みPNGがそのまま出力元になります。`check` は全geometryに加えて37アニメーションの名前、長さ、ループ、全キーフレームの時刻・値・easing、PNGの一致を確認します。合法なモデル編集を反映する前の `check` が差分を報告するのは正常です。確認用geometryを指定して編集後の出力を比較することもできます。

```bash
python3 scripts/blockbench/apex_body.py check \
  --geometry build/blockbench/apex.geo.json \
  --base-texture build/blockbench/textures/entity/apex.png \
  --glow-texture build/blockbench/textures/entity/apex_glow.png \
  --healing-texture build/blockbench/textures/entity/apex_healing_glow.png
```

Geometryの書き出しでは `apex.animation.json` を一切更新しません。今回の37クリップは名前・全時刻・再生長・ループ・easingを維持しています。`charge_prepare` / `charge` の `chest` / `neck` のX回転だけ、計16値を低い突進姿勢に合わせて補正しました。残り35クリップと、対象2クリップの他のボーン・position・scale・Y/Z回転は変更していません。Blockbench標準のアニメーション書き出しはタイムラインのグリッドへ時刻を丸めます。例えばchargeにある `0.0563` 秒は、Blockbenchが許可する最大500のsnappingでも正確に表せません。そのため、標準メニューで `apex.animation.json` を上書きしないでください。プロジェクト内の元のキーフレーム時刻は保持しており、37クリップすべての全5,316キーフレームを検証しています。

将来、pivot変更に伴うhead/neckなどの最小限の補正が必要になった場合は、BlockbenchのAnimateタブで値だけを補正して `.bbmodel` を保存し、次の明示的なコマンドで別の確認用ファイルへ出力します。このコマンドはグリッドへ丸めず、`.bbmodel` の生のキーフレーム時刻・値・easing・名前・長さを出力します。

```bash
python3 scripts/blockbench/apex_body.py export-animations \
  --project models/blockbench/apex.bbmodel \
  --output build/blockbench/apex.animation.json
```

この専用コマンドもMODの既存 `src/main/resources/assets/apexpredator/animations/apex.animation.json` への直接出力は拒否します。必ず確認用ファイルの差分を調べ、クリップ名・攻撃の時刻・全キーフレーム時刻を維持した補正だけを、意図的にランタイムへ反映してください。通常のモデル/PNG編集にはこのコマンドを使う必要はありません。

本体のgeometryもCLIで書き出すことを推奨します。Blockbench標準のGeckoLib geometry書き出しは、表示範囲を自動拡張し、ボーン順やゼロ回転の省略表現を変更します。本CLIは保存されたgeometryの表示範囲・ボーン順・UVを保ち、編集したキューブとpivotを反映します。

テクスチャを外部の絶対パスや相対パスにリンクせず埋め込んでいるため、`.bbmodel` 単体を別のPCへコピーして開けます。Blockbench Desktopは外部ファイルがあると埋め込みPNGより優先して読み込むため、古い外部PNGにPaint結果を置き換えられることも避けられます。

旧 `scripts/art/generate_apex_model.py` は履歴用に残しています。正式 `.bbmodel` が存在する場合の既定実行は、リソースを上書きする前に停止します。過去の初期モデルを意図的に再生成する場合だけ `--legacy-overwrite-model` が必要です。通常の編集・ビルドには実行しません。`import-legacy` も一度限りの移行用で、既存の正式 `.bbmodel` を上書きしません。

Pythonの検証は次のコマンドで実行できます。

```bash
python3 -m unittest discover -s scripts/blockbench -p 'test_apex_*.py' -v
```

## Apex Fangを編集する

[Fangの編集・UV・Paint・左右の表示設定・Forge発光の手順](../../docs/BLOCKBENCH-FANG.md)を参照してください。`energy_channel` / `core` / `accent_left` / `accent_right` を保持すると、通常のBlockbench保存後も9箇所の発光設定を再出力できます。

```bash
python3 scripts/blockbench/apex_fang.py export \
  --project models/blockbench/apex_fang.bbmodel \
  --output src/main/resources/assets/apexpredator/models/item/apex_fang.json \
  --textures-dir src/main/resources/assets/apexpredator/textures
python3 scripts/blockbench/apex_fang.py check
./gradlew build
```

書き出し前に試す場合は、出力先を`build/blockbench/apex_fang.json`と`build/blockbench/textures`へ変更してください。Fangの専用書き出しツールは、Minecraft 1.20.1で表現できない回転やメッシュを明示的に拒否します。

## 配布と検証

編集ファイルはリポジトリの上記リンクから **Download raw file** で保存できます。1.15.2 Releaseの編集用ZIPにも、両プロジェクト・書き出しスクリプト・手順を収録します。Minecraftへ入れるのはRelease AssetsのMOD jarです。[検証記録](../../VALIDATION.md)と[配布案内](../../dist/README.md)を参照してください。
