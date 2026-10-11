# Apex Predator 1.15.2 — リグ・編集元・モデル検証

この検証はApex本体の編集元、実行用モデル、テクスチャ、既存モーションの整合性を対象にします。戦闘AI、攻撃発生tick、Phase、自己回復、BGM、TaCZ連携、Beacon、Fangの実装を変更する検証ではありません。

## 編集元と実行用アセット

正式な編集元は [`models/blockbench/apex.bbmodel`](../models/blockbench/apex.bbmodel) です。GeckoLib Entity形式の28ボーン、全キューブの意味の分かる名前、個別pivot、6面のUV、37クリップを保持します。

通常装甲 `apex.png`、発光 `apex_glow.png`、回復時の発光 `apex_healing_glow.png` をPNGとしてプロジェクトへ埋め込みます。回復時マスクも新しいUV配置と同じ解像度へ更新し、既存の回復描画処理が読み込むファイル名を維持します。

```bash
python scripts/blockbench/apex_body.py export \
  --output src/main/resources/assets/apexpredator/geo/apex.geo.json \
  --textures-dir src/main/resources/assets/apexpredator/textures
python scripts/blockbench/apex_body.py check
```

exportは保存されたキューブ、outlinerの親子関係、pivot、UV、埋め込みPNGから出力します。旧モデルをひな形として読み戻しません。既存の2テクスチャ版 `.bbmodel` も引き続き変換できます。第3の回復マスクはプロジェクト内に存在するときだけ自動出力します。個人的な参考画像など、未知のテクスチャを実行用アセットへ自動出力しません。

## アニメーションを維持する検証

実行用 `animations/apex.animation.json` のSHA-256を、実際のBlockbench表示で確認した1.15.2の承認済みファイルと照合します。

```text
c12bf23e69775ab74fa4af038fe0611391a312743bdc3c66bb9b07c771b54a16
```

37クリップ、5,316キーフレームについて、名前、再生長、loop、対象ボーン、channel、全時刻、全数値、GeckoLib easingを編集元と実行用ファイルで比較します。`charge` の `0.0563` 秒を含む時刻も丸めません。全37クリップの時刻・loop・easingと、35クリップの全数値を1.5.1の基準から保持しています。

原図の低い突進姿勢を実際のBlockbench表示と合わせて確認し、`charge` と `charge_prepare` の胸・首のX回転だけを補正しました。変更は次の16数値で、Y/Z回転、position、scale、他の骨、予備動作の開始値、攻撃発生tickは変更していません。

| クリップ・ボーン | 補正箇所 | 旧角度 → 新角度 |
| --- | --- | --- |
| `charge` / chest | 既存9時刻 | 38° → 53° |
| `charge` / neck | 既存の定数回転1か所 | −22° → −30° |
| `charge_prepare` / chest | 0.4 / 0.58 / 0.7秒 | 28° → 40° |
| `charge_prepare` / neck | 0.4 / 0.58 / 0.7秒 | −15° → −20° |

移植可能な検証として、1.5.1から凍結した3種類の署名をコード内に保持します。全時刻・easingの署名は `3d8af3a277a99812669185753150db7fa0a9f2b0c31c0018b5f4aa88fe966525`、許可された4channelのX回転以外を保護する署名は `4e148195aaeb14f0b0d4ec25feb86781534e46206e9d12bc965bb25003656821`、残る35クリップの完全な署名は `11c2c5538788c31c8c4cce92041d6ece0b7d2964dda16ecfeddcc103dc0402d9` です。検証は作業環境だけの旧ファイルへ依存しません。

Blockbench標準のAnimation ExportはTimeline gridへ時刻を丸めるため、今回も実行用ファイルへの上書きに使いません。必要なレビュー用出力は独立した `export-animations --output <別ファイル>` で行い、実行用アニメーションと編集元への上書きを拒否します。

## 追加・更新した自動検証

[`ApexAssetIntegrityTest`](../src/test/java/dev/apexpredator/assets/ApexAssetIntegrityTest.java) は28ボーンの有限座標・acyclic hierarchy、300〜500キューブの密度、回転後の全キューブ頂点からの高さ、全UV、37クリップと承認済みSHA、通常・発光・回復マスクの解像度と透過画素の対応を検証します。暗い金属の主色と複数の明暗、発光部分が装甲の少量に留まることも確認します。

[`BlockbenchAssetTest`](../src/test/java/dev/apexpredator/assets/BlockbenchAssetTest.java) は、編集元の全キューブをoutliner順に実行用モデルと対応させ、位置、サイズ、独立回転、pivot、inflate、6面UVと面回転を比較します。ボーン名、親子、ボーンpivot、埋め込みPNGの全バイトも一致検証します。既存Fangの56キューブ、左右の表示設定、fullbright選択の検証は維持します。

[`test_apex_body.py`](../scripts/blockbench/test_apex_body.py) は15件です。旧333キューブや匿名の `head_001` に依存する試験を、実outlinerと新しい密度範囲に合わせました。キューブ・pivot・UVを編集すると実際の出力が変わること、raw animation export、履歴generatorの誤上書き防止、埋め込みPNG、旧2テクスチャとの互換性、回復マスクの欠落・解像度違い・不一致・保護先への出力拒否を確認します。

[`test_apex_reference.py`](../scripts/blockbench/test_apex_reference.py) は6件です。既存28ボーンの全親子構造、承認済みアニメーションSHAと3種類の不変署名、意味の分かる部品名、実際の回転後頂点、細い頭・腹部と後方へ流れるフィン、股関節・前方の膝・後方のhock・前方の爪を検証します。許可されたX回転の変更だけが不変署名から除外され、Y/Z、position、時刻の変更は検出されることも変異データで確認します。首、肩、肘、手首、腰、股関節、膝、足首について、oriented cubeへの距離から内部支持が各pivotへ届くことを確認します。

