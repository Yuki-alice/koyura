# 功能补齐交付报告

2026-11-20 · `feat/functional-core` · 续轮基线 `2c1e4bb`

复验时将 `-PveneraReferenceDir` 指向主工作区的 `reference/Venera-Next`。本轮沿用仓库依赖与版本基线。

## 本轮交付

| 范围 | 生产行为 |
| --- | --- |
| 共用后台更新 | 系统唤醒和进程内计时共用一次到期检查入口及执行锁。后台优先复用前台会话；冷启动打开一次无界面会话，用完释放。扫描结果和下一次期限落盘后才发送新章通知。 |
| iOS | 启动委托注册 `BGProcessingTask`。偏好或下一次期限变化时更新系统请求；过期取消时保留后续请求。系统决定实际执行时刻。 [Apple 后台任务](https://developer.apple.com/documentation/backgroundtasks) |
| 移动端网络与通知 | Android 用 ConnectivityManager，iOS 用 Network.framework。恢复符合条件的网络时立即重判逾期。仅 Wi-Fi 偏好按非计量网络执行，iOS 同时排除低数据模式。使用 Android 通知渠道和 iOS 本地通知，通知授权在用户启用定时更新时请求。 |
| 下载队列 | 现有队列页改用持久任务。进入队列才订阅实时进度，普通刷新保留当前队列。 |
| 固定来源 | 浏览页现有固定动作写入同一个 `browse.discovery` 偏好记录，冷启动按 SQLite 快照恢复固定状态。 |
| 阅读计时 | 以单调时钟累加阅读器可见且应用已恢复前台的时间。在进度保存、切换章节、离开阅读器或切后台时落盘。首次打开可读章节时保存初始位置，使停留在首页的阅读也可累计。 |
| 外观 | 应用外壳根据持久设置应用跟随系统、浅色、深色和 AMOLED。保存成功后即时生效。 |

## 验收

- 定向调度及生产装配测试通过，覆盖冷启动恢复、两个唤醒只扫一次、落盘后发一次新章通知、队列、来源固定、设置和阅读时长。
- Android APK、桌面 classes 和 iOS 模拟器 framework 的 Gradle 联合构建 EXIT 0。Android 调试 APK 合并了网络状态和通知权限。iOS Xcode 应用构建 EXIT 0，产物 plist 含 `app.koyura.library.refresh` 与 `processing`，应用在新建 iOS 26.5 模拟器中安装并启动，启动后进程持续运行。
- 离线全量 JVM 门两遍 EXIT 0，第二遍使用 `--rerun-tasks`。每遍收录 667 项（domain 13、features 101、data 136、source-runtime 65、app 352），663 通过、4 项显式在线回归跳过、0 失败。
- 两遍都重验 Venera 原版样例和六源夹具。样例 SHA-256 为 `41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d`，搜索、详情、章节、正文图片、缩略图和标签回调通过。baozi、mxs、manhuagui、komga、comic_walker、manga_dex 的夹具均通过。

## 待裁决及 UI 接线

Android 的依赖检查确认 `:apps:android:debugRuntimeClasspath` 目前没有 WorkManager。按既定选型完成系统任务需要批准在 `:shared:app` 的 Android source set 中增加 `androidx.work:work-runtime`。本轮 Android 的进程内补跑、网络门和通知适配已接通。

iOS 验收分为应用启动与系统实际调度。真实的延后、挂起唤醒和通知投递仍需在设备端验证。后台任务的起始时间是最早允许执行的时间。 [Apple 调度时序](https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate)

| 顺序 | 现有文件与入口 | 接线 |
| --- | --- | --- |
| 1 | `shared/app/src/commonMain/kotlin/browse/SourceScreens.kt:1080` · 详情下载 | `onClick = { scope.launch { downloads.enqueue(sourceKey, workId, chapter.id) } }`。从 `LocalProductionDownloads.current` 取仓储，通过 `downloads.statusesForWork(sourceKey, workId)` 回读状态，无分章传 `null`。 |
| 2 | `shared/features/src/commonMain/kotlin/more/MoreScreen.kt:125` · 设置入口 | `val settings = LocalProductionSettings.current`。读写外观、Reader、下载和更新调度。主题 ID 和封面取色仍由视觉主线接入主题渲染。 |
| 3 | `shared/app/src/commonMain/kotlin/browse/BrowseScreen.kt:175` · 来源管理与 Feed | 固定已接通。管理入口从 `sources.discovery.sources(includeFiltered = true)` 读取分组和隐藏项。Feed 从 `sources.feed.feedItems()` 读取并调用 `sources.loadFeedPage(id, position, next)`。 |
| 4 | `shared/app/src/commonMain/kotlin/browse/SourceScreens.kt:169` · 搜索 | `val result = browse.searchWorks(sourceKey, query, page, next)`。保存搜索调用 `VeneraBrowseService` 的 CRUD 与 `searchSavedWorks`。在 `AppScaffold.kt:387` 将 `onBack = { navigation.popCurrentChild() }` 传入来源搜索。 |
| 5 | `shared/features/src/commonMain/kotlin/updates/UpdatesScreen.kt:219` · 更新错误入口 | `val groups = productionUpdates.failuresByWork()`。多选删除调用 `productionUpdates.clearFailures(selectedKeys)` 后回读。 |

## 首轮保留记录

- F0–F2 已交付下载及离线、统计 SQL、偏好持久化、调度、来源和 Feed、搜索分页、详情元数据、更新错误批删与 IO 调度。统计的追踪三项维持 0、N/A、0。旧版仅内存固定标记没有可迁移记录。
- 首轮 JVM 门两遍 EXIT 0，各收录 664 项，660 通过、4 项显式在线回归跳过。三端构建和 WAL、5000 ms 锁等待实测均通过。Android API 26–29 的超时设置使用写连接初始化，逐连接设置由支持该 API 的 Android 版本执行。
- `HistoryMigrationTest` 的精确列断言加入 `read_duration_ms`，老行累计时长从零起算。引擎轨并入时核对 SQLDelight 迁移号 `11.sqm`。
- 扩展锁测量的 250 ms HTTP 模拟下，另一源的登录状态读取等待中位数 302 ms，直接搜索 1 ms。共享 QuickJS 回调锁内的另一源搜索等待中位数 263 ms。保留锁策略，以维持扩展替换、网页登录所有权与共享上下文的原子边界。
