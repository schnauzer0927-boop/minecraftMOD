# Apex Predator 1.5.1 — Blockbench実機検証

2026-10-10 UTC。公式Blockbench **5.2.2 Linux Desktop**と公式GeckoLib Animation Utils **4.2.5**を使用しました。チェックは実アプリのプロジェクト読み込み・保存・Edit・Paint・Displayと書き出しcodecで実施しています。編集例は検証用コピーに保存し、配布するゲーム用モデル・PNG・アニメーションは変更していません。

| 対象 | 読み込み・保存の結果 |
| --- | --- |
| `models/blockbench/apex.bbmodel` | GeckoLib Entity、28ボーン、333キューブ、37クリップ、5,316キーフレーム。階層・pivot・UV・キーフレームの時刻/値/easingが保存後も一致 |
| 本体テクスチャ | `apex.png`と`apex_glow.png`の512×512埋め込みPNGを読み込み。両テクスチャのエラー0 |
| `models/blockbench/apex_fang.bbmodel` | Java Block/Item、56キューブ、18グループ、128×128埋め込みPNG。保存後の専用CLI出力は既存JSON・PNGと一致 |
| Fang表示設定 | 一人称左右、三人称左右、GUI、ground、fixed、headの8表示設定をDisplayモードで読み込み。全行列が正常 |
| Fang発光 | 名前付き4グループから9キューブのForge fullbrightを再出力し、既存設定と一致 |

本体のhead pivot・マスクキューブ寸法・UV、Fangの刃先寸法・UVを実Editで変更し、保存した`.bbmodel`から専用CLIを使って変更が反映されることを確認しました。実Paintで変更した1ピクセルも、埋め込みPNGから書き出した画像に反映されました。以前のPython生成器やゲーム用JSONを読み直して形状を復元する方式にはしていません。

専用Pythonテストは本体13件、Fang7件、計20件成功しました。新規JUnit `BlockbenchAssetTest` 4件も親ビルドで成功しています。これらは全ボーン・UV・時刻・PNGの保持に加え、実際の編集内容の反映、不正な形状の拒否、旧生成器の上書き防止を検証します。

## 書き出し時の互換性

Blockbench標準のAnimation Exportはタイムラインのsnappingに合わせて時刻を丸めます。chargeの`0.0563`秒などは最大snappingでも完全には表せません。通常保存は元の時刻を保持します。専用の`export-animations`は生のキーフレーム時刻から別ファイルへ出力し、実アプリで再保存したプロジェクトから全37クリップ・5,316時刻・値・easingが元のJSONと一致することを確認しました。既存ランタイムアニメーションへの直接出力は拒否します。

標準GeckoLib geometry書き出しも確認しました。キューブ・pivot・UVは同じですが、ゼロ回転の省略や表示範囲の自動拡張が発生します。専用geometry CLIは保存された表示範囲とボーン順を保持します。Fangのネイティブ保存による`180° → −180°`は同じ向きとして正規化して書き出します。

実UI画像：

- [Apex Edit](visuals/apex-1.5.1-blockbench-body.png)
- [37アニメーション](visuals/apex-1.5.1-blockbench-animations.png)
- [Fang Edit](visuals/apex-1.5.1-blockbench-fang.png)
- [Fang一人称Display](visuals/apex-fang-1.5.1-blockbench-firstperson.png)

詳細な再現コマンドは[正式編集手順](../models/blockbench/README.md)を参照してください。Minecraft内の一人称・三人称・クールダウン検証は、別の[クライアント記録](VALIDATION-1.5.1-client.md)へ記載します。BlockbenchプレビューにはGeckoLibのゲーム内発光レイヤーを合成していません。