37クリップをそれぞれ9時刻でサンプリングし、すべてのキューブを実階層とpivotで変形させ、非有限値や大きな離脱がないこと、run / sprint / chargeで頭が下がることを確認します。この数値検証だけでは、見た目の小さな隙間・装甲の干渉・原図への忠実度は保証できません。それらは実レンダリング、Blockbench、Minecraft内の表示で別途確認します。

```bash
python scripts/blockbench/test_apex_body.py -v
python scripts/blockbench/test_apex_reference.py -v
python -m unittest discover -s tests/blockbench -v
./gradlew test
```

胴の寸法は股関節装甲を含む `waist` ボーン全体のbboxだけで比較しません。キューブの辺と水平面の交点から、y24〜26の最細断面、y25の腹部装甲、y20〜23の股外装、y28〜32の胸幅を分けて測定します。最細部分が装甲同士の隙間だった場合に、そこだけを細い腰の証拠として使わないためです。alpha cutoutの透明な角を含むキューブ包絡は、見えているシルエットと区別します。

## 最終アセットに対する実行結果

正式アセット確定後、上記Python検証を実行しました。

| 検証 | 結果 |
| --- | --- |
| 編集元・実export・保護・PNG互換 | 15 / 15成功 |
| 新モデルのリグ・寸法・部品名・姿勢 | 6 / 6成功 |
| 既存Fangの編集元・実export | 7 / 7成功 |
| `apex_body.py check` | 28ボーン / 498キューブ / 37クリップ / 5,316フレーム一致 |
| 1.5.1との差分監査 | 28ボーン名・全parent保持、pivot 18か所変更 |
| 実行用アニメーション | 承認済みSHA-256一致、旧版との差分は胸・首X回転16数値のみ |
| 編集元アニメーション | 全37クリップの時刻・easing保持、35クリップ完全不変、実行用の全数値と一致 |
| 3PNG | 1024×1024、埋め込みPNGと実行用PNGの全バイト一致 |
| 発光の透過対応 | 通常装甲の不透明部分に発光、発光部分の内側に回復マスク |

新旧モデルで同じ骨に属するキューブの回転後8頂点を比較し、旧333キューブと同一位置・同一形状を保持したものは0個でした。旧モデルの形状を少し修正した構成ではなく、28ボーンの互換リグに新しい498キューブを載せています。テスト用Javaファイル2本はJava 17で単体コンパイル成功です。Gradle全体のJUnit・ビルド・Minecraft内の確認結果は全体の検証報告で管理します。

### 寸法の測定結果

以下のuはモデル座標で、16uが1ブロックです。キューブ包絡にはalpha cutoutの透明な端も含まれます。見えている輪郭をこの値だけで判定しません。

| 測定対象 | 結果 |
| --- | --- |
| 全キューブの回転後包絡の高さ | 約2.729ブロック |
| 頭部・マスク・目の包絡幅 | 5.168u |
| 肩の左右の包絡幅 | 15.174u |
| 胸の最大水平断面（y28〜32） | y31.5で10.388u |
| 腹部装甲の水平断面（y25） | 4.677u、胸断面幅の45.0% |
| 最細内部断面（y24〜26） | y24.4375で2.842u、胸断面幅の27.4% |
| 股外装・脚の最大断面（y20〜23） | y20で8.261u |
| 背面フィンの後方端 | Z=7.645u、横方向端は左右4.143u |

run / sprint / chargeの4分の1時刻で、実際のhead pivotを全親階層で変形させた結果、静止状態からそれぞれ2.116u / 5.440u / 7.326u下がり、前方へ4.488u / 6.666u / 8.378u移動しました。最初の追加QAはhead全キューブのbbox中心を頭位置としていましたが、透明なフィン端を含むため不適切でした。実可動head pivotへ測定点を修正し、下降量2uの基準を維持しました。胸・首の補正はこの数値基準ではなく、原図と実際のBlockbench突進姿勢の比較に基づきます。

### Pivot変更18か所

以下は左右対称の9組で、左が+X、右が−Xです。head / neck / face_plate / eye_glowを含む中央の10ボーンのpivotは維持しています。全28ボーンのparentは維持しています。

| 左右のボーン | 旧pivot | 新pivot |
| --- | --- | --- |
| shoulder | `[±5.2,31,0]` | `[±4.9,31,0]` |
| arm | `[±5.8,30,0]` | `[±5.1,30,0]` |
| forearm | `[±6.8,23,-0.5]` | `[±6.4,23,-0.5]` |
| blade | `[±8,24,-1]` | `[±7.7,24,-1]` |
| hand | `[±7,16,-2]` | `[±6.6,14.9,-1.6]` |
| leg | `[±2.8,22,0.5]` | `[±2.4,22,0.5]` |
| lower_leg | `[±3.1,13,-1]` | `[±3.5,13.2,-1.6]` |
| foot | `[±3.1,4,2]` | `[±4,4.2,2.1]` |
| back_blade | `[±3.5,31.5,2.5]` | `[±3.3,31.5,2.5]` |

首のgaiter・装甲ring、肩の黒いballと胸側connector、肘のparent coverとchild axis、手首のgaiterとsocket、腹部の重なり装甲と内部lattice、股のaxle、膝とhockの親子jointを確認しました。足裏と後方の爪の間には左右の `heel_sole_bridge` を追加しています。内部支持の数値検証に加えて、実際に見える状態の隙間と装甲干渉は実機確認の対象です。
