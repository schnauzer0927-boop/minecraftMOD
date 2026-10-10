# 音量0.45版の保存記録

コミット `38c1eeb9aeb513277386194666b9b1901d6d3f62` 時点の記録です。[現在の検証](../VALIDATION.md)と区別してください。

# Apex Predator 1.3.0 — BGM音量更新の検証

2026-10-10（JST）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17で実施しました。基準は初回BGM版 `6d63a2cda546546382e8ee19304e37fad626a688`、バージョンは1.3.0のままです。

## 変更

ユーザーの「音量もっと上げてほしい」に対応し、ボスBGMの既定音量を **0.22 → 0.45** へ上げました。音声信号の振幅は約2.05倍、約+6.2 dBです。マスター・RECORDSカテゴリの音量調整と消音は維持します。

実行コードの変更は `src/main/java/dev/apexpredator/client/effects/ApexBossMusic.java` の音量とデバッグ表示の2行だけです。音源、ループ、再生・停止条件、ピッチ、戦闘AI、Phase 3回復、TaCZ、モデル・37アニメーションは変更していません。README、BGM説明、配布説明も更新しました。

## 今回実行した検証

- `./gradlew build` **成功**、終了コード0、21秒。
- 既存JUnit **64件すべて成功**。失敗・エラー・スキップ0。今回のbuildで実行し、テストの追加・変更はありません。
- 配布jarの逆アセンブルで、実際の音量代入が `0.45F` であることを確認。
- 初回BGM版jarと全163エントリを比較し、**変更はApexBossMusic.classだけ**。他162エントリ、全音資源・全既存クラス・モデル・アニメーションはバイト単位で一致。
- `build/libs` と `dist` のjarが完全一致。

今回は音量定数だけの変更のため、GameTest・Dedicated Server・runClientの全曲ループ試験は再実行していません。[初回BGM版の実クライアント・Dedicated Server検証](VALIDATION-1.3.0-music.md)を保存しています。旧記録の実ゲイン・jarハッシュは音量0.22版のものです。

OGG SHA-256: `7f6f9e433df7e55e88242e0e950addb572792e7c333acdaf8055df0cc560bfe4`（音源そのものは変更なし）。

## 配布

- `build/libs/apexpredator-1.20.1-1.3.0.jar`
- `dist/apexpredator-1.20.1-1.3.0.jar`
- `dist/apexpredator-1.20.1-1.3.0.jar.sha256`

サイズ: **4,267,477 bytes**。

SHA-256: `0aae93c3d350e910fb03a64abec86f7ff17fc18f4157847e640aa201e451f025`

GeckoLib 4.4.9 Forge版が必要、TaCZは任意です。通信プロトコルは既存BGM版と同じ3で、今回の音量変更では同期・サーバー仕様を変更していません。

ビルドログ・比較記録・逆アセンブルはignoredの `build/music-volume/build.log`、`audit.json`、`javap.txt` にあります。[BGM仕様](BOSS-MUSIC.md)と[導入方法](../dist/README.md)も参照してください。
