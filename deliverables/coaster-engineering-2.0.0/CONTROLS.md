# 操作方法

## 乗車と駅GUI

配布ワールドは研究施設の駅から始まります。停車中で空席のある車両を右クリックすると乗車し、7秒後に自動発車します。3両・各2席、計6席。自作コースの編成は1〜4両に設定できます。

F5で視点を切り替え、Shiftで降車します。走行中なら編成全体を停止します。降車地点は駅を優先し、固い足場、液体、乗員の空間を確認して探します。シングルプレイではメニューの「保存してタイトルへ戻る」で終了します。

駅コントローラをエディタなしで右クリックするとGUIを開きます。利用範囲は8ブロック以内。乗車中の同じコースのプレイヤー、または権限レベル2の管理者は発車・緊急停止を操作できます。駅戻し、警報解除、自動発車、停車時間の変更は管理者専用です。

| GUI操作 | 動作 |
| --- | --- |
| 発車 | 完全な編成と安全な現在位置を確認して発車・再開 |
| 緊急停止 | 全編成を停止し、コースの警報を保持 |
| 駅へ戻す | 読み込み済みの全車両が停止・無人のとき駅へ戻す |
| 警報解除 | 警報だけを解除。解除処理そのものは車両を動かさない |
| 自動発車 | 駅で停止した完全な編成に乗客がいる場合、停車時間後に発車 |
| 7秒 / 20秒 / 60秒 | 自動発車の停車時間を140 / 400 / 1200 Tickに設定 |

警報中は自動発車やレッドストーンも発車できません。レッドストーンは電源の新しい立ち上がりで反応し、入力が高いままでは帰着後に繰り返し発車しません。

## 運転・建設コマンド

`/coaster` はヘルプを表示します。`list` と `status` は全プレイヤーが使用できます。その他の建設・運転・管理コマンドは権限レベル2が必要です。`<UUID>` は `/coaster list` に表示されるコースID、`<点>` は1から始まる制御点番号です。

| コマンド | 操作 |
| --- | --- |
| `/coaster demo` | 開けた場所に最小立体コース、駅、支持柱、1両を生成 |
| `/coaster list` | 現在のディメンションのコース名とUUIDを表示 |
| `/coaster status` | 最寄りまたは乗車中の車両の速度、距離、周回数、乗客数、運転状態 |
| `/coaster start` | 乗車中、または32ブロック以内の最寄り編成を発車・再開 |
| `/coaster stop` | 編成を緊急停止し、コースの警報を保持 |
| `/coaster clear_alarm [UUID]` | 最寄り編成または指定コースの警報だけを解除 |
| `/coaster train <1〜4>` | 全車両が読み込み済み・停止・無人の編成の両数を変更 |
| `/coaster reset` | 全車両が読み込み済み・無人の編成を駅へ戻す |
| `/coaster create "名前"` | 手持ちエディタの4点以上から閉じたコースを保存 |
| `/coaster spawn <UUID>` | 保存済みコースの出発点に1両を配置。予約済み編成との重複を拒否 |
| `/coaster delete <UUID>` | コースと読み込み済み車両を削除。駅や支持柱の建築ブロックは残る |
| `/coaster supports <UUID>` | 空気の部分に支持梁を生成。既存ブロックを保持 |
| `/coaster station bind <UUID>` | 見ている8ブロック以内の駅コントローラをコースへ紐づけ |
| `/coaster clear` | 手持ちエディタのドラフト、設定、選択をクリア |
| `/coasterclient status` | 実乗車先、速度、バンク、周回数、車両番号、読込数に加え、FXゾーン・稼働音楽数・演出回数・音源数を表示 |

`coasterclient status` は読み取り専用のクライアントコマンドです。1コースには1編成、1コースには1箇所の駅コントローラを割り当てます。未読み込み車両の予約も保持し、欠けた車両を勝手に再生成しません。削除後の未読み込み車両は再読み込み時に走行を停止します。

## 新しいコースの作成

```mcfunction
/give @s coasterengineering:course_editor
/coaster editor kind lift
/coaster editor bank -15
/coaster editor speed 40
```

エディタを持ってブロックの面を右クリックすると、面から半ブロック離れた場所に制御点を追加します。Shift＋ブロック右クリック、またはドラフト中のShift＋空中右クリックで末尾の点を削除します。エディタを持つと制御点と曲線をプレビューします。

`editor kind / bank / speed` は、ドラフト中には以後に追加する点の設定です。種別は `normal / boost / brake / lift / station`、バンクは−75〜75度、目標速度は0〜120km/h。

最初の点を置いたときのプレイヤーの足元が駅の降車地点になります。固い安全な足場と2ブロックの空間を保ち、最初の制御点から6ブロック以内に置いてください。ドラフトはディメンションを記録します。

4点以上置いたら次を実行します。

