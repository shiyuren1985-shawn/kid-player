# 项目协作

- 在已授权范围内自主完成常规执行；用户要求讨论或只读时不修改。事实、推测、未验证结果分开表述。
- 当前开发交接入口为 `docs/handoff/MAC-MINI.md`。接收 Mac Mini 后单端写入，保留用户已有更改。
- 图片生成、打开、视觉检查必须委派隔离子 Agent，仅返回文字结论与文件路径；主线程不加载或展示图片，除非用户明确要求当前主对话展示指定图片。
- 读取其他 Codex 任务仅取文字摘要，includeOutputs=false，不读取原始历史图片载荷。
- 构建不等于实际播放验收。仪器测试只能在专用 AVD 运行，使用恢复偏好/网络的包装脚本，不测试家用正式设备。
- GitHub 仓库必须私人；不要提交密钥、真实设备偏好或签名私钥。App 更新目标是个人域名和 Cloudflare，GitHub 仅用于私人源码和交接归档。

- 显示名称为精确小写 `kid player`，仓库与新产物 slug 为 `kid-player`。保持 `applicationId=family.kidcinema`、存储键及签名兼容性；历史 ZIP/APK/QA 原名原 SHA 保留。
- Mini 源码位于 `/Volumes/ZHITAI/AI/KidPlayer/kid-player`，新增环境和缓存默认位于相邻外置盘 `runtime`；先 source tools/env.sh，不提交本机凭据和签名文件。
- 用户已授权应用内更新和本机经 Cloudflare 分发。当前清单 `https://kid-player.shiyu.ren/kid-player/update.json`，服务仅监听127.0.0.1:18897，公开目录在外置盘独立 update-server/public，不能暴露源码或凭据。发布前完成相关验证，版本号递增、签名不变、APK先到位再原子切换清单；不卸载清数据冒充升级。未来可迁移HTTPS更新源，不能在APK里放私人GitHub token。
