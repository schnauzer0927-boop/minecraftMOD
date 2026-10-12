# VOLCANIC ABYSS 2.0 BGMの交換

Suno等で制作した音楽は、MODのJARを編集せず、Minecraftのリソースパックから交換できます。音楽未提供でも、同梱のオリジナル仮BGMで走行・演出は動作します。5区間の仮BGMは既存の3曲を再利用しており、5曲の新作音楽が完成したという意味ではありません。

| ファイル | サウンドイベント | 使用する場面 |
| --- | --- | --- |
| `exploration.ogg` | `coasterengineering:music.exploration` | クリスタル湖・地底探索 |
| `ruins_chase.ogg` | `coasterengineering:music.ruins_chase` | 古代都市の高速走行 |
| `monster_encounter.ogg` | `coasterengineering:music.monster_encounter` | 生命体の巣・怪物との遭遇 |
| `eruption_escape.ogg` | `coasterengineering:music.eruption_escape` | 怪物からの逃走・火山噴火 |
| `finale.ogg` | `coasterengineering:music.finale` | 巨大落下・最終脱出・帰還 |

実際の切り替えは、サーバーのイベント定義が指定する距離・イベント時刻を使います。乗車中の経過秒を別に推定して切り替える方式ではないため、停止演出中に曲が先の区間へ進むことはありません。怪物との遭遇では指定時間だけBGMをフェードダウンし、発進後は指定された逃走曲へ移ります。旧1.1.0のワールドでは従来の3曲の動作を維持します。

## 5曲を用意する場合

1. ソースに含まれる `examples/VolcanicAbyss-BGM` フォルダーをコピーします。
2. コピーしたフォルダー内の `assets/coasterengineering/sounds/music/` に、上記5ファイルを配置します。**Ogg Vorbis**を使用してください。拡張子を変更しただけのMP3やOgg Opusは使えません。
3. `pack.mcmeta` がZIPの直下になるようにフォルダーの内容をZIPにします。
4. Minecraftの「設定 → リソースパック → パックフォルダーを開く」へZIPを入れ、選択します。Minecraft 1.20.1の `pack_format` は15です。
5. F3+Tで再読み込みし、乗車してください。MODの音楽イベント名が同じなので、新しい曲に置き換わります。未配置の曲はJARの仮BGMを引き続き使用します。

`sounds.json` の `replace: true` は曲候補を混ぜず置き換えるための指定です。`stream: true` は長い音楽をストリーム再生するための指定です。リソースパックの優先順位にも注意してください。

音量の初期値は `config/coasterengineering-client.toml` の `attractionMusicVolume = 0.8` です。Minecraftの「音楽」と「主音量」も適用されます。プレイヤー相対音源で、距離減衰はありません。曲切り替え・乗車・降車では通常40tick（20 TPSで約2秒）のフェードを使います。2.0の一時的な静寂イベントだけは10tick（約0.5秒）でBGMをフェードアウトし、既にクロスフェード中の前の曲も同じ短い時間で止めます。静寂の終了後は通常40tickでフェードインします。走行音は別に `rideSounds` と `rideSoundVolume` で調整できます。

音源は急な大音量を避けて制作し、書き出し後のピークを確認してください。無音部分を含む曲は、MODが追加した無音ではなく音源の内容として再生されます。自然なループには、曲の末尾と先頭がつながる音源を用意してください。

## 1曲から場面ごとの再生位置を指定する場合

Minecraft標準のストリームに任意の秒へシークするAPIはありません。このMODでは、元の1曲から**編集時に各場面の再生位置を切り出す**方法を用意しています。実行中に大きな単一ファイルを任意位置へシークできる、と偽装する方式ではありません。

`examples/bgm-cues.example.json` の5区間について、元曲内の `startSeconds` と `durationSeconds` を指定します。これらは元音源内の位置で、コースの走行時間ではありません。元曲が短ければ範囲を縮めるか、同じ部分を複数区間に割り当ててください。

Python 3とFFmpegを導入したPCで、プロジェクトのフォルダーから実行します。

```powershell
python scripts/prepare-bgm.py --input "my-suno-song.wav" --cues "examples/bgm-cues.example.json" --output "MyVolcanicBGM"
```

`MyVolcanicBGM.zip` が作られます。5ファイルへの変換と切り出し、Vorbis形式の確認、端の短いフェード、リソースパック設定を生成します。切り出した部分は区間中にループし、区間移動時にクロスフェードします。中断後の再乗車は現在の区間の曲の先頭から再生します。音源内の拍に完全同期する機能や、連続した単一楽曲の途中秒へのランタイムシークは未実装です。

## カメラ設定と診断

`cameraVibrationDegrees = 0`、`cameraFovPercent = 0`、`automaticGaze = false` は個別に演出をOFFにします。自動視線誘導は遭遇時の一人称のみで、プレイヤー自身の向きや車両走行を変更しません。初期値の影響量0.30では追加される視線の変化は横13.5度・縦6度以内で、滑らかに入り、元の自由な視線に戻ります。

`/coasterclient status` で選択曲、実際に有効な音楽・走行音、旧素材9件と新テーマ5件の読み込み、イベントのstage・cue・sequence・gain・silence・gaze残tickを確認できます。`gain` はフェード先の目標値なので、静寂開始直後の `gain=0` はそのフレームで完全に無音になったという意味ではありません。音楽素材が読み込めない場合は既存曲へフォールバックし、車両の走行処理は継続します。乗車中の人間による聴取・音質の判断は、ゲーム画面のログや自動テストとは別に必要です。

乗車前に `/coasterclient capture start` を実行すると、Minecraft標準のスクリーンショット機能で区間と停車位置の実際の画像を自動保存します。保存先は使用中のゲームフォルダーの `screenshots` です。撮影の状態は `/coasterclient capture status`、中止は `/coasterclient capture stop` で確認します。画面や音の確認手順は `docs/CLIENT_VALIDATION_2.0.md` を参照してください。
