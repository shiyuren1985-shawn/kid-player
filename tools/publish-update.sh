#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$PROJECT_DIR/tools/env.sh"
cd "$PROJECT_DIR"
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 tools/publish-update.py --apk app/build/outputs/apk/debug/app-debug.apk "$@"
