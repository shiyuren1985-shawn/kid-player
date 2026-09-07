# 0.6.1 缓存和入口验证

2026-09-07，Mac Mini，专用 Android 12 平板 AVD。包名 family.kidcinema，versionCode 10。

- PASS：assembleDebug、assembleDebugAndroidTest、25 项 JVM 测试（新增 6 项磁盘缓存测试），Lint 0 错误、38 警告，与上一版本相同。
- PASS：磁盘缓存跨实例及旧时间戳复用；新 URL 增量下载；8 个并发请求只有一次获取；坏缓存修复；非法、超大及失败响应不污染已有内容；写入后容量限制及最近读取保留。
- PASS：14 项仪器测试（KidPlayerUiTest 8、BiliCollectionsTest 6），47.116 秒。包含管理入口直接开名单且作者选择不变、模拟 loading 时缓存卡片仍在且刷新禁用、取消后进度消失、目录新增导入、失败保留旧目录、所有者校验及分页完整性。
- PASS：包装脚本恢复测试前偏好和系统设置。证据 qa/kid-player-tests/run-20260907-134200，构建和测试摘要 qa/cache-0.6.1。
- 边界：上述目录网络响应使用测试数据；loading UI 测试人为控制状态。未把它们当作真实 B 站联网更新、实际平板安装或视频下载的证明。
- 本次未修改视频播放链路或更新安装器，不重复上一版下载/安装全流程验收。

- PASS：隔离视觉检查：首页管理入口、最终名单说明、模拟 loading 转圈与保留卡片，无重叠或截断。最终文案定向 2 项测试再次通过，偏好恢复 true，证据 qa/kid-player-tests/run-20260907-134520；视觉报告 qa/cache-0.6.1/visual-review.md。
- PASS：最终文案构建成功及 Lint 0 错误、38 警告。发布脚本验证同一签名，APK 就位后原子切换清单；公网清单与本地发布 JSON 完全一致，APK HEAD 返回 HTTP 200、正确大小和 immutable 缓存头。此项未重复下载整个公网 APK。

已发布 0.6.1 / versionCode 10：
- 清单：https://kid-player.shiyu.ren/kid-player/update.json
- APK：https://kid-player.shiyu.ren/kid-player/releases/kid-player-0.6.1-10.apk
- 大小：13,433,270 字节。
- APK SHA256：f89e54f1b785fa701b3dcb31996ae18c6395426c83ed07c864721bee01899e0e。
- 签名证书 SHA256：489567f04c62e842cede6b0497e690efcddc6c7f97abe44a2d9b29f48a07f140。

