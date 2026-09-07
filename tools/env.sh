#!/bin/bash
# Source before building; generated state defaults to the adjacent external runtime.
if [ -n "${ZSH_VERSION:-}" ]; then
    eval 'KID_PLAYER_ENV_FILE=${(%):-%x}'
else
    KID_PLAYER_ENV_FILE="${BASH_SOURCE[0]}"
fi
KID_PLAYER_PROJECT="$(cd "$(dirname "$KID_PLAYER_ENV_FILE")/.." && pwd)"
export KID_PLAYER_RUNTIME="${KID_PLAYER_RUNTIME:-$(dirname "$KID_PLAYER_PROJECT")/runtime}"
export JAVA_HOME="${JAVA_HOME:-$KID_PLAYER_RUNTIME/jdk17/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$KID_PLAYER_RUNTIME/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$KID_PLAYER_RUNTIME/android-user}"
export ANDROID_EMULATOR_HOME="${ANDROID_EMULATOR_HOME:-$ANDROID_USER_HOME}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$KID_PLAYER_RUNTIME/avd}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$KID_PLAYER_RUNTIME/gradle}"
export TMPDIR="$KID_PLAYER_RUNTIME/tmp/"
export PIP_CACHE_DIR="$KID_PLAYER_RUNTIME/pip-cache"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME" "$GRADLE_USER_HOME" "$TMPDIR" "$PIP_CACHE_DIR"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/16.0/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
