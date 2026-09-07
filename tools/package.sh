#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$PROJECT_DIR/tools/env.sh"
cd "$PROJECT_DIR"
./gradlew assembleDebug
VERSION=$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle)
test -n "$VERSION"
mkdir -p output/releases
DEST="output/releases/kid-player-${VERSION}-debug.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$DEST"
shasum -a 256 "$DEST" > "$DEST.sha256"
echo "kid player 调试产物：$DEST"
echo '此脚本不发布文件。覆盖旧包前必须确认签名一致；不得卸载清数据替代升级。'
