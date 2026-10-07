#!/usr/bin/env bash
set -euo pipefail
APEX_DISPLAY_ROOT=/workspace/.tools/virtual-display
export DISPLAY=:97
export XAUTHORITY="$APEX_DISPLAY_ROOT/.Xauthority"
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_SHADER_CACHE_DIR="$APEX_DISPLAY_ROOT/mesa-cache"
mkdir -p "$MESA_SHADER_CACHE_DIR"
if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
    printf 'Virtual display %s is ready.\n' "$DISPLAY"
    exit 0
fi
touch "$XAUTHORITY"
chmod 600 "$XAUTHORITY"
python3 - <<'PY'
import secrets
import subprocess
subprocess.run(['xauth', '-f', '/workspace/.tools/virtual-display/.Xauthority',
                'add', ':97', '.', secrets.token_hex(16)], check=True)
PY
nohup "$APEX_DISPLAY_ROOT/root/usr/bin/Xvfb" "$DISPLAY" -screen 0 1280x720x24 \
    +extension GLX +render -noreset -nolisten tcp -auth "$XAUTHORITY" \
    >"$APEX_DISPLAY_ROOT/xvfb.log" 2>&1 &
printf '%s\n' "$!" >"$APEX_DISPLAY_ROOT/xvfb.pid"
for APEX_READY_TRY in {1..50}; do
    if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
        printf 'Virtual display %s is ready.\n' "$DISPLAY"
        "$APEX_DISPLAY_ROOT/root/usr/bin/glxinfo" -B
        exit 0
    fi
    sleep 0.1
done
cat "$APEX_DISPLAY_ROOT/xvfb.log" >&2
exit 1
