#!/usr/bin/env bash
set -euo pipefail
APEX_PROJECT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APEX_WORKSPACE="$(dirname "$APEX_PROJECT")"
APEX_TOOL_ROOT="$APEX_WORKSPACE/.tools"
mkdir -p "$APEX_TOOL_ROOT"
if [[ ! -x "$APEX_TOOL_ROOT/jdk-17.0.12/bin/javac" ]]; then
    curl --fail --location --retry 2 --output "$APEX_TOOL_ROOT/jdk17.tar.gz" \
        https://download.oracle.com/java/17/archive/jdk-17.0.12_linux-x64_bin.tar.gz
    printf '%s  %s\n' 311f1448312ecab391fe2a1b2ac140d6e1c7aea6fbf08416b466a58874f2b40f "$APEX_TOOL_ROOT/jdk17.tar.gz" | sha256sum --check --strict
    tar -xzf "$APEX_TOOL_ROOT/jdk17.tar.gz" -C "$APEX_TOOL_ROOT"
fi
source "$APEX_PROJECT/scripts/environment.sh"
python3 - <<'PY'
import os, pathlib, re, urllib.parse
path = pathlib.Path(os.environ['GRADLE_USER_HOME']) / 'gradle.properties'
path.parent.mkdir(parents=True, exist_ok=True)
existing = path.read_text() if path.exists() else ''
existing = re.sub(r'(?ms)^# BEGIN APEX CLOUD\n.*?^# END APEX CLOUD\n?', '', existing)
lines = ['# BEGIN APEX CLOUD']
proxy = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
if proxy:
    parsed = urllib.parse.urlsplit(proxy)
    if not parsed.hostname:
        raise SystemExit('HTTPS proxy has no hostname')
    if parsed.username or parsed.password:
        raise SystemExit('Authenticated Java proxy needs supported environment credentials; credentials are not copied to Gradle configuration')
    for protocol in ('http', 'https'):
        lines += [f'systemProp.{protocol}.proxyHost={parsed.hostname}',
                  f'systemProp.{protocol}.proxyPort={parsed.port or 80}']
if pathlib.Path('/etc/ssl/certs/java/cacerts').is_file():
    lines.append('systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts')
lines.append('# END APEX CLOUD')
path.write_text(existing.rstrip() + '\n' + '\n'.join(lines) + '\n')
PY
cd "$APEX_PROJECT"
if [[ "${1:-}" == "--with-display" ]]; then
    bash "$APEX_PROJECT/scripts/setup-display.sh"
fi
java -version
./gradlew build