```mcfunction
/coaster create "My Course"
/coaster spawn <表示されたUUID>
```

保存時に第1点を発車用のSTATION区間とし、目標速度を最低10.8km/hにします。名前は1〜64文字、制御点は最大128、コース長は8〜16,000ブロック、隣接点間は最大512ブロックです。

車両配置アイテム `coasterengineering:coaster_vehicle` でも、保存済みコースのレールから4ブロック以内を右クリックすると、そのコースの駅に1両を配置できます。両数は後から `train` で変更します。

## 保存済みのコースを編集する

全車両を読み込み、停止させ、乗員を降ろします。UUIDを変えずに編集し、変更が通ると空の編成を出発点に戻します。提案したコース全体の空間を検査し、障害物や未読み込み領域があれば元のコースを保持して拒否します。

```mcfunction
/coaster editor select <UUID> <点>
```

選択モードでは、制御点の3ブロック以内を右クリックすると選択点を切り替えます。Shift＋ブロック右クリックで選択点を移動します。`editor kind / bank / speed` は選択済みの保存コースの点へ適用します。選択後に対象点や点数が変わった場合は再選択してください。

`/coaster editor draft` または選択中のShift＋空中右クリックでドラフトモードへ戻ります。このモード切り替えでは保存済みの点を削除しません。

コマンドでも直接編集できます。

| コマンド | 操作 |
| --- | --- |
| `/coaster edit <UUID> move <点> <x> <y> <z>` | 制御点を移動 |
| `/coaster edit <UUID> remove <点>` | 制御点を削除。閉じたコースは最低4点、開いたコースは最低2点 |
| `/coaster edit <UUID> bank <点> <度>` | バンクを−75〜75度に変更 |
| `/coaster edit <UUID> kind <点> <種別>` | 区間種別を変更。第1点はSTATIONを保持 |
| `/coaster edit <UUID> speed <点> <km/h>` | 目標速度を0〜120km/hに変更。第1点は最低10.8km/h |
| `/coaster edit <UUID> landing <x> <y> <z>` | 安全な駅の降車地点を変更 |

第1点と駅の降車地点は6ブロック以内、駅コントローラと降車地点は8ブロック以内を保ちます。大きく駅を移設する場合は古いコントローラを外し、第1点と降車地点を少しずつ移動してから新しいコントローラを配置・紐づけしてください。

## 火山ワールドの生成と走行診断

いずれも権限レベル2が必要です。火山生成は専用のシード `20261011` のOverworldで使用し、草の表面がY=63で、対象領域が未改変、保存済みコースがないことを検査します。新しいワールドの準備は [README.md](README.md) の `scripts/prepare-volcano-server.sh` を使ってください。

| コマンド | 操作 |
| --- | --- |
| `/coaster volcano` | 対象領域を検査し、Tickごとの処理量を制限して火山・地下空間・駅・コース・3両編成を生成 |
| `/coaster volcano status` | 生成段階、進捗、完了・失敗の状態を表示 |
| `/coaster volcano cancel` | 生成を中止。既に置いた景観ブロックは残る |
| `/coaster upgrade volcano` | バックアップ済みの元ワールドで、停車・無人の編成を維持して補修・照明・速度を更新 |
| `/coaster upgrade volcano status` | 更新段階と完了時の補修数・照度検査を表示 |
| `/coaster upgrade volcano cancel` | 更新を中止。適用済みの安全な景観は残り、未完了の速度設定は保持 |
| `/coaster upgrade volcano reapply` | 修繕と照明を再適用し、区間の速度設定を1.1.0の既定値へ戻す |
| `/coaster validate <UUID>` | 完全・無人・停止中で警報のない編成を、実サーバーのTickで1周させて駅停止まで検査 |
| `/coaster validate status` | 診断の進捗または最後の結果、距離・時間・最高速度を表示 |
| `/coaster validate cancel` | 診断を停止し、安全に可能なら駅へ戻す。一時チャンク保持を解除 |

サーバー起動完了の `Done` を待って生成を開始し、自動で続く品質更新が `/coaster upgrade volcano status` で `COMPLETED` になってから走行診断してください。診断は保存済みの経路周辺チャンクが読み込み済みであることを確認してから一時的に保持します。正常終了・失敗・中止時に保持を解除し、永続的な強制読み込みを残しません。乗客が乗る、警報が発生する、コースが変わるなどの場合は停止します。

火山コースのUUIDは `61da851c-7705-3a82-b7b3-2e2e8ee296b4`。無人診断は実プレイヤーを模擬しておらず、実クライアントの乗車試験とは区別します。配布ワールドで生成をやり直す必要はありません。生成途中の失敗・中止後は、新しい専用ワールドでやり直してください。

## 音・演出と快適性

