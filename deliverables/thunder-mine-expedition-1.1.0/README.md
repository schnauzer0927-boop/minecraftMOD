# Thunder Mine Expedition 1.1.0

Minecraftの既存Create・Sable・Create: Coasters Simulatedを利用した、オリジナルの鉱山アトラクションです。添付3JARは改変していません。独立した **thunder-mine-tools-1.1.0.jar が発車・坂道駆動・ブレーキ・演出・BGMを担当する必須MOD**です。

## 必要な環境

Minecraft Java Edition 1.21.1 / NeoForge 21.1.228 / Java 21。modsフォルダに次の4本を入れます。

```text
create-1.21.1-6.0.10.jar
sable-neoforge-1.21.1-2.0.6.jar
simulatedcoasters-0.1.5.jar
thunder-mine-tools-1.1.0.jar
```

旧thunder-mine-tools-1.0.0/1.0.1.jarはmods外へ移し、補助MODは1本だけにします。Coaster EngineeringのForge1.20.1環境とは別のプロファイルを使います。第三者MOD本体は配布ZIPへ同梱しません。

## 変更点

- 補助MODを必須と明記し、modsとsavesをまとめた導入セットを追加。
- ワールド名を変更しても、保存済みコースの指紋と車両UUIDで制御。
- 停止中は実際のSable物理拘束で車両を保持し、発車時に解除。
- 条件を満たす駅からずれた車両を、灰色STARTで低速帰還。ワープ・コース再生成・完走判定の偽装なし。
- 探索・坑道・疾走のオリジナルBGM3曲。乗客側でMUSICカテゴリ・距離減衰なし、曲間フェードと降車後のフェード停止。
- 坑道・峡谷を加速、導入を短縮。大洞窟の低速と駅への減速を維持。コース形状とJSON指紋は変更なし。

## 走行結果

実シングルプレイで乗客を乗せて、約1035.60ブロックを171.425秒で完走、駅で正常停止。最高実速度16.465ブロック/秒（59.27km/h相当）。従来189.275秒・48.5km/h相当より高速化。BGM3曲の実再生・切り替え・降車後の停止を確認。詳しくは[テスト結果](TEST_RESULTS.md)。音の聞こえ方とWindows実機のFPSは未検証です。

## 操作

木製BOARDを右クリックして乗車し、待機後に自動発車。灰色STARTは手動発車・条件を満たす車両の駅帰還、黒色は緊急停止。駅で停車したらShiftで降車します。乗り続ける場合は約10秒後に再発車します。

`/mine_world status` はチートなしで補助MODの版・車両・運転状態を確認できます。これが不明なコマンドなら、使用中のゲームフォルダに補助JARがあるか、MOD一覧でThunder Mine Expedition Tools 1.1.0を確認してください。

[導入手順](docs/INSTALL.md) / [更新・復旧](docs/PATCH_1.0.1.md) / [BGM設定・曲の交換](docs/BGM_GUIDE.md) / [必要MODと正規配布先](docs/MOD_LIST.md)

音量はconfig/thunder-mine-tools-client.tomlで変更できます。速度はconfig/thunder_mine_tools-common.tomlのride項目。enhancedSpeed=falseで元の速度、thrillFactor=1.15で控えめにできます。実曲率・加減速・駅の安全制限は残ります。

## ソース

独自補助MODとコース設計・建築・音楽生成・検証スクリプトはSource.zipへ収録。Java21、Gradle9.2.1でビルドします。指定3JARをinput-modsへ用意し、python3 scripts/extract-compile-libs.pyの後、helper/gradlew -p helper build。BGMは独自の生成スコアで作成し、元のMODのアセット・コードを同梱しません。旧火山ワールドと2.0成果物は未変更です。
