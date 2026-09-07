# 2026-09-08 更新服务 502 修复

## 已确认原因

公网 update.json 返回 Cloudflare HTTP 502；Mac 本机 127.0.0.1:18897 连接被拒绝，无监听进程。此前更新 HTTP 服务是临时进程，没有对应 LaunchAgent。当前证据能确认源站进程已退出，不能确定原进程退出的具体触发原因。本次不修改 APK、Tunnel 或 Cloudflare 防护。

## 修复

安装单一用户 LaunchAgent `ren.shiyu.kid-player.update-server`，RunAtLoad/KeepAlive 开启。macOS launchd 对外置盘工作目录/日志报 EX_CONFIG，随后对外置盘脚本报 Operation not permitted，因此把最小运行脚本与公开发布镜像放入本机 Application Support；开发源码、原始 APK 仍在外置盘。日志位于用户 Library/Logs/KidPlayer。

发布脚本自动同步已安装服务：共用发布锁，校验 APK 大小和 SHA256，先保存不可变 APK，再原子替换清单。只复制指定公开文件，保留服务已有旧版 APK。

## 验证

- PASS：8 项更新服务器/发布/镜像测试，包括路径隔离、头信息、符号链接越界、不可变发布、镜像幂等以及损坏 APK 不替换清单。
- PASS：本机清单恢复；公网 Android Dalvik UA 清单成功，仍发布 0.7.3 / code 14，与外置盘原发布清单完全一致。
- PASS：向专属服务发送 SIGTERM 后，launchd 运行次数从 1 变为 2，PID 从 48895 变为 49197，进程自动恢复为 running。不是只验证配置已加载。
- PASS：公网完整下载 13,353,413 字节 APK，SHA256 为 395339ca2366301fa99f94ddf10103b857e3e13bda05705175abe30080f92ef1，与原发布文件完全一致。首次 60 秒诊断超时，延长到 300 秒上限后成功；0.7.1 应用本身允许最多 600 秒总下载时间。
- PASS：公网私有路径 /.git/config 返回 404。核对 0.7.1 源码，更新源与修复后的同一域名一致。
- 未操作用户平板，未执行家庭平板安装或 Mac 重启验证。服务在用户登录会话运行，Mac/Tunnel 离线时仍无法检查更新。

证据目录：qa/update-service-20260908。
