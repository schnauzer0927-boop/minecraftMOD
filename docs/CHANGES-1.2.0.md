# Apex Predator 1.2.0 の変更

Minecraft 1.20.1 / Forge 47.4.10 / Java 17 向けの外観改修です。今回添付されたコンセプトアートを参照し、Apex の形状と装甲・発光テクスチャを作り直しました。作業は `feature/apex-appearance-1.2.0` で行い、1.1.0 は `feature/apex-visuals-1.1.0` に保持しています。

## 外観

細く尖ったヘルメット、V 字形の橙色の目、縦に細い胸部コア、肋骨状に重なる胸部装甲、長い前腕ブレード、後方へ流れる背面フィン、逆関節脚と鋭い爪で、参照画像の細身の捕食者らしい輪郭に近づけました。黒い金属装甲の面・端・継ぎ目を描き分け、発光を目・コア・関節・刃の溝などに限定しています。

| モデル項目 | 1.2.0 |
| --- | --- |
| 高さ | 2.7 ブロック（Y=0〜43.2 モデルピクセル） |
| ボーン | 28 |
| キューブ / 四角形 | 290 / 1,740 |
| 装甲・発光アトラス | 各 512×512 |
| 発光マスクの有効ピクセル | 964 |

28 ボーンの名前・親子関係・ピボット・初期回転を維持しました。既存の Java、適応 AI、ダメージ保護、TaCZ 連携、攻撃・フェーズ・演出・SE・設定・判定時刻は変更していません。37 アニメーションもそのまま使用します。アニメーション JSON の SHA-256 は次の値で 1.1.0 と一致しています。

```text
0218e1e3c8ecaa6dea5cc8bf6913b2a29eeda67c2c5f422df9fab975a9d21744
```

## 変更ファイル

モデル素材は `src/main/resources/assets/apexpredator/` 配下です。

| 分類 | ファイル |
| --- | --- |
| 形状 | `geo/apex.geo.json` |
| 装甲・発光 | `textures/entity/apex.png`, `textures/entity/apex_glow.png` |
| モデル制作元・説明 | `scripts/art/generate_apex_model.py`, `scripts/art/README.md` |
| バージョン | `gradle.properties`, `src/main/resources/META-INF/mods.toml` |
| 導入・検証・配布説明 | `README.md`, `VALIDATION.md`, `dist/README.md`, `docs/CHANGES-1.2.0.md` |
| 1.1.0 の記録保存 | `docs/VALIDATION-1.1.0.md`, `docs/CHANGES-1.1.0.md`（過去の検証記録へのリンク調整） |
| 配布物・チェックサム | `dist/apexpredator-1.20.1-1.2.0.jar`, `dist/apexpredator-1.20.1-1.2.0.jar.sha256` |
| 正面・側面・背面・四方向図 | `docs/visuals/apex-1.2-front.png`, `docs/visuals/apex-1.2-side.png`, `docs/visuals/apex-1.2-back.png`, `docs/visuals/apex-1.2-turnaround.png` |
| ゲーム内表示 | `docs/visuals/apex-1.2-ingame.png` |

素材の比較には [正面](visuals/apex-1.2-front.png)、[側面](visuals/apex-1.2-side.png)、[背面](visuals/apex-1.2-back.png)、[四方向図](visuals/apex-1.2-turnaround.png)、[ゲーム内表示](visuals/apex-1.2-ingame.png) を使用できます。

## 導入・検証

成果物は `build/libs/apexpredator-1.20.1-1.2.0.jar` です。引き続き **GeckoLib 4.4.9 の Forge 1.20.1 版**が必要で、TaCZ は任意です。Apex と GeckoLib をクライアント・サーバー双方の `mods` に配置し、旧 Apex jar と入れ替えてください。

1.2.0 の素材で JUnit **22 件**、TaCZ なしの専用サーバー GameTest **22 件**、TaCZ 1.1.8-hotfix2 ありの GameTest **24 件**が成功しています。TaCZ なしの実クライアントでモデル・テクスチャ・37 クリップ、実ダメージによる Phase 2 / 3、死亡と Apex Core、実モデル残像を確認し、世界保存と正常終了も確認しました。

TaCZ ありの実クライアントでも連携イベント登録、モデル・発光・BossBar、コンボの姿勢、衝撃波と粒子、世界保存と正常終了を確認しました。最後の `./gradlew build` は正常終了し、1.2.0 jar を生成しました。73 個の保護対象ファイルも 1.1.0 とバイト単位で一致しています。詳細なログと jar のハッシュは [1.2.0 検証記録](VALIDATION-1.2.0.md) に記録しています。

姿勢確認は抽出したフレームによる検証です。FPS ベンチマークと実銃による手動バランステストは、この外観改修の検証には含みません。
