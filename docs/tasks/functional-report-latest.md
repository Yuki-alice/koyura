# 功能补齐交付报告

2026-11-20 · `feat/functional-core` · 基线 `c729ff6`

沿用仓库的依赖与版本基线。复验时将 `-PveneraReferenceDir` 指向主工作区的 `reference/Venera-Next`。

## 改动

| 范围 | 生产落点 |
| --- | --- |
| F0 下载 | 队列从 SQLite 回读可见任务、进度和标题。支持暂停、恢复、重试、取消和清空。清空批量取消当前可见任务，已提交的离线包继续可读。单章手动再次入队复用已取消记录的 ID。更新发现新章后按阅读顺序入队。阅读超过四分之一且当前章与下一章已离线时，按偏好在其后补入未读章。 |
| F0 统计 | 在 `ExtensionApp` 注入真实统计仓储。从书架、章节、更新元数据、进度和下载聚合数据。已完结且无未读章计为完成。开始阅读包含持久进度或已读章。下载数统计全部完成任务。阅读时长由调用方传入实际前台计时；旧记录从 0 起算。追踪三项为 0、N/A、0。 |
| F0 设置 | 外观存 `library_prefs`，Reader 沿用现有文件，下载触发与更新调度沿用原键。智能更新策略仍经现有 `ProductionUpdatePolicyRepository`。三端装配均可访问同一个生产设置仓储。 |
| F0 调度 | 进程内调度保留持久锚点和发布窗口。启动及前台恢复重判到期，前台事件合并。保留旧 JSON 读取，缺锚点时用上次检查时刻或当前时刻补齐。全次扫描失败、部分作品失败和自动下载触发失败分别入账。通知边界为领域接口与测试 Fake。 |
| F0 来源和 Feed | 复用 `browse.discovery` 与 `browse.feed`，生产浏览列表按可见性过滤，Feed 按稳定的来源键与栏目名加载。固定、隐藏、分组、默认、NSFW 和 Feed 启用态均有持久回读。旧版内存里的固定标记没有可迁移记录。 |
| F1 搜索 | `BrowseService.searchWorks(sourceKey, query, page, next)` 提供页码和游标结果，原三参数调用保留。全局搜索保留各源分页数据和独立失败。保存搜索支持读取、创建、编辑、删除和按固化选项执行。 |
| F1 详情与更新错误 | 兼容平面和分组章节映射，并从扩展章节对象补日期、汉化组及页数。缺失字段返回 null。错误组按 `(sourceKey, kind, workId)` 识别，多选删除在单事务内完成。 |
| F2 数据库和 I/O | 三端生产驱动配置 WAL 与 5000 ms 锁等待。书架和历史的列表、偏好、常规写入与章节事务移到 `Dispatchers.IO`。取消协程时保留原有的取消传播。 |

`HistoryMigrationTest` 的三个精确列集合加入 `read_duration_ms`，以覆盖当前 schema。旧记录的章节、页码及未知访问时间断言保留，另断言旧记录的累计时长为 0。详情日期接受 epoch 毫秒、带时区 ISO 时间及日期字符串，日期字符串按 UTC 当日零点解析。

## 验收记录

- F0 受影响模块 `:shared:data:jvmTest :shared:app:jvmTest` 通过。
- F1 四项定向测试通过。覆盖三类章节目录、页码和游标、部分失败、保存搜索回读及更新错误批删。夹具按生产顺序执行 `open()` 与 `restore()`。
- 书架重启夹具增加等待归类对话框成功关闭与工具栏回显。分类写入移到 IO 后，提交和关闭是异步的，所有原有断言保留。单独重跑通过。
- Android APK、桌面 classes、iOS 模拟器 framework 的联合构建 EXIT 0。
- 桌面与 iOS 模拟器通过生产驱动两次打开测试。每次分别读取常规连接和事务连接，四次均为 `journal_mode=wal`、`busy_timeout=5000`。
- 离线全量 JVM 门两遍 EXIT 0，第二遍带 `--rerun-tasks`。两遍各收录 664 项（domain 13、features 101、data 135、source-runtime 65、app 350），660 通过、4 项显式在线回归按默认开关跳过、0 失败。
- 两遍均传入 `-PveneraReferenceDir` 与 `-PveneraCommunityFixtureDir`。Venera-Next `c0c4597` 原版样例 SHA-256 为 `41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d`，搜索、详情、章节、正文图片、缩略图与标签回调通过。社区夹具使用 `a04e05591f7832f7166d6660546613c114d6738a`，baozi、mxs、manhuagui、komga、comic_walker、manga_dex 六源通过并逐项校验脚本哈希。
- Android API 36（`han1me-16kb`）驱动实测 EXIT 0。临时只读模拟器中的独立测试 APK 直接调用 Android 调试产物内的生产驱动工厂。两次打开的常规查询和事务均为 `wal/5000`，逐连接配置可用。

## 扩展锁测量

桌面 JVM，两个独立来源，Ktor MockEngine 将来源 A 的 HTTP 阶段挂起 250 ms。收到请求后启动来源 B 的操作，用单调时钟记录等待，重复三次。括号内说明竞争操作是否在释放 HTTP 前完成。

