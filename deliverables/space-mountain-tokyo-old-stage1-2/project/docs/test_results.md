# Stage 1/2 テスト結果

実施日: 2026-10-13 UTC。Linuxクラウド、Python 3.12.14、NumPy 2.3.5、Pillow 12.3.0。専用venvの新規導入、生成、回帰検査を実行した。Windowsを起動したという意味ではない。

| 検査 | 結果・確認範囲 |
|---|---|
| Python回帰テスト | 24件成功、失敗0、エラー0、スキップ0。`output/reports/python_tests.*`。 |
| 独立NBT/形式検査 | 14個成功。gzip、Sponge v2、DataVersion3955、Palette、VarInt、YZX、空Entity、原点。 |
| 正式WorldEdit7.3.8 Reader | 改変していない正式Readerで最終14個を実読込し、独立readerの全非air座標/stateハッシュと一致。スタンドアロンのバニラ1.21.1 registryを使用。ゲーム内pasteとは別。 |
| 登録ブロック・state | 正式BlockFactoryで厳密に解析。未知ブロックをairに置換した状態を合格にしない。 |
| 分割・位置 | 四分割の重複0、結合結果は外観全体90,140ブロックと同じ。全14clipboard origin0。 |
| 保護領域 | 通常モデル108,014ブロックと全予約領域の交差0。後から上書きする配置でもユーザー予約を守る回帰検査。 |
| 仮ガイドの空間 | 各ガイド点と線分周辺を予約。初期9×9の空間が塞がれないことをデータで検査。物理的な通過テストではない。 |
| 屋根・入口 | 屋根全水平セルに指定高さのブロックが存在、偶発的な屋根穴0。南入口から中央への人物用空間をデータで検査。 |
| レール | 全Paletteはバニラ、rail/powered_rail等なし。車両とBlockEntityもなし。ガイドはマゼンタガラスのみ。 |
| 画像 | 生成ブロックから等角・正面・上面・縮小版を描画、実際に画像を開いて外形と凡例を確認。予約領域図も確認。ゲーム画面ではない。 |
| Windows向け依存 | PyPIで指定NumPy/Pillow版のHTTP200、必要Python版、Windows AMD64 wheelの存在を確認。BAT実行は未検証。 |
| 配布ZIP | 全項目のCRCとSHA256を照合、解凍して生成・テストする配布検査を実施。外部写真・JAR・venvを除外。詳細は配布検査JSON。 |

再検証:

```bash
python3 generator/main.py
python3 tools/check_project.py
python3 -m generator.validate_schem output/schem/*.schem
```

任意の正式Reader再検証 (Java21とHTTPS取得が必要):

```bash
python3 tools/check_worldedit_738.py output/schem/*.schem --report output/reports/worldedit_reader.json
```

コード・形状が変わると既存報告のハッシュは古くなるため、再実行で更新する。

## 未検証・未実装

- Minecraft/NeoForge内でのWorldEdit貼付、実ゲーム描画、FPS、全指定MODの同時起動。
- Windows11での生成BAT、正式Reader検査ツールの実行。
- Create: Coasters Simulatedでの実レール配置、バンク時・動的車両のクリアランス、走行・駅停止。今回レールを作成していない。
- 正式な実寸、写真の原撮影者・撮影日、2024年閉鎖直前との一致、裏面や内装、正確なリブ・アンテナ本数。
- 指定YouTube動画、OLC/TDR公式本文はクラウド接続403で未確認。
- Stage3～7の本格内装・待機列・駅・暗所照明・装飾。入口・広場はStage2の仮造形で、完成再現ではない。

成功は上表の範囲に限定する。建築ファイルが読めることを、実際に乗車可能なワールドや走行成功の証拠にはしない。
