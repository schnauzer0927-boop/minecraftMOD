#!/usr/bin/env bash
# Source this file before Gradle commands in the cloud environment.
APEX_WORKSPACE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ -d "$APEX_WORKSPACE/.tools/jdk-17.0.12" ]]; then
    export JAVA_HOME="$APEX_WORKSPACE/.tools/jdk-17.0.12"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$APEX_WORKSPACE/.gradle}"
