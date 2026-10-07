#!/usr/bin/env bash
set -euo pipefail
APEX_DISPLAY_ROOT=/workspace/.tools/virtual-display
mkdir -p "$APEX_DISPLAY_ROOT/apt/lists/partial" "$APEX_DISPLAY_ROOT/apt/cache/archives/partial" \
    "$APEX_DISPLAY_ROOT/apt/parts" "$APEX_DISPLAY_ROOT/deb" "$APEX_DISPLAY_ROOT/root"
cat >"$APEX_DISPLAY_ROOT/apt/sources.list" <<'SOURCES'
deb [signed-by=/usr/share/keyrings/debian-archive-keyring.gpg] https://deb.debian.org/debian trixie main
deb [signed-by=/usr/share/keyrings/debian-archive-keyring.gpg] https://deb.debian.org/debian-security trixie-security main
deb [signed-by=/usr/share/keyrings/debian-archive-keyring.gpg] https://deb.debian.org/debian trixie-updates main
SOURCES
cat >"$APEX_DISPLAY_ROOT/apt/apt.conf" <<'CONFIG'
Dir::Etc::main "-";
Dir::Etc::parts "/workspace/.tools/virtual-display/apt/parts";
Dir::Etc::sourcelist "/workspace/.tools/virtual-display/apt/sources.list";
Dir::Etc::sourceparts "/workspace/.tools/virtual-display/apt/parts";
Dir::State::lists "/workspace/.tools/virtual-display/apt/lists";
Dir::State::status "/var/lib/dpkg/status";
Dir::Cache::archives "/workspace/.tools/virtual-display/apt/cache/archives";
APT::Get::List-Cleanup "0";
Acquire::Retries "2";
CONFIG
export APT_CONFIG="$APEX_DISPLAY_ROOT/apt/apt.conf"
/usr/bin/apt-get update
cd "$APEX_DISPLAY_ROOT/deb"
/usr/bin/apt-get download xvfb mesa-utils mesa-utils-bin
for APEX_PACKAGE in ./*.deb; do
    dpkg-deb -x "$APEX_PACKAGE" "$APEX_DISPLAY_ROOT/root"
done
