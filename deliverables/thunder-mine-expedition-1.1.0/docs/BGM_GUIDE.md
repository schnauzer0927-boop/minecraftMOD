# 乗車中のオリジナル BGM

音楽対応版の Thunder Mine Tools は、探索・坑道・疾走の 3 曲を同梱します。音源はこのプロジェクトのコード進行・旋律・合成楽器から生成したオリジナル作品です。既存作品の楽曲や第三者 MOD の音声ファイルをコピーしていません。楽譜と生成方法は [MUSIC_SCORE.md](MUSIC_SCORE.md)、再生成スクリプトは `scripts/generate-original-mine-music.py` にあります。

**音楽なしの旧 1.0.0 / 1.0.1 と、音楽対応版の検証結果は区別してください。** 音源の生成とファイル検査は完了しました。実ゲームでの再生・切替・降車時の終了は、統合後に `TEST_RESULTS.md` へ記録します。クラウドの音声出力は null 出力のため、実際の聞こえ方は未検証です。

## 再生の仕組み

専用 BGM はワールドの固定座標ではなく、実際に乗車しているプレイヤーのクライアントで再生します。Minecraft の **音楽（MUSIC）**カテゴリを使用し、距離による減衰を無効にします。車両が駅から離れても音量は下がりません。

サーバーが、元の Create 座席への乗車・本アトラクションの実車両 UUID・レールへの拘束を確認して、区間と実速度を送信します。クライアント側も自分の乗車、車両、ディメンション、コースの指紋を照合します。乗っていないプレイヤーに自動で BGM を流す実装ではありません。

| 走行区間 | 同梱曲 |
|---|---|
| 駅・出発・低速の荒野・帰還 | exploration |
| 坑道・巨大地下採掘場 | mine_suspense |
| 峡谷・荒野の高速走行 | canyon_chase |

区間が変わると約 1.2 秒でクロスフェードします。降車、明示的な停止、乗車情報の途絶ではフェードアウトして終了します。エラーや音源不足があっても、この音楽処理から車両位置・姿勢・走行状態を変更しません。

同梱曲はループ用です。Minecraft のストリーミング再生で繰り返し、曲の途中で区間が変われば次の曲へフェードします。区間に応じて曲を選ぶ方式で、任意の秒数へのシークや映像作品のような厳密なフレーム同期は実装していません。

## 音量と ON/OFF

ゲームディレクトリの次のファイルで設定します。

```text
config/thunder-mine-tools-client.toml
```

```toml
[ride_audio]
enabled = true
volume = 0.65
fade_seconds = 1.2
```

- `enabled`：BGM の有効・無効。
- `volume`：0.0〜1.0。初期値 0.65。
- `fade_seconds`：0.2〜4.0 秒。曲の切替と終了のフェード時間。

Minecraft の **主音量**と**音楽**のスライダーも適用されます。どちらかが 0 なら、BGM は聞こえません。聞こえないときは、音楽対応版の補助 JAR が入っていること、`enabled`、主音量、音楽の値を順に確認してください。設定変更のために世界のデータを編集する必要はありません。

通常のバニラ音楽は、専用 BGM の再生中だけ重ならないよう抑制します。効果音のカテゴリや他のプレイヤーの音量設定は変更しません。

## 自作 OGG への交換

元の MOD JAR を編集せず、Minecraft のリソースパックで同じパスのファイルを差し替えます。Minecraft 1.21.1 用のリソースパックは次の構成にします。

```text
ThunderMineMusic/
  pack.mcmeta
  assets/
    thunder_mine_tools/
      sounds/
        music/
          exploration.ogg
          mine_suspense.ogg
          canyon_chase.ogg
```

`pack.mcmeta` の内容：

```json
{
  "pack": {
    "pack_format": 34,
    "description": "Thunder Mine Expedition - Custom Music"
  }
}
```

このフォルダをゲームディレクトリの `resourcepacks` に入れ、ゲームの「リソースパック」で有効にします。変更する曲だけを用意すれば、ほかは同梱曲を使用できます。同じパスに OGG を置く場合、追加の `sounds.json` は不要です。Minecraft のリソース再読込後、次の乗車から確認してください。

OGG Vorbis、44.1 kHz または 48 kHz、ステレオが適しています。ファイル名を変えるだけでは MP3 / WAV を OGG に変換できません。変換する場合は、例えば次を使用します。

```bash
ffmpeg -i "my-music.wav" -c:a libvorbis -q:a 5 -ar 44100 "exploration.ogg"
```

ループする曲は、先頭と末尾が自然につながるように編集してください。Suno 等で自作した曲を使う場合も、自分が利用できる音源を用意します。1 曲だけ使いたいときは、その OGG を 3 つのファイル名で配置できます。ただし、区間変更ではクロスフェードして曲を先頭から再生します。

## 実ゲームの確認記録

クライアントの `logs/latest.log` には、次の記録を出します。

- `MINE_AUDIO_CLIENT_STATE`：実乗車で受信した区間・車両・速度。
- `MINE_AUDIO_CLIENT_START`：音源とイベントの存在、MUSIC カテゴリ、減衰なしの再生開始。
- `MINE_AUDIO_CLIENT_SAMPLE`：SoundManager 上の再生状態とフェード音量。
- `MINE_AUDIO_CLIENT_STOP`：フェード終了・ワールド変更・音声エラー等での停止。
- `MINE_AUDIO_MISSING_RESOURCE`：交換用音源が見つからない場合。

これらは実際のクライアント動作を調べる記録です。記録の生成を確認する前に再生成功とは扱わず、null 出力での成功を実際の聴取成功とは扱いません。
