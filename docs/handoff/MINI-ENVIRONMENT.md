# kid player Mini 构建环境

外置盘源码目录 `/Volumes/ZHITAI/AI/KidPlayer/kid-player`；运行 `source tools/env.sh`，默认所有新增构建状态在相邻 `/Volumes/ZHITAI/AI/KidPlayer/runtime`。可通过环境变量覆盖路径，不修改 shell 全局配置。

本次使用：

- Azul Zulu JDK 17.0.20.1+1，macOS aarch64；`runtime/jdk17` 指向解压目录，Java 位于 `Contents/Home`。
- Android command-line tools 16.0，安装于 `android-sdk/cmdline-tools/16.0`，兼容本项目 JDK17。
- Platform Tools 37.0.1、Build Tools 35.0.0、Android Platform 35 revision 2。
- Emulator 37.1.11；Android 12/API31 `google_apis;arm64-v8a` revision 11。
- Gradle 8.11.1，使用仓库 Wrapper；缓存位于 `runtime/gradle`。
- 复用 Mini 的 `/usr/bin/python3` 3.9.6；未另在内置盘安装 Python。项目 `tools/.venv` 和 pip 缓存均在外置盘，impacket 0.13.1。

JDK 下载来自 Azul 官方 CDN，SDK 来自 Google 官方仓库；原始下载校验记录保留在本机 `qa/kid-player-handoff/environment-download-sha256.txt`。JDK TAR SHA256 为 `ed54397260c5994b2fc4e31c780f480b947e6532bc6a5ac7754355d5dd4c127d`；command-line tools ZIP SHA256 为 `da181a8683e2f0599cc561c04468919ff0e229318187ad7c68fe0f9b7925e936`，其 SHA1 与 Google repository 元数据一致。

下载 URL：

- `https://cdn.azul.com/zulu/bin/zulu17.68.203-ca-jdk17.0.20.1-macosx_aarch64.tar.gz`
- `https://dl.google.com/android/repository/commandlinetools-mac-12266719_latest.zip`

配置环境后安装 SDK 与专用 AVD（创建前检查同名 AVD，不加覆盖参数）：

```bash
source tools/env.sh
sdkmanager --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' 'emulator' 'system-images;android-31;google_apis;arm64-v8a'
avdmanager list avd
avdmanager create avd -n KidCinema_Tablet_12 -k 'system-images;android-31;google_apis;arm64-v8a' -d medium_tablet -p "$ANDROID_AVD_HOME/KidCinema_Tablet_12.avd"
python3 -m venv tools/.venv
tools/.venv/bin/python -m pip install -r tools/requirements.txt
```

SDK 包命令解析当时官方可用版本；重新安装须记录实际版本。本次 AVD 使用 2560×1600、320 dpi。自动验证使用无窗口模拟器及软件 GPU，交互启动脚本保留有窗口方式。Android Studio 未安装，命令行构建无需它。

Android 用户状态与新调试签名位于 `runtime/android-user`，AVD 位于 `runtime/avd`。不上传或复制签名私钥，不把该签名视为 Air 的旧调试签名。GitHub CLI 复用外置盘已安装版本 2.98.0，认证配置独立存于源码之外；不把凭据纳入 Git。
