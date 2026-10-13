# 調査用資料

`docs/sources.md` を出典整理の正本とする。出典ID `S-GH-TDL-01` は実際にHTTP取得して閲覧した写真。

配布対象は取得結果・独自メモ・URL・画像メタデータ。以下はローカル分析用であり、配布ZIPから除外する。

- `source_images/`: 再配布許諾未確認の写真。
- `official/`: 取得した外部原文 (現在は公式本文未取得)。
- `local_analysis/`: 非公式リポジトリから取得したHTMLやREADME原文。

`fetch_log.json` の CONNECT403 は原サイトの応答ではなくプロキシ接続制限。成功証拠と数えない。`source_image_metadata/` は画像を含まない出典記録であり配布できる。
