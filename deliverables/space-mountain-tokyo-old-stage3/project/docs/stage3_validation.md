# Stage 3 検査結果

実施日: **2026年10月14日 (日本時間)**。報告は実際に実行した範囲を区別する。

| 検査 | 結果 |
|---|---|
| Pythonテスト | 44件成功、失敗/エラー/スキップ0。旧24件とStage3の20件。 |
| 外観保持 | Stage2入力SHA256を固定し、108,014ブロックの変更・上書き0。 |
| 形式・分割 | 8個のSponge v2/DataVersion3955を独立NBTで読み込み。部品結合は内装全体と一致、共通原点0。 |
| 正式WorldEdit Reader | 改変していない7.3.8 Readerで8個を読み込み、全非空気座標・状態・ハッシュが独立検査と一致。 |
| 数値歩行導線 | 入口→4ゾーン→左ホーム、右ホーム→帰還の順に連続。階段は向きも確認し、中央レーンを横切らない。 |
| 高さ変更 | 床Y5/駅天井Y21に変更して同じ接続を検査。 |
| 実Minecraft貼付 | Minecraft1.21.1 / NeoForge21.1.228 / 正式MOD版WorldEdit7.3.8の専用サーバーでAPI貼付。原点(0,64,0)、空気を省く指定。 |
| 実ブロック状態 | 貼付後の119,124非空気ブロックをMinecraftのServerLevelから照合、不一致0。 |
| 実予約空間 | 中央ライド空間＋駅レーン72,603セルをServerLevelで照合、空気以外0。 |
| 実当たり判定 | 数値導線の216個の足元セルをMinecraftの幅0.6×高さ1.8のAABBで検査。身体の衝突0、下の支持欠落0。人の手で歩いた結果とは異なる。 |
| 保存・終了 | 実サーバーのワールド保存と正常終了、プロセスexit0。再ロード検査は未実施。 |
| プレビュー | 生成した実ブロックから断面/切断等角/人物目線を描画。入口・待機列・駅画像を開いて配置と見せ方を確認。ゲームのスクリーンショットではない。 |

実サーバー試験の最終全体schematic SHA256:
`e225eee6792f36e438299b0381e7148b26520d8acafe172be0be7bdf49439f4d`

実サーバーは旧プロジェクトのワールドを使わず、独立した`SpaceMountainStage3Verification`という空のワールドへ配置した。接続先はlocalhostのみ、online-mode=true、接続プレイヤー0人。元のNeoForge/WorldEdit JARは改変していない。配布ZIPに検査MODや外部JAR、参考画像、検査ワールドは含めない。

## 記録

- `output/stage3/reports/python_tests.txt` / `python_tests.json`
- `output/stage3/reports/numerical_validation.json`
- `output/stage3/reports/worldedit_reader.json`
- `output/stage3/reports/runtime-verification.json`
- `output/stage3/reports/runtime-provenance.json`
- `output/stage3/reports/verification-manifest.json` / `server-console.log`

## 未検証

- 人が操作して入口から駅へ歩くテスト、Windows11、シェーダー、ユーザー指定全MOD組合せの起動・描画・FPS。
- Create/Sable/Coasters Simulatedのレール配置、車両乗車、駅停止、バンク/高速走行時の必要空間。
- 実際の旧東京版内部との一致、実寸、サインやモニターの本物の配置。内装写真は未確認なので推定/創作。
- 指定YouTube動画は今回も未視聴。暗所ライド全体や照明イベントはStage4以降。

検査MODと実行補助コードは`tools/runtime-test`に保持するが、現在の実行準備スクリプトはこのクラウドの既存NeoForgeライブラリの場所を使う。一般Windows用の導入手順ではない。ユーザーが建築を使うために検査MODを入れる必要はない。
