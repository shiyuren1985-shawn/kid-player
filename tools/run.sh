#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$PROJECT_DIR/tools/env.sh"
cd "$PROJECT_DIR"
mkdir -p output
if ! adb devices | awk '$1 == "emulator-5554" && $2 == "device" { found=1 } END { exit !found }'; then
    nohup "$ANDROID_HOME/emulator/emulator" -avd KidCinema_Tablet_12 -port 5554 -no-boot-anim -gpu host > "$PROJECT_DIR/output/emulator.log" 2>&1 < /dev/null &
fi
for attempt in {1..90}; do
    if [ "$(adb -s emulator-5554 get-state 2>/dev/null || true)" = device ]; then break; fi
    sleep 1
done
if [ "$(adb -s emulator-5554 get-state 2>/dev/null || true)" != device ]; then
    echo '模拟器未能连接。请查看 output/emulator.log；如果 macOS 询问终端访问文稿文件夹，请允许本项目访问后重试。'
    exit 1
fi
AVD_NAME="$(adb -s emulator-5554 emu avd name | head -1 | tr -d '\r')"
if [ "$AVD_NAME" != "KidCinema_Tablet_12" ]; then
    echo '5554 端口被其他模拟器占用；未安装应用。请关闭该模拟器后重试。'
    exit 1
fi
for attempt in {1..90}; do
    if [ "$(adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then break; fi
    sleep 1
done
if [ "$(adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" != 1 ]; then
    echo '模拟器尚未完成启动，请查看 output/emulator.log 后重试。'
    exit 1
fi
if [ "${1:-}" = "--build" ] || [ ! -f app/build/outputs/apk/debug/app-debug.apk ]; then
    ./gradlew assembleDebug
fi
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n family.kidcinema/.MainActivity
echo '小小影院已在模拟器中打开。'
