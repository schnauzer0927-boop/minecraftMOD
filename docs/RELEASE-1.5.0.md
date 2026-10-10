Apex Beaconの5秒召喚儀式とApex Fangを追加した、Minecraft 1.20.1 / Forge 47.4.10 / Java 17向けの配布版です。

**使うファイルはAssetsの `apexpredator-1.20.1-1.5.0.jar` です。** クライアント・サーバー双方の `mods` フォルダーへ入れてください。旧Apex Predator jarは取り除きます。[GeckoLib 4.4.9 Forge版](https://cdn.modrinth.com/data/8BmcQJ2H/versions/RBA7lJaW/geckolib-forge-1.20.1-4.4.9.jar)が必須で、TaCZは任意です。

- Apex Beacon: 地面の上面を右クリックし、100tickで召喚。Survivalは成功時のみ1個消費、Creativeは不消費。
- Apex Fang: 合計攻撃力16・攻撃速度1.6・耐久3000。右クリックで7ブロックのPredator Dash、スニーク＋右クリックで9ブロックのApex Slash。両技は10秒クールダウンを共有し、壁・崖で停止します。
- 既存戦闘AI、ボスモデル、37アニメーション、Phase 3回復、TaCZ連携、BossBar、音量0.85の専用BGMを維持。

[レシピと操作方法](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.5.0/docs/PROGRESSION.md) / [検証記録](https://github.com/schnauzer0927-boop/minecraftMOD/blob/v1.5.0/VALIDATION.md)

`./gradlew build`成功。JUnit78件、TaCZなしDedicated GameTest86件、TaCZあり89件がすべて通過。実クライアントの操作検証も実施しています。

jarサイズ: 4,381,770 bytes

SHA-256: `04613fa9fb8fb346966c0f93cfc641775e44581d31c718dae45e1fca13232399`
