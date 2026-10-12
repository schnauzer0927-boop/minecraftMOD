# Thunder Mine Expedition の導入

必要な環境は **Minecraft Java Edition 1.21.1 / NeoForge 21.1.228 / Java 21** です。以前の Coaster Engineering の Minecraft 1.20.1 / Forge 環境とは別のランチャープロファイルとゲームフォルダを使ってください。

## 1. MOD を用意する

NeoForge 21.1.228 を公式ランチャーの Minecraft 1.21.1 プロファイルへ導入し、次の **4 本**をそのゲームフォルダの `mods` に入れます。

```text
create-1.21.1-6.0.10.jar
sable-neoforge-1.21.1-2.0.6.jar
simulatedcoasters-0.1.5.jar
thunder-mine-tools-1.1.0.jar
```

最初の 3 本は今回の添付 JAR、または [必要 MOD 一覧](MOD_LIST.md) の同一公開版を使用してください。最後の 1 本はこのワールドの成果物として配布する独立した補助 MOD です。Create / Sable / Simulated Coasters の本体は改変していません。

Flywheel、Ponder、Sable Companion、Veil、Rapier は添付 JAR に同梱されています。別の JAR を手動追加しないでください。最初は上記 4 本だけで起動します。

## 2. ワールドを展開する

`CoasterWorld-ThunderMineExpedition.zip` をダウンロードして展開します。

Windows では `Win + R` を押して次を入力すると、標準の保存先がエクスプローラーで開きます。

```text
%APPDATA%\.minecraft\saves
```

ランチャーで専用ゲームディレクトリを指定した場合は、そのフォルダ内の `saves` を開いてください。展開した `Coaster World - Thunder Mine Expedition` フォルダを `saves` にコピーします。

次のように **ワールドフォルダ直下に `level.dat` がある構成**にします。

```text
saves/
  Coaster World - Thunder Mine Expedition/
    level.dat
    region/
    data/
    sublevels/
    ...
```

`saves/CoasterWorld-ThunderMineExpedition/Coaster World - Thunder Mine Expedition/level.dat` のような二重フォルダにしないでください。`sublevels` は実車両の保存データなので、省略・移動・削除すると車両が復元できません。ZIP 全体を展開してください。

Minecraft の「シングルプレイ」から **Coaster World - Thunder Mine Expedition** を開きます。

## 3. 乗車する

駅の案内に従い、停止中の車両のそばで **木製の乗車ボタン（BOARD）**を右クリックします。元の Create の座席へ乗車し、初回は約 3 秒の待機後に自動発車する設計です。通常の乗車にコマンド・チート・OP 権限は必要ありません。

| 駅のボタン | 操作 |
|---|---|
| 木製・BOARD | 停止中の車両へ乗車 |
| 灰色の石製・START | 駅に停止している車両を手動発車 |
| 黒い石製・EMERGENCY STOP | 車両を安全停止 |

1 周後は駅で停止します。降車は `Shift` です。乗り続けると駅の待機後に再発車しますので、降りるときは帰還後の停車中に操作してください。再度乗るときは駅で降車してから木製ボタンを押します。座席は 2 席ありますが、複数人同時乗車の実機試験は別項目です。

途中で停止操作や途中退出を行った場合、安全のため自動で駅へワープして戻しません。手動の緊急停止後は、通常の自動発車や再読込で緊急停止を解除しません。途中の緊急停止からの復旧は管理者向け操作が必要です。通常の体験は駅から出発し、駅へ帰還してから降車してください。

これらの操作の実機検証範囲と走行・再読込の結果は [テスト結果](../TEST_RESULTS.md) に記載します。

専用BGM3曲を同梱し、乗車中に再生します。警告音、金属音、煙などの演出は Minecraft の音量設定に従います。クラウドの音声出力は実際に聞けないため、聞こえ方は未検証です。Windows 実機の動作速度・FPS も未検証です。

保存後の再開には実際の物理保持が安定するまでの待機が必要です。駅で停止・降車してから保存してください。新しい制御は実シングルプレイで完走を確認しました。

## 4. 不具合を確認する場合

- ワールドが一覧に出ない場合は、ワールドフォルダ直下の `level.dat` を確認します。
- MOD 読込エラーでは、Minecraft / NeoForge / Java と 4 本の JAR の版を確認します。Forge と NeoForge は別のローダーです。
- 車両がない場合は `sublevels` を含めて展開したか確認し、元の ZIP から別名で再展開してください。
- 同梱依存を二重に入れたり、ScalableLux、Rubidium、Embeddium、Celeritas などの非互換 MOD を追加したりしないでください。詳細は [MOD 一覧](MOD_LIST.md) にあります。

旧 VOLCANIC ABYSS のワールドへ上書きせず、別フォルダで導入してください。この鉱山ワールドは旧ワールドを変換しません。
