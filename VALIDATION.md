# Apex Predator 1.4.0 — バージョン更新の検証

2026-10-10（JST）、Minecraft 1.20.1 / Forge 47.4.10 / Java 17で実施しました。基準は直前の配布版 `af2c1ce475ea63b511780b1b69d3d2a384af423a` です。

## 変更

ユーザーの依頼に従い、Gradleの `mod_version` と `META-INF/mods.toml` のバージョンを **1.3.0 → 1.4.0** へ更新しました。jarのファイル名・ManifestのImplementation-Version・ゲーム内MOD情報が1.4.0になります。READMEと配布案内も現行jarへ更新しました。

音量0.85のボスBGM、戦闘中の通常MUSIC抑止、Phase 3回復、戦闘AI、TaCZ任意連携、モデル・37アニメーションは維持します。通信プロトコルは3のままです。旧jarは以前の配布記録として保持しました。

## 今回の検証

- `./gradlew build` **成功**、終了コード0、17秒。
- JUnit **72件すべて成功**。今回のbuildで実行し、失敗・エラー・スキップ0。
- 配布jar内部の `mods.toml` とManifestが **1.4.0** であることを確認。
- 旧jarと全166エントリを比較し、変更は `META-INF/mods.toml` と `META-INF/MANIFEST.MF` だけ。他164エントリ、全89クラス、音源・モデル・アニメーション等の61資源がバイト単位で一致。
- jarのCRC正常・重複エントリなし。`build/libs` と `dist` のjarは完全一致。

バージョン情報だけの変更のため、今回GameTest・runClient・TaCZ試験は再実行していません。[直前の実クライアント・Dedicated Server・BGM検証](docs/VALIDATION-1.3.0-music-priority.md)を保存しています。旧検証のjar名とハッシュは1.3.0版のものです。

## 配布

- `build/libs/apexpredator-1.20.1-1.4.0.jar`
- `dist/apexpredator-1.20.1-1.4.0.jar`
- `dist/apexpredator-1.20.1-1.4.0.jar.sha256`

サイズ: **4,270,954 bytes**。

SHA-256: `0bfa49ca2cd379c949cbeb6c954287582e5b2e1b240c6f07e1fc0d7dd8b084fa`

GeckoLib 4.4.9 Forge版が必要、TaCZは任意です。旧Apex jarを取り除き、新しいjarをmodsフォルダへ配置します。

ビルド・jar比較ログはignoredの `build/version140/build.log` と `jar-audit.json` に保存しています。[BGM仕様](docs/BOSS-MUSIC.md)と[導入方法](dist/README.md)を参照してください。
