Apex本体を、今回のHUNTER設計図の正面・側面・背面・詳細図に合わせて全面再構築した版です。MODバージョンはユーザー指定の **1.15.2**、Minecraftは引き続き **1.20.1 / Forge 47.4.10 / Java 17** です。

**Minecraftへ入れるのはAssetsの `apexpredator-1.20.1-1.15.2.jar` です。** クライアントとサーバー双方の `mods` フォルダーへ配置してください。[GeckoLib 4.4.9 Forge版](https://cdn.modrinth.com/data/8BmcQJ2H/versions/RBA7lJaW/geckolib-forge-1.20.1-4.4.9.jar)が必要、TaCZは任意です。以前のApex jarを取り除いてください。

- 頭部・全身体型・胸腹部・肩・複合前腕刃・背部フィン・逆関節脚の形状と装甲を作り直しました。
- 黒い内部機構、ガンメタルの装甲、銀色の面とエッジ、細いオレンジ発光を使用しました。
- 正式な編集元は `models/blockbench/apex.bbmodel`。PNGを埋め込んだGeckoLib Entityモデルです。保存した編集元から専用CLIでgeometryとテクスチャを出力できます。
- 498キューブ・28ボーン、1024×1024の通常・発光・回復用PNGを収録。既存ボーン名と親子関係を維持し、pivotは18か所調整しました。
- 37アニメーションの名称・全時刻・戦闘タイミングを維持。突進準備と突進の胸・首X回転だけ16値を低姿勢へ補正し、残り35クリップは変更していません。
- 既存AI・Phase 3回復・攻撃判定・BossBar・BGM・TaCZ連携・Beacon/Core・Fangの性能を維持しています。Dashは2秒、Slashは10秒のままです。
- `apexpredator-blockbench-1.15.2.zip`は編集用です。MODの再ビルドには[1.15.2ソース一式](https://github.com/schnauzer0927-boop/minecraftMOD/archive/refs/tags/v1.15.2.zip)も取得してください。

[編集手順](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.15.2/models/blockbench/README.md) / [設計図との一致基準](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.15.2/docs/REFERENCE-DESIGN-1.15.2.md) / [検証記録](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.15.2/VALIDATION.md)

検証: JUnit84件、Python28件、Dedicated Server88件、TaCZあり91件がすべて成功。`./gradlew build`も成功しました。実Blockbenchで編集・Paint・書き出しを確認し、実Minecraftの`runClient`はTaCZなし・ありの両方でモデルと19クリップ名、通常AI・フェーズ・回復・BGMを確認して正常終了しました。

jarは4,703,599 bytes、SHA-256 `91dfd4dd7352a76c75bb3115f6efee778cf221f69c91822a9911e9dfe5e10042`。編集用ZIPは3,402,207 bytes、SHA-256 `30f3e1fa75f211627da2b2e7f6279f1efde0bce3c3d746cb3e9d736f1a86ece3`。

[正面](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.15.2/docs/visuals/apex-reference-match-front.png) / [側面](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.15.2/docs/visuals/apex-reference-match-side.png) / [背面](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.15.2/docs/visuals/apex-reference-match-back.png) / [頭部](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.15.2/docs/visuals/apex-reference-match-head.png) / [実Minecraft](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.15.2/docs/visuals/apex-reference-match-ingame.png) / [旧版比較](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.15.2/docs/visuals/apex-reference-match-before-after.png)
