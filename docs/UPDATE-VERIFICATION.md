# 0.6.0 更新功能验证记录

日期：2026-09-07，Mac Mini。产品 versionCode9，包名 family.kidcinema。

## 已验证

- Android Debug APK 和 instrumentation APK 构建：PASS。
- JVM 单元测试：19项 PASS（原13项，新增6项更新规则）。
- Lint：0错误，38警告。较原32警告增加6项，涉及SharedPreferences同步写入、存储空间建议和字符串国际化，不作为零警告交付。
- Python 服务/发布测试：6项 PASS；覆盖路径与符号链接隔离、HEAD、缓存头、原子发布、重复发布一致性、降版本拒绝、HTTPS源要求。
- 额外真实签名负例：Air 历史 APK 被发布工具以签名不一致拒绝，现有清单不变。
- 公网 DNS/HTTPS/Tunnel：域名任务验证 kid-player.shiyu.ren 独立 ingress 到127.0.0.1:18897，Tunnel配置有效，HTTP2连接存在。
- 清单：Android Dalvik UA HTTP200；schema、versionCode9、文件大小和SHA与本地一致，no-store/DYNAMIC/nosniff。
- APK：HEAD200，长度13,331,151，类型为APK，immutable缓存；Cloudflare边缘Range请求返回206。
- 路径隔离：根路径、目录、源码/.git、备份清单及越界路径均404；未提供公开上传接口。

## APK

URL：`https://kid-player.shiyu.ren/kid-player/releases/kid-player-0.6.0-9.apk`

SHA256：`87346de2b9155c029c39ef77f5c2343ecdf29c0bec0001598d710c58ee6dd80a`

签名证书SHA256：`489567f04c62e842cede6b0497e690efcddc6c7f97abe44a2d9b29f48a07f140`（Mini现有调试签名）。本地构建、导出和公开文件字节一致。

## 专用模拟器验收

AVD：KidCinema_Tablet_12，Android12/API31 ARM64。更新起点是同签名versionCode7测试引导包，目标versionCode9；使用实际公网清单和APK。未连接家庭设备。

- 仪器测试：9项 PASS（UpdateTest7项+PrototypeTest2项），43.657秒。覆盖错误哈希、清单/APK版本不一致、包/URL/大小规则、安装来源权限、私有Provider，以及原有播放/收藏邻接回归。
- 包装脚本恢复：全部原偏好哈希恢复一致，临时SMB服务已停止；本次日志 `qa/kid-player-0.5.0/run-20260907-122437`（旧日志目录名保留为实际证据，之后使用通用kid-player-tests目录）。
- 更新提示、手动检查、下载取消/重试：隔离子Agent已确认PASS。
- 实际系统安装：PASS。由应用发起来源授权，在Android系统安装器点击Update，从7升级到9；未以adb install替代、未卸载或清数据。
- 数据保留：收藏标记和正观看进度与升级前逐键相等，四个布尔验证结果全true。
- 最终视觉与最新状态：PASS。更新后页面显示「当前已是可用的最新版本」，版本0.6.0；文本/控件可读无截断。仅专用AVD安装来源授权被开启。

## 当前边界

- 有一次清单超时，手动重试成功；本次12.7MiB公网APK下载约8分钟，验收不能代表家庭网络稳定性。
- Python默认UA被Cloudflare BIC返回403/1010，源站同UA正常；Android Dalvik和浏览器UA正常。本次未为辅助验证器增加安全规则例外。
- 旧微信客服、voice入口恢复后可访问；openclaw入口有一次独立超时，网络持续稳定性为PARTIAL，不能写持续无抖动。
- 本机更新服务正在运行；自动启动所需内置盘小plist尚待用户存储许可，未配置为重启后自动恢复。手动方式为 `bash tools/start-update-server.sh`。
- 未验收Huawei/TCL真实设备、Android13通知权限界面、OEM后台调度、Air→Mini签名迁移或真正的无人确认更新。本版有系统安装确认。

详细本机日志与隔离视觉报告位于 `qa/update-0.6.0`，不提交图片和设备偏好。
