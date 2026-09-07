# kid player Mac Mini 接收回执

首次源码/环境接收：2026-09-07T11:19:31+08:00。归档补齐及开发交接闭环：2026-09-07T11:43:40+08:00，Asia/Shanghai。

开发交接状态：ACCEPTED。源码、构建环境和完整历史归档均已接收并验证。此状态仅表示开发交接完成，不代表签名迁移、家庭设备播放或自动更新已验收。此前历史归档缺失导致的 PARTIAL 已解除。

## 主机、路径和源码

- 主机：Yuren的Mac mini，arm64；macOS 27.0（26A5416b）。
- 任务标题：kid player 开发（Mac Mini）；本机可见任务及全部归档任务未发现同项目名称冲突。
- 挂载外置盘：`/Volumes/ZHITAI`，预检可用约 866 GiB，安装后约 857 GiB。
- 任务绑定目录：`/Volumes/ZHITAI/AI/KidPlayer`；源码：`/Volumes/ZHITAI/AI/KidPlayer/kid-player`。
- origin：`https://github.com/shiyuren1985-shawn/kid-player.git`；GitHub API 确认 `private=true`、默认分支 main。
- clone 基线 HEAD：`6e21db28a5a77612581a7ab9d39a4bc368aeb837`，与交接消息要求一致，接收时工作区干净。
- 已验证并推送的更名/环境提交：`6f62d8e2ebfcf964d5c7a8bfae93c53d3103459f`。补齐归档前 HEAD 仍为此提交，工作区干净。本次仅修改此回执；回执提交用 `git log -1 --format=%H -- docs/handoff/RECEIPT-MAC-MINI.md` 定位，推送后独立读回远端 HEAD 核对。
- GitHub CLI 已安装，版本 2.98.0；用户完成设备授权，API 与 clone 已验证。没有复制 Air 凭据或签名私钥。

## 历史归档接收（PASS）

- 私人 prerelease：`https://github.com/shiyuren1985-shawn/kid-player/releases/tag/handoff-20260907-kid-player`；实际 API 核对 `draft=false`、`prerelease=true`，3 个资产均为 `uploaded`。
- 实际 ZIP 路径：`/Volumes/ZHITAI/AI/KidPlayer/historical-handoffs/handoff-20260907-kid-player/kid-player-handoff-20260907.zip`。
- ZIP 实际大小：19,729,336 字节；实际 SHA256：`a095909d7127e6b3599952a3ce77d8933495efeb5d78e3fd9a9dd3e10f34fa4d`，与交接预期、GitHub 服务端 digest 和 SHA256SUMS 一致。
- Release 附件清单：同目录 `MANIFEST.json`；实际 SHA256：`0070cf58b332f9ec9b815e97dc5dff26aefeba99bf38499466ef7391b2300dde`。
- 独立解压目录：同目录 `extracted/`；包内 `extracted/MANIFEST.json` 与 Release 附件清单 SHA256 一致。
- 执行包内 `extracted/verify-handoff.py`：`Verified 95 files; source commit c3442f4bcc5c301ca054e6dc91c3d243dbec8f7f`；95 个文件存在性、大小及 SHA256 全部 PASS。
- 另执行 `git bundle verify`：`sisi-cinema.bundle is okay`，包含完整历史；未向开发仓库导入 refs 或检出旧源码。
- ZIP 仅外层文件名更名，内部旧名称、旧基线 `c3442f4` 和历史 APK 保留原样，作为历史证据。历史 `KidPlayer/` 源码独立保存，未覆盖当前 `6f62d8e` 开发仓库。
- 此前 Releases 为空、旧标签 `handoff-20260907` 返回 404 的缺项已由上述新归档解决。未重新要求用户提供归档。

## 环境和验证

以下为已完成的 Mini 验证记录；本次仅接收历史归档，未重复构建、单元/仪器测试或视觉检查。环境详情与复现命令见 `MINI-ENVIRONMENT.md`。JDK 17.0.20.1、Gradle 8.11.1、Build Tools 35.0.0、Platform Tools 37.0.1、Android Platform35、Emulator37.1.11 和 API31 ARM64 镜像均在外置盘；复用系统 Python3.9.6，impacket0.13.1 虚拟环境在项目内。未安装 Android Studio。

- `./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`：PASS。
- 单元测试：13 项，0 失败、0 错误、0 跳过。
- Lint：0 错误、32 警告；仍有现有警告，不声称零警告。
- 专用 AVD：`KidCinema_Tablet_12`，`emulator-5554`，Android12/API31 ARM64，2560×1600、320 dpi。
- 包装回归：`python3 tools/test-emulator.py --class family.kidcinema.PrototypeTest,family.kidcinema.KidPlayerUiTest,family.kidcinema.KidPlayerStoreTest`，18 项 PASS，78.541 秒；未开启 `--live`。
- 包装脚本恢复记录：`preferences_exactly_restored_before_launch=true`，`fixture_stopped=true`。
- 壳脚本语法、Python 包装脚本语法、Git diff 空白检查：PASS。
- 隔离视觉复验：PASS。检查平板、电视模式 2560×1600，以及 600dp 窄屏/字体 1.3 的首页与设置新名称；无截断或重叠。检查后恢复自动模式、原尺寸和默认字体 1.0。电视横幅旧文字已替换，其他 launcher 图标无旧品牌文字。
- 真实家庭 SMB、MatePad、TCL、真实设备升级、线上播放与自动更新：NOT TESTED。本次没有连接或修改家庭设备。

本机详细日志位于 `qa/kid-player-handoff` 和 `qa/kid-player-0.5.0/run-20260907-111448`；QA 临时文件和偏好备份不上传仓库。

## 新产物与签名

- 显示名称及 APK label：精确小写 `kid player`。
- applicationId：`family.kidcinema`，versionName：`0.5.0-easy-player`，versionCode：6；包身份、存储实现与键未变更。
- 导出 APK：`output/releases/kid-player-0.5.0-easy-player-debug.apk`。
- SHA256：`7ecabb02a816f7a0e0fe8b6e25158225f6f97eefac89f30f265984e870a89362`。
- APK 签名校验 PASS；Mini 新调试签名证书 SHA256：`489567f04c62e842cede6b0497e690efcddc6c7f97abe44a2d9b29f48a07f140`。
- 这是 Mini 新调试签名，不能覆盖 Air 的旧签名安装；未卸载旧包或清除家庭设备数据。签名私钥只留本机外置盘，不上传、不复制。

## 完成范围与待办

更名覆盖程序标题/无障碍文字、电视横幅、启动脚本、配置示例、当前文档、相关 UI 测试定位、新导出名称；环境脚本移除 Air 绝对路径。旧启动脚本由新名称入口取代；专用 AVD 的安全身份名称保留。

未来约定 `/kid-player/update.json`、`/kid-player/releases/kid-player-<version>.apk`、`/kid-player/creators.json`，真实域名仍待用户提供。未实现自动更新、操作 Cloudflare/DNS、发布 APK 或公开文件。GitHub 仅 PRIVATE 源码和交接归档。

历史归档及清单已完整收到并验证，无资料接收阻塞。后续由 Mini 单端继续开发，Air 仅保留备份。剩余独立待办：Air 旧签名与 Mini 新签名的兼容迁移、真实家庭设备/SMB/在线播放验收、用户确认实际域名后另行授权的自动更新实现与部署；这些不属于归档缺失。
