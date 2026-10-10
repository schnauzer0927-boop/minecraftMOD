Apex本体とApex FangをBlockbenchで直接編集できる正式プロジェクトを追加し、Predator Dashを2秒クールダウンにした配布版です。Minecraft 1.20.1 / Forge 47.4.10 / Java 17向け。

**Minecraftへ入れるファイルはAssetsの `apexpredator-1.20.1-1.5.1.jar` です。** クライアント・サーバー両方の `mods` フォルダーへ配置してください。[GeckoLib 4.4.9 Forge版](https://cdn.modrinth.com/data/8BmcQJ2H/versions/RBA7lJaW/geckolib-forge-1.20.1-4.4.9.jar)が必要、TaCZは任意です。旧Apex jarと重複させないでください。

- Apex本体: `models/blockbench/apex.bbmodel`。GeckoLib Animated Model / Entity、28ボーン・333キューブ・37アニメーション、通常/発光PNGを埋め込み。
- Apex Fang: `models/blockbench/apex_fang.bbmodel`。Java Block/Item、56キューブ・18グループ、埋め込みPNG、左右の手持ち・GUI・地面の表示設定。
- `apexpredator-blockbench-1.5.1.zip`に編集プロジェクト・PNG・専用書き出しツール・手順をまとめています。MODを再ビルドする場合は[1.5.1ソース一式](https://github.com/schnauzer0927-boop/minecraftMOD/archive/refs/tags/v1.5.1.zip)も取得してください。
- Predator Dash: 初期クールダウン40tick（2秒）。`apexFangDashCooldownTicks`で調整可能。
- Apex Slash: 独立した200tick（10秒）を維持。通常時はDash、スニーク中はSlashの標準ホットバー表示へ切り替えます。
- 既存AI・Phase 3回復・Beacon・Core・BGM・エフェクト・TaCZ連携・37アニメーション・武器の攻撃性能を維持。

[編集手順](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.5.1/models/blockbench/README.md) / [検証記録](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.5.1/VALIDATION.md)

検証: `./gradlew build`成功、JUnit 82件・Python 20件・Dedicated GameTest 88件（TaCZあり91件）がすべて成功。Blockbench 5.2.2で実際に開いて編集・保存・再出力し、TaCZあり・なし両方の`runClient`で能力・標準クールダウン・左右手持ち・召喚・BGMを確認しました。

| Assets | サイズ | SHA-256 |
| --- | ---: | --- |
| `apexpredator-1.20.1-1.5.1.jar` | 4,389,041 bytes | `1a22ecad06799cd8daed8f5dddaafa216bd26bf71dcbf3640abaadad2da7d4f9` |
| `apexpredator-blockbench-1.5.1.zip` | 641,877 bytes | `b8954c37f12e6451776d5b71d4edb82684154a70aa08e4ba4bf122266c8833b6` |
