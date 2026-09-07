# kid player 应用更新

当前开发版本 0.7.2 / versionCode 13。默认更新清单：

`https://kid-player.shiyu.ren/kid-player/update.json`

## 平板使用

首次手动安装带更新功能的 APK 后，首页在有网络时自动检查；后台由 WorkManager 按约 15 分钟周期申请检查，系统省电、离线、强行停止会延后，不保证即时推送。首页发现新版本提示一次，后台在允许通知时发系统通知；播放页不弹更新框。

“播放设置 → 检查应用更新”可立即手动检查。查看版本说明，点击“下载更新”；可取消、失败后重试。下载完成后点击“安装更新”，首次按系统页面允许 kid player 安装应用，再确认安装。Android 13 及以上若需要后台通知，可在更新页点击“允许更新通知”。可关闭自动检查。

这里的自动更新是自动发现和提示，下载与安装由用户发起；本版不进行无人确认安装。Android 的部分自更新场景可以请求免确认，但仍须处理系统确认、OEM 限制和权限差异，本次不依赖该能力。

同包名、同签名、递增 versionCode 的覆盖升级保留应用数据。本机调试签名和 Air 原调试签名不同，不能直接覆盖已装的 Air 版本；不得以卸载或清数据替代签名迁移。首次接入长期设备前应确认其现有签名。当前开发发布继续使用 Mini 现有调试签名，不能丢失或重新生成该密钥。

## 电脑发布

1. 完成修改，在 `app/build.gradle` 增加默认 `versionCode`（当前13，下一次至少14）并更新 `versionName`。
2. 运行相关回归；涉及画面必须隔离视觉检查。
3. 发布已完成版本：

```bash
bash tools/publish-update.sh --notes '本次更新说明'
```

脚本先构建并执行单元测试、lint，然后验证 APK 签名和身份，复制版本化 APK 至独立公开目录，最后原子替换 `update.json`。拒绝降版本、同 versionCode 替换不同字节及改变签名，避免平板拿到半个文件或错包。已有 APK 永不覆盖。发布前后的清单和 SHA 应留在相应本机 QA 记录。UI 改动的仪器/视觉验证须另外完成，发布脚本不会替代这些检查。

默认目录：`/Volumes/ZHITAI/AI/KidPlayer/update-server/public/kid-player/`。当前下载路径为 `/kid-player/releases/kid-player-0.7.2-13.apk`。`tools/package.sh` 仅导出本地 APK，不发布清单。

## 服务与来源迁移

`kid-player.shiyu.ren → Cloudflare Named Tunnel → 127.0.0.1:18897`，服务是 `tools/update-server.py`，只开放清单和版本化 APK，不提供目录索引或源码。清单返回 `Cache-Control: no-store`，APK 返回一年 immutable 缓存和 `nosniff`。更新期间电脑、外置盘和 Tunnel 必须在线；电脑关机时平板现有版本仍可用，更新检查失败可重试。

手动前台启动：

```bash
bash tools/start-update-server.sh
```

自动启动部署状态见本次验证记录。源码、APK、日志、下载与缓存均在外置盘；系统启动项如需内置盘，由用户另行同意。

未来可将相同清单和 APK 放到其它 HTTPS 服务。在更新页“更新源设置”修改完整清单地址即可；也可保留当前域名并迁移后端，让已装设备无须改配置。允许 HTTPS 重定向，每一跳拒绝降级到 HTTP。GitHub 未来可用公开分发仓库/公开资产或受控代理；当前私人源码仓库的下载需要认证，不能直接把私有 Release 地址当作匿名更新源，也不得把 GitHub token 放进 APK 或公开清单。

UP 主名单仍是独立配置；当前更新服务不承载 `/kid-player/creators.json`。

本轮公网观察：Android Dalvik 清单请求成功；Python 默认 User-Agent 会被 Cloudflare Browser Integrity Check 返回403/1010，属于调试客户端限制，本次未为它关闭站点保护。命令行读回使用 curl。公网偶有超时，应用支持重试；首个未缓存 APK 下载可能较慢。

## 校验与失败处理

清单限定 schema、包名、正向版本、最低 Android 版本、下载大小和 SHA256；下载限制256 MiB，连接/读取和总耗时均有上限。先写内部私有临时文件，成功后验证大小、完整 SHA256、APK 包名、精确 versionCode、minSdk 和当前安装签名。通过后才生成 FileProvider 私有下载目录的只读 URI，交给系统安装器。安装前再校验一次；系统安装器最终验证 APK 签名。失败不修改安装，不清应用数据。

下载不跨进程自动续传；中途退出可重新下载，已校验完整包可在下次进入时继续安装。密钥轮换暂不支持，严格要求当前签名集合一致。撤回版本应停止发布清单或发布更高版本修复包，不做降级安装。

## 官方依据

- [Android 应用签名与升级身份](https://developer.android.com/studio/publish/app-signing)：包升级必须保持兼容签名，丢失原密钥会影响后续更新。
- [Android 安装来源授权](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())：安装前检查来源许可并使用系统授权页。
- [PackageInstaller 用户确认条件](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int))：不能跨 Android/OEM 无条件承诺静默安装。
- [Android FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)：使用 content URI 和临时读授权共享私有文件。
- [Google Play In-app Updates](https://developer.android.com/guide/playcore/in-app-updates)：面向 Play 分发，本项目使用独立 HTTPS 清单及系统安装。
- [Cloudflare Tunnel 路由](https://developers.cloudflare.com/tunnel/routing/)：固定公开主机名映射本机服务，无须开放家庭路由器入站端口。
