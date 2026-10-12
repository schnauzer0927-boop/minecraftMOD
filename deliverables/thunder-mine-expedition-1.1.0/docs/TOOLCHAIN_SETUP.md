# Java 21とNeoForge開発環境の準備記録

添付MODはMinecraft 1.21.1 / NeoForge 21.1.228以上 / Java 21を要求する。以前のForge 1.20.1プロジェクトのJava 17環境を変更せず、Java 21のJDKを別のディレクトリへ展開した。

## このクラウドで使うJDK

```sh
export JAVA_HOME=/workspace/.tools/thunder-mine-java21/extracted/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
```

OpenJDK **21.0.12.1+1-1~deb13u1**。JREのみの既存環境とは別に、`javac`、JDKモジュールとヘッドレスJREを含む。

`scripts/setup-java21-debian.sh`で再現できる。Debian 13公式リポジトリのJDK/JREパッケージをHTTPSで取得し、ローカルディレクトリへ`dpkg-deb --extract`する。システムへのパッケージインストールは行わない。

- Debianのインストール済み鍵束`/usr/share/keyrings/debian-archive-keyring.pgp`でInReleaseの署名を確認。
- `AllowUnauthenticated=false`、`AllowInsecureRepositories=false`。
- HTTPSの証明書・ホスト検証を有効のまま使用。
- 取得後、3つのInReleaseを`/usr/bin/sqv`でも個別に検証。
- 2つの`.deb`のSHA-256を認証済みAPT Packagesインデックスと照合。
- `javac --release 21`でコンパイルし、クラスmajor65を確認。そのクラスを新JDKのJavaで実行して成功。

証拠は`toolchain/java21-provenance.json`、`evidence/java21/`、`evidence/java21-debian-setup.log`に格納する。JDKや125MBのDebianパッケージを完成ワールドZIPに同梱しない。

## 取得した公式MDK

GitHub上の公式`NeoForgeMDKs`から、取得時のコミットを固定してソースアーカイブを取得した。ZIP CRCを確認し、テンプレートライセンス`TEMPLATE_LICENSE.txt`を保持した。取得自体はビルド成功を意味しない。

| 公式テンプレート | コミット | ビルドプラグイン | Gradle | テンプレートのNeoForge |
|---|---|---|---|---|
| MDK-1.21.1-ModDevGradle | `3bca8f1e0756c001e4b71f266c328d1568d29363` | ModDevGradle 2.0.148 | 9.2.1 | 21.1.259 |
| MDK-1.21.1-NeoGradle | `50b02952c596784d7c493866650021c1222846ca` | NeoGradle 7.1.38 | 9.2.1 | 21.1.235 |

両テンプレートのNeoForgeは添付MODの共通最小要件21.1.228を満たす。MDKのテンプレート設定はワールド補助MODの最終環境ピンとは区別する。実際に採用したNeoForge版をテスト結果と導入手順に記載する。

両テンプレートはParchmentのMinecraft 1.21.1 / mapping 2024.11.17を指定している。必要なマッピングが取得できない場合は、補助MODのビルド設定で標準Mojangマッピングを使用することを検討できる。第三者MODの添付JARはそのまま使用する。

取得物とURL・サイズ・SHA-256は`toolchain/official-mdk/provenance.json`に記録した。クラウドの通信設定で許可されていないMavenホストを別プロキシ経由で取得する処理は行っていない。

公式MDKを取得できても、NeoForge本体とビルドライブラリの取得先`maven.neoforged.net`への接続が拒否される場合、NeoForgeのビルド・起動には環境のネットワーク許可設定が必要になる。JavaだけでMODの正常動作を判定しない。

この作業では後に通信設定が復旧し、公式MavenからNeoForge21.1.228インストーラーと必要ライブラリを取得できた。独立した製品版サーバーのインストール記録は[NATIVE_PRODUCTION_SERVER.md](NATIVE_PRODUCTION_SERVER.md)を参照。初期の取得制限と、その後の成功を区別して記録している。
