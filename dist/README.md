# Apex Predator ダウンロード

最新の配布ファイルは **[apexpredator-1.20.1-1.3.0.jar](apexpredator-1.20.1-1.3.0.jar)** です。GitHub 上でファイルを選び、**Download raw file**（または **Raw**）から保存してください。アーカイブのプレビューは不要です。

Minecraft **1.20.1**、Forge **47.4.10**、Java **17** を使用します。次の **2 個の jar** をクライアント・サーバー双方の `mods` フォルダーへ配置してください。

1. `apexpredator-1.20.1-1.3.0.jar`
2. **[GeckoLib 4.4.9 / Forge 1.20.1](https://cdn.modrinth.com/data/8BmcQJ2H/versions/RBA7lJaW/geckolib-forge-1.20.1-4.4.9.jar)**

GeckoLib は 1.3.0 の必須依存です。TaCZ は任意です。旧 Apex Predator jar は取り除き、Apex の複数バージョンを同時に入れないでください。

現在の1.3.0 jarには [Phase 3自己回復](../docs/PHASE3-HEALING.md) と、提供曲 **The Stalking Motif** の[ボス戦BGM](../docs/BOSS-MUSIC.md)を追加しています。最新jarのBGM既定音量は0.85です（前回の0.45からさらに増量）。交戦中は通常BGMのMUSICカテゴリを抑止し、戦闘終了後に通常の再生スケジュールへ戻します。RECORDSカテゴリでボスBGMを調整できます。マルチプレイでは両側をこの追加版へ更新してください。更新前1.3.0との混在は通信プロトコル3の確認で拒否します。

Forge で起動し、`/summon apexpredator` で召喚できます。`apexpredator-1.20.1-1.3.0.jar.sha256` に配布 jar の SHA-256 を記載しています。

旧 **[1.2.0](apexpredator-1.20.1-1.2.0.jar)**・**[1.1.0](apexpredator-1.20.1-1.1.0.jar)**・**[1.0.0](apexpredator-1.20.1-1.0.0.jar)** と対応するハッシュも保持しています。1.0.0 の仮モデルには GeckoLib は不要です。

[変更内容・ファイル一覧](../docs/CHANGES-1.3.0.md)・[検証記録](../VALIDATION.md)・[頭部比較](../docs/visuals/apex-1.2-vs-1.3-head-comparison.png)・[実ゲームのモデル](../docs/visuals/apex-1.3-ingame.png)
