# 思思影院：Mac Mini 开发交接

交接日期：2026-09-07。来源：Shawn MacBook Air，项目 `/Users/shawn/AI/KidPlayer`。接收目标：用户 24 小时运行的 Mac Mini 上的 Codex。

## 用户最新决定

- 全部程序上传私人 GitHub 仓库，作为源码备份及开发协作入口。
- 后续开发在 Mac Mini 继续。接收方完成核验并写回执后，Air 停止修改代码，避免双端同时开发。
- App 在线更新仍通过用户个人域名和 Cloudflare 路径。不要改成 GitHub Releases 作为设备更新源：用户明确指出中国网络访问 GitHub 不稳定。
- 更新功能仅讨论了方案，尚未实现或部署。域名下实际更新 URL 尚未提供，不自行猜测其他项目域名或复用其他服务配置。

## 当前代码和产品

私人仓库：`https://github.com/shiyuren1985-shawn/sisi-cinema`。当前交接提交：`c3442f4bcc5c301ca054e6dc91c3d243dbec8f7f`。此前产品代码提交：`3cc4e718f6831d64727c31b02b819a0363f56d0f`。版本 `0.5.0-easy-player`，versionCode 6，包名 `family.kidcinema`。

名称思思影院，奶油白/粉/淡紫。视频卡片直接播放；72dp 常驻返回、播放/暂停、收藏、关闭影院；播放完重放和最多四部同 UP 主视频，手动选择下一部。作者名单默认通过用户 HTTPS JSON 文件管理，本机模式独立可选；点头像切换作者。原有本地演示、只读 SMB、收藏、观看进度保留，无 PIN。

初始指定 UP 主为 UID 402576555。在线目录仍限制为作者公开合集最新 30 条，并不保证全部投稿。没有账号登录或付费视频支持。更新功能和 UP 名单是两个文件/用途，不混淆。

## 已验证和限制

- 13 项单元测试通过；Lint 0 错误、32 警告。
- 49 项完整仪器测试：47 通过，2 项在线首播超时；失败项在同一当时 APK 上分别实播重测通过，首播偶发超时根因未确认。
- 最后触屏转遥控器焦点修复后，6 项定向回归全部通过（38.544 秒）。Android 首次方向键离开触摸模式会吞键，已通过监听触摸模式切换把焦点送到底部；同真实 adb input 20/23/22 流程有失败→通过证据。
- 最终隔离视觉复验通过：暂停后底部播放紫框保持，右键移至收藏，中央控制隐藏。
- 最终 APK SHA-256：`1f6263c6c843df51fbd1da3ab702fb620cc17dd2f9a557c8658cd83b6f996d79`。
- 仅专用 Android 12/API31 ARM64 模拟器验收；MatePad、TCL、真实家庭 SMB、HDR/字幕等尚未实机验收。

## 接收与环境

1. 克隆私人仓库，确认 HEAD 为 `c3442f4bcc5c301ca054e6dc91c3d243dbec8f7f`。不要覆盖 Mini 上可能存在的旧项目；如有旧目录先比较提交和未提交文件。
2. 解压配套 handoff ZIP 到单独目录，按 MANIFEST.json 验证文件 SHA。其中包含 git bundle、当前 APK、测试资料和交接文档。未包含真实设备偏好备份、个人凭据、签名私钥、SDK/JDK/Gradle 缓存和虚拟环境。
3. Mini 使用自己的 JDK17、Android SDK（compileSdk35/build-tools35），检查 `tools/env.sh`、`gradle.properties` 和 `tools/run.sh` 中 Air 路径；调整本机路径后再构建。Mac Mini 用户目录未核实，不可直接假定为 /Users/shawn。
4. `./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`。SMB 测试需要在 Mini 重建 `tools/.venv` 并安装 impacket。只在专用 KidCinema_Tablet_12 AVD 跑包装测试 `python3 tools/test-emulator.py`；`--live` 才允许访问真实 B 站。不得在家用正式设备清偏好跑仪器测试。
5. 不把构建成功写成播放或实机通过。读取 QA 报告时只取文字；图片生成/打开/检查必须交给隔离子 Agent，不向主线程传图片载荷。
6. 写 `RECEIPT-MAC-MINI.md`：主机、项目路径、HEAD、ZIP SHA、清单验证、构建结果、签名状态、遗留项和接收时间。只有实际验证后才写 ACCEPTED；签名/硬件待办仍单独列出。

## 签名衔接

当前交付是 Air 调试签名。私钥没有上传 GitHub，也没有混入普通交接包。Mini 默认生成的新调试签名不能直接覆盖 Air 旧包。接续工作必须先决定安全转移原签名，或建立长期 release 签名并明确迁移；不能用卸载旧包导致收藏/进度丢失来假装无缝升级。签名私钥只走用户控制的独立传输渠道。

## 下一步更新功能

推荐固定 HTTPS `/sisi/update.json` 与版本化 `releases/*.apk`。清单含 versionCode/versionName、APK URL、大小、SHA256、更新说明。启动自动检查（限频）、设置页手动检查、不打断播放；下载进度/取消/失败重试、文件及包身份签名校验、系统安装确认。先部署 APK 再发布清单，Cloudflare 清单不缓存，APK 文件名版本化。首次须手动安装一次带更新模块的兼容签名版本，之后 App 内更新。

用户将继续在 Mini Codex 指示开发；本次交接接收任务只验证环境与交接，不自动部署域名、发布公开文件或开始未确认的新功能。