| 竞争操作 | 三次耗时（ms） | 中位数（ms） |
| --- | --- | --- |
| A 下载扩展更新，B 读登录状态 | 346、302、270（三次均等待释放） | 302 |
| A 下载扩展更新，B 直接执行搜索回调 | 25、1、1（三次均在释放前完成） | 1 |
| A 搜索的 JS 回调等待 HTTP，B 执行搜索 | 347、263、256（三次均等待释放） | 263 |

扩展服务的更新锁覆盖下载、替换和版本提交。QuickJS 的独立锁覆盖共享上下文内的回调执行。前者若将下载移到锁外，提交时需重验版本、来源和网页登录所有权。后者若改成每源锁，需要重新隔离 QuickJS 上下文及宿主状态。本轮记录测量，保留两处锁策略。

## UI 接线顺序

下列行号以本工作树为准。生产对象由 `shared/app/src/commonMain/kotlin/ExtensionApp.kt:166` 起注入。

| 顺序 | 文件与入口 | 数据接线片段 |
| --- | --- | --- |
| 1 | `shared/app/src/commonMain/kotlin/AppScaffold.kt:115` · 下载队列 | `val activeDownloadsSession = rememberDownloadsSession(LocalProductionDownloads.current ?: remember { FakeDownloadRepository() })`。队列打开期间订阅 `downloads.jobs`，按最新快照刷新行与进度，避免每次进度更新切回加载态。 |
| 2 | `shared/app/src/commonMain/kotlin/browse/SourceScreens.kt:1080` · 详情下载按钮 | `onClick = { scope.launch { downloads.enqueue(sourceKey, workId, chapter.id) } }`。从 `LocalProductionDownloads.current` 取仓储透传到行，来源、作品、章变化时调用 `downloads.statusesForWork(sourceKey, workId)` 生成可用状态。无分章用 `null`。 |
| 3 | `shared/app/src/commonMain/kotlin/AppScaffold.kt:113`、`shared/features/src/commonMain/kotlin/reader/comic/ReaderScreen.kt:172` · 统计与计时 | 已通过 `LocalStatisticsRepository` 接通。阅读器在保存位置后、退出章节或进入后台时提交实测时间，调用 `(readerService as? ReadingDurationRecorder)?.recordReadingDuration(sourceKey, workId, chapterId, elapsedMillis)`。现有阅读器尚无该计时调用。 |
| 4 | `shared/app/src/commonMain/kotlin/App.kt:77`、`shared/features/src/commonMain/kotlin/more/MoreScreen.kt:125` · 主题与设置 | `val settings = LocalProductionSettings.current`，进入设置时读 `settings?.appearance()`、`readerSettings()`、`downloadPreferences()`、`updateSchedule()`，分别调用对应保存方法；将保存后的模式与 AMOLED 传给 `ReaderTheme`，主题 ID 与取色由主线主题渲染消费。 |
| 5 | `shared/app/src/commonMain/kotlin/browse/BrowseScreen.kt:175`、`BrowseSession.kt:108` · 固定来源和 Feed | `sources.discovery.setPinned(sourceKey, pinned)`，刷新时以 `sources.discovery.sources().sources.filter { it.pinned }` 还原。管理页从 `sources.discovery.sources(includeFiltered = true)` 读取分组及隐藏项。Feed 读取 `sources.feed.feedItems()`，翻页调用 `sources.loadFeedPage(id, position, next)`。 |
| 6 | `shared/app/src/commonMain/kotlin/browse/SourceScreens.kt:169` · 来源搜索 | `val result = browse.searchWorks(sourceKey, query, page, next)`（导入 `app.koyura.app.browse.venera.searchWorks`）。用 `hasMore` 和 `next` 更新分页；保存搜索通过 `VeneraBrowseService` 的 CRUD 与 `searchSavedWorks`。全局单源继续加载调用 `globalSearch.searchOne(sourceKey, sourceName, query, page, next)`。在 `AppScaffold.kt:387` 将 `onBack = { navigation.popCurrentChild() }` 传入来源搜索。详情元数据由现有章节副标题直接读取，网页入口可读 `detail.url`。 |
| 7 | `shared/features/src/commonMain/kotlin/updates/UpdatesScreen.kt:219` · 更新错误入口 | `val groups = productionUpdates.failuresByWork()`；删除时 `productionUpdates.clearFailures(selectedKeys)`，然后回读。旧错误弹层继续可读平面列表。 |

## 待接线与下一轮

- 接入系统后台任务与通知实现，补齐 Android 和 iOS 的网络状态回调。当前会话调度据可用的 `isOnline` 与 `isUnmetered` 判断，平台未供给的网络属性沿用平台默认。系统驻留与退到后台后的唤醒由下一轮平台接线完成。
- 阅读计时需由阅读页面的前台生命周期产生。当前统计仓储回读已提交时长。平台本地作品仍无生产导入入口，现有统计为 `local` 来源保留计数口径。
- 与引擎轨并入时核对 SQLDelight 迁移号，`11.sqm` 为本分支新增的阅读时长列。Android API 26–29 使用连接初始化时的写连接超时，支持逐连接初始化的系统应用到连接池。
