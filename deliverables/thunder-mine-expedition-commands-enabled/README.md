# ゲームモード変更を許可した鉱山ワールド

元の配布ワールドの Data.allowCommands だけを有効にしました。初期モードはアドベンチャーです。ワールドを開き、チャットに `/gamemode creative` を入力するとクリエイティブに、`/gamemode survival` でサバイバルに変更できます。変更用にLAN公開する必要はありません。

すでに遊んでいる元のワールドでは、Esc → LANに公開 → コマンド（チート）の許可をオン → LANワールドを開始し、上のコマンドを入力してください。このLAN設定は開いている間だけ有効です。今の進行を保つならこちらの方法を使い、新しいZIPで上書きする必要はありません。

新しく始める場合はZIPを展開してsavesへ入れてください。必要構成は元の配布と同じ Minecraft 1.21.1 / NeoForge 21.1.228 / Java 21 と4本のMODです。

[導入手順](https://github.com/schnauzer0927-boop/minecraftMOD/blob/9066a3cb0ef4491faae9aaefea089f028c2e094e/deliverables/thunder-mine-expedition-1.0.0/docs/INSTALL.md)

実際のNBT値とZIPのCRCを検査し、他の全ワールドファイルが元のZIPと一致することを確認しました。この修正版の実ゲームでのコマンド入力は未検証です。元の2周完走の記録と途中保存・再開の制限は既存テスト結果を参照してください。

[既存テスト結果](https://github.com/schnauzer0927-boop/minecraftMOD/blob/9066a3cb0ef4491faae9aaefea089f028c2e094e/deliverables/thunder-mine-expedition-1.0.0/TEST_RESULTS.md)
