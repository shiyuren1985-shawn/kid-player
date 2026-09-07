# kid player 缓存策略与官方依据

调研日期：2026-09-07。缓存容量是本应用的保守默认值，不是标准规定。

| 数据 | 本应用执行方式 | 失效与边界 |
| --- | --- | --- |
| 全部投稿、合集 | UI 读取本机已保存目录；逐页网络读取、逐页持久保存，记录已读数/总数/完整状态 | 读取完成后才以新完整快照替换；中途失败保留旧条目和新读取内容。全部投稿按接口总数读取，不截断成 30 条 |
| 头像、封面 | 12 MiB 内存 + 24 MB 磁盘缓存；同 URL 并发去重；磁盘缓存复用跨重启；按最近读取淘汰 | 资料/投稿接口带回新 URL 后增量获取；图片解码通过才落盘。内存压力时释放内存；相同 URL 原地换图可通过缓存管理清理后获取 |
| 视频片段 | 单例 Media3 SimpleCache + CacheDataSource + LRU，最多 256 MiB | 只缓存实际播放读取过的字节；小于 512 MiB 可用空间时不新建缓存；缓存异常交由网络播放路径处理。不是全量预下载或离线下载功能 |
| 播放权限 | 每次开始播放先核对当前启用作者、目录与平台详情，再创建读取缓存的媒体源 | 不因缓存而跳过作者/付费/公开状态核验；无法核验时不承诺离线播放 |
| 收藏、观看进度、名单 | 保留现有独立持久存储键 | 图片/视频缓存清理不触碰这些记录，也不删除目录 |
| APK 更新清单 | 每次联网检查，服务器清单 no-store、版本化 APK immutable，安装前验证 SHA/签名/身份 | 与目录缓存和播放缓存独立 |

视频缓存沿用完整、已验证的播放 URL 作为键；音视频流和不同签名 URL 不共用字节。这样不会将重新编码或不同资源的片段混合，但平台刷新签名链接后可能需要重新缓存。没有擅自删除签名参数来制造跨链接命中。

目录每次刷新重新核对分页，避免只靠“条数不变”判断内容未变而漏掉改标题/删除并新增。图片、已经播放的片段和未变化的合集成员可独立复用。没有把 HTTP 412 或接口拒绝当作空目录成功，也没有加入密集重试。

## 官方资料及采用范围

- [Android：构建离线优先应用](https://developer.android.com/topic/architecture/data-layer/offline-first)：本机数据作为 UI 读取来源、网络结果先持久化、区分可重试网络错误和未经授权请求。应用采用这些数据流原则；当前目录存储仍是本机 JSON，不声称已迁移 Room。
- [Glide：Caching](https://bumptech.github.io/glide/doc/caching.html)：多级缓存、内容变化时改变资源标识、LRU、内存压力释放。应用保留已验证的轻量图片实现，采用这些机制，并未引入 Glide 依赖。
- [Android Media3：Caching media](https://developer.android.com/media/media3/exoplayer/network-stacks#caching-media)：使用 SimpleCache、CacheDataSource 和有限容量淘汰，缓存播放读取的字节。
- [Media3：CacheKeyFactory](https://developer.android.com/reference/androidx/media3/datasource/cache/CacheKeyFactory)：缓存键识别资源，不能随读取区间变化。应用使用默认 URI 键，Range/seek 共享同一资源缓存。
- [RFC 9111：HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111.html)：区分 HTTP 新鲜度、校验和 no-store。版本化 APK 与不缓存更新清单按服务器策略分开；图片/Media3 是应用资源缓存，不宣称自写实现是完整 RFC 9111 HTTP 缓存。
