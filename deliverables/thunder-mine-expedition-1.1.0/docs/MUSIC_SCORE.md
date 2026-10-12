# Thunder Mine Expedition — オリジナル乗車音楽

3曲とも本プロジェクト用に作曲し、Pythonで楽器音を合成しました。他の楽曲、録音、効果音、サンプルライブラリ、SoundFontを使用していません。スコア・合成スクリプト・生成音声は補助MODと同じMITライセンスです。

| サウンドイベント | ファイル | 曲名・構成 | テンポ | 長さ |
|---|---|---|---:|---:|
| `thunder_mine_tools:ride_exploration` | `exploration.ogg` | Lanterns at Red Mesa。バンジョー風の旋律、ギター風伴奏、交互の低音、シェイカーと木打楽器。16小節の探検曲。 | 108 BPM | 35.556秒 |
| `thunder_mine_tools:ride_suspense` | `mine_suspense.ogg` | Picks Behind the Wall。低い持続和音、採掘を連想させる刻み、離れた金属音と不穏なベル旋律。12小節。 | 96 BPM | 30.000秒 |
| `thunder_mine_tools:ride_chase` | `canyon_chase.ogg` | Run Across Iron Canyon。フィドル風の旋律、細かなギターとバンジョー風応答、疾走する低音とドラム。16小節。 | 132 BPM | 29.091秒 |

音声形式はすべて44,100 Hz・ステレオ・OGG Vorbis、品質設定5です。`sounds.json`はストリーミング再生を指定しています。音の余韻と短いステレオ残響を曲の先頭へ折り返して合成し、ループの境界で切り落としていません。Minecraftの再生側でループを設定してください。OGGの`LOOPSTART`・`LOOPEND`タグは確認用で、Minecraftがこれらを自動的に解釈するという意味ではありません。

再生成方法：

```bash
python3 scripts/generate-original-mine-music.py
```

Python 3、NumPy、`ffmpeg`（libvorbis）、`ffprobe`が必要です。スクリプト内に全曲の音符・和音・音色・テンポ・乱数シードを記載しています。同じツール構成では音声を再現できます。`--track exploration`などで1曲だけ再生成できます。

出力先：`helper/src/main/resources/assets/thunder_mine_tools/sounds/music/`。楽曲を交換する場合は、同じ名前のOGGをリソースパックの`assets/thunder_mine_tools/sounds/music/`へ配置できます。曲名・演奏時間を変更する際は再生側の切替・ループ動作も確認してください。

`evidence/music-original-synthesis.json`に、音符数・打楽器数・時間・フレーム数・ピーク・RMS・ループ境界差分・SHA-256とツール版を保存しています。OGGを実際に再デコードし、Vorbis・44.1 kHz・2ch・全フレームの保持・クリッピングなし・有限サンプル・極端な無音なしを検査しました。

実際に人が聴いた音質、Minecraft内の音量バランス、区間切替、降車時フェード、ゲーム内の連続ループは、この音声生成作業だけでは未検証です。ゲーム側の試験結果と区別してください。