`config/coasterengineering-client.toml` の `bankCamera=false` で視点の傾きを抑え、`followTrackCamera=false` で経路の向き・傾斜への追従を止められます。`cameraRollLimitDegrees` は0〜60度、初期値25度です。

`attractionMusic=false` または `attractionMusicVolume=0` でBGMを停止します。Minecraftの音楽・主音量設定も適用します。`effectsIntensity=0` はクライアントの追加粒子、画面端の光、旧コースの短い効果音を無効にします。2.0ではサーバーが制御する怪物・照明・扉・警報などのショーは継続し、Minecraftの各効果音カテゴリで音量を調整できます。音楽とカメラの設定は走行を変更しません。降車時は約2秒でBGMと走行音をフェードアウトし、ワールド退出では即時に音源を解放します。リソース再読み込みでは停止後に乗車状態に応じて再開します。

`/coasterclient status` の `sound assets=9/9` は既存のBGM3曲・短い効果音4個・風切り音/車輪音2個の読込、`active music` はソフトウェア上の稼働数、`events` はクライアントの演出通知数です。旧コースのFXゾーンは研究施設の `research`、深淵の `abyss`、脱出の `escape`。2.0ではさらに `themes=5/5`、`stage`、`cue`、`seq`、`gain`、`silence`、`gaze` が表示されます。走行中は通常`active music=1`、`motion=2`、降車約2秒後は両方0が期待値です。区間切り替え中はクロスフェードのため一時的に2つの音楽が稼働し、静寂イベントでは音楽が0になる場合があります。実施済みの確認はTEST_RESULTSを参照してください。これらは実際に音が聞こえたことを示す値ではありません。

## 1.1.0の音量と快適性

ゲームを終了してから`config/coasterengineering-client.toml`を編集します。Minecraftの音楽と主音量スライダーも適用されます。既存ファイルの手動設定は上書きしません。旧初期値0.35を使っている場合は次の値を推奨します。

```toml
attractionMusic = true
attractionMusicVolume = 0.8
rideSounds = true
rideSoundVolume = 0.35
cameraVibrationDegrees = 0.18
cameraFovPercent = 2.0
```

カメラ演出を使わない場合は`cameraVibrationDegrees=0.0`、`cameraFovPercent=0.0`にします。既存の`bankCamera`、`followTrackCamera`も引き続き使用できます。FOV変化の上限は3%、追加振動の上限は0.25度です。


## 2.0専用アトラクション

- `/coaster volcano2 [status|cancel]`: 新seed20261012専用ワールドの生成・状態・中止。旧worldは拒否します。
- `/coaster attraction status [UUID]`: ショー・停止・音楽・イベント履歴の管理状態。
- `/coaster attraction reload [UUID]`: 停止した読み込み済み編成で、ワールド内JSONを再読込。
- `/coasterclient capture start|stop|status`: 実乗車の区間・停車画面をscreenshotsへ記録。

新UUIDは `fd0fb037-a161-3b04-931f-de6e3c3a422e`。ワールドは別フォルダ、`Coaster World - Volcanic Abyss 2.0`を使用します。操作・BGM・カメラ設定は2.0 READMEとBGM_GUIDEを確認してください。1.1用の`upgrade volcano`は旧固定コースだけの操作で、2.0へ使う必要はありません。

2.0の仮BGMは既存3曲を5区間に割り当てています。Suno曲の交換手順、単一曲の編集時の切り出し、ランタイムの任意シークが未実装であることは `BGM_GUIDE.md` に記載しています。

カメラ演出は次の設定で個別に止められます。ゲームを終了してからクライアント設定を編集します。

```toml
cameraVibrationDegrees = 0.0
cameraFovPercent = 0.0
automaticGaze = false
```

`automaticGaze=true` の初期影響量は `automaticGazeBlend=0.30`。怪物との遭遇時の一人称でだけ、最大横13.5度・縦6度の視線の手がかりを滑らかに加えます。マウスの自由な視線と、プレイヤーのサーバー上の向きは維持します。三人称では自動視線を使いません。

撮影は乗車前に `/coasterclient capture start` で開始します。乗車・12チェックポイント・駅帰還の最大14枚をMinecraft標準のスクリーンショットとして、ゲームフォルダーの `screenshots/volcanic-v2-*.png` に保存します。状態確認は `/coasterclient capture status`、中止は `/coasterclient capture stop`。通常地点では通過後の最初の実描画フレームを保存するため、低FPSでは指定地点より先の画像になる場合があります。撮影した実距離と指定地点との差は `V2_CAPTURE_CHECKPOINT`、保存結果は `V2_CAPTURE_FILE` で確認し、実際のPNGも開いてください。途中からの開始や再乗車では、過去の地点の画像を作りません。詳しい実乗車の検証手順は `docs/CLIENT_VALIDATION_2.0.md` を参照してください。
