# Koyura 功能差距审阅

审阅日期：2026-11-18（Asia/Shanghai）。

## 结论与口径

项目已建立「安装源 → 单源搜索 → 详情 → 在线漫画阅读 → 保存进度 → 历史/书架续读」的生产主干，并接入书架收藏、分类和五种阅读模式。当前适合定位为早期 Alpha；距离项目所定义的 Komikku 完整体验，仍有多个系统尚未交付，已有页面也存在界面能力超过生产数据能力的情况。

本次以当前工作区为准，包含已有未提交修改；Git HEAD 为 `0962fd0`。对照目标来自 `docs/komikku-alignment.md`、`docs/architecture.md` 和 `docs/technology-stack.md`。Komikku 使用项目固定的本地参考提交 `383ffaa81667f45fc6c2975edb1760a7606531d3`，不将上游后续新增功能混入缺口。

按本文拆分的 **13 个漫画功能域，7 个已有部分实现，6 个尚无完整用户操作闭环**。这个计数表示覆盖范围，不代表完成了 7/13 的工作量；没有足够验收证据把任一大域标记为完整对齐。跨平台、源兼容和性能的剩余工作也不能用页面数量衡量。

小说属于项目的后续额外目标，单列，不算作 Komikku 缺失项。Komikku APK 扩展与 Venera JS 扩展的协议差异是项目的明确选择，不应要求实现 APK 扩展兼容。

## 本次验证

执行：

```sh
./gradlew :shared:domain:jvmTest :shared:features:jvmTest \
  :shared:data:jvmTest :shared:source-runtime:jvmTest :shared:app:jvmTest --console=plain
```

应用公共代码编译失败，阻断 `:shared:app:jvmTest`：

```text
SourceScreens.kt:694:35 'val maxWidth: Dp' cannot be called in this context
with an implicit receiver. Use an explicit receiver if necessary.
Kotlin compiler: DSL_SCOPE_VIOLATION
```

随后独立执行四个核心模块的 JVM 测试任务成功：

| 模块 | 测试结果 XML 记录 | 失败/错误 |
| --- | ---: | ---: |
| domain | 11 | 0 |
| features | 68 | 0 |
| data | 105 | 0 |
| source-runtime | 48 | 0 |
| 合计 | 232 | 0 |

部分任务命中 Gradle 的最新结果；并非全部强制重跑。四模块检查日志：`/tmp/koyura-audit-jvm-core-20261118.log`。工作区存在历史 app 测试 XML，但本次 app 编译失败，不能将旧报告算作当前通过。未执行 Android/iOS 实机、Windows/Linux 安装包或真实站点端到端验收。

以下问题除编译错误已由命令复现外，均由当前源码调用链确认；未因本次审阅修改业务代码或添加测试。

## 现有功能中的实际缺陷

### 1. 高优先级：当前应用无法编译

`shared/app/src/commonMain/kotlin/browse/SourceScreens.kt:694` 在 `Column` 接收者中隐式访问外层 `BoxWithConstraints.maxWidth`，被 Compose 的 DSL 作用域限制拒绝。需要在外层保存宽度或显式限定接收者，再恢复应用层验证。此问题位于已有未提交修改中，不代表所有已提交版本均有该问题。

### 2. 高优先级：隐藏分类没有隐藏「全部」中的作品

生产适配器 `ProductionLibraryRepository.kt:48` 总是用 `includeHiddenCategories = true` 读取条目。`LibrarySession.kt:87` 的 `visibleEntries` 把全部条目交给 `applyLibraryView`；后者只按当前分类、搜索和普通筛选过滤。`LibrarySession.kt:103` 只控制隐藏分类页签是否显示。

因此，一部作品如果只属于隐藏分类，默认「全部」视图仍会显示它。数据层已有隐藏分类排除语义，但生产装配与显示层未沿用。验收应覆盖「只属于隐藏类」「同时属于可见类与隐藏类」「无分类」三种情况及搜索结果。

### 3. 高优先级：书架标记已读/未读可点击但无实际写入

`LibraryScreen.kt:1206`、`:1212` 提供可点击动作，经 `LibrarySession.markSelectedAsRead` 调用生产仓储，但 `ProductionLibraryRepository.kt:92` 只重新 `load()`。

章节表与章节已读、书签、下载状态尚未落地；生产映射也没有填入总章数、未读数、下载数、完结状态等字段。这导致相关筛选、角标、排序和状态分组虽然存在界面，生产数据却长期是 0、false、UNKNOWN 或 null。它们不能计为已完成能力。

详情目录同样只是以最近续读章匹配行的显示状态（`SourceScreens.kt:910`），不能表达「多章已读」和「整部读完」。详情批量标记入口则明确禁用。

### 4. 高优先级：七个书架显示开关在生产仓储中丢失

`LibrarySheets.kt:343` 起提供未读角标、下载角标、语言角标、继续按钮、分类标签、显示隐藏分类、作品数七个开关。`ProductionLibraryRepository.kt:205` 的序列化只保存模式、列数、筛选、排序、分组；`:230` 重建 `LibraryPrefs` 时没有恢复这些布尔字段。

`LibrarySession.updatePrefs` 会立即用仓储返回的快照替换界面状态，因此这些开关可能在点击后就恢复默认，而不仅是重启后丢失。其中「显示隐藏分类」默认 false，也会妨碍用户从该设置进入隐藏页签。

### 5. 中优先级：书架直接开始/继续阅读不完整

`LibraryEntry.canContinue`（`LibraryModels.kt:80`）要求非空章节 ID 或总章数大于 0。生产适配器没有填总章数，刚收藏且未阅读的作品不会显示「开始」按钮；无分章作品的章节 ID 为 null，即使有阅读记录也可能没有继续按钮。

`LibraryTab.kt:48` 还会对空章节列表直接报错；这与详情页/阅读器支持无分章作品的契约不一致。该路径在进入阅读前必须在线请求详情，也不能支持离线续读。

### 6. 中优先级：书架部分导航按钮未接线

`LibraryScreen` 的 `onOpenBrowse`、`onGlobalSearch`、`onGuide` 默认是空回调（`:196` 起）；`LibraryTabContent` 只传入打开作品与继续阅读。这意味着空书架的浏览引导、全局搜索等可见按钮没有对应动作。全局搜索本身也没有生产页面和路由。

## 漫画功能覆盖矩阵

“已有部分实现”表示代码与生产接线存在，不等同于当前工作区已通过完整应用回归。“无用户闭环”允许底层存在零散类型或预留接口。

| 功能域 | 当前状态 | 相对项目目标和 Komikku 的主要缺口 |
| --- | --- | --- |
| 1. 应用外壳与主题 | 已有部分实现 | 五标签、独立返回栈、720dp 底栏/导航轨切换、系统浅深色已实现。自定义调色板、封面动态主题、主题设置入口、重选标签行为及原版真实设备视觉验收未完成。AMOLED 有参数但生产 `App` 未提供用户入口。 |
| 2. 扩展与仓库 | 已有部分实现 | 仓库增删改、启停、预览、安装/更新/卸载、事务恢复已接线。源配置、账号、网页会话和完整脚本宿主缺失；脚本文件/URL 导入有服务方法，但常规 UI 尚未提供完整流程。安装缺字节进度及取消步骤。 |
| 3. 内容发现与搜索 | 已有部分实现 | 目前是单源 `search.load(query, [], page)`。无全局搜索、Feed、保存搜索、explore/category 浏览及搜索选项。`SearchNext` 虽有枚举，但生产搜索未消费该分页路径；来源语言为空、Latest 恒 false，缺固定/自定义源分类和 NSFW 过滤。 |
| 4. 作品详情与目录 | 已有部分实现 | 作品信息、封面、收藏、章列表、直达阅读、开始/续读存在。分组目录被拍平，缺章节状态持久化、书签、批量管理、完整排序筛选、相关推荐、信息编辑、封面分享/保存等操作。 |
| 5. 书架与分类 | 已有部分实现 | 收藏、分类管理、多分类归属、网格/列表/全景、搜索、多选、取消收藏和偏好主项已接真实 SQLite。缺有效章节派生数据、隐藏语义、七个开关保存、完整开始/续读；高级搜索仅为标题/ID/源名 contains，缺 Komikku/SY 的查询语法、拖拽排序、追踪筛选和浏览结果批量收藏。 |
| 6. 历史 | 已有部分实现 | 本地历史、日期分组、搜索/过滤、收藏联动、删除/清空后的持久隐藏、续读均有实现。无选择模式与批量选择删除，未收藏作品无封面快照，进入阅读仍依赖在线解析目录。清除历史不删除续读位置是当前明确设计。 |
| 7. 漫画阅读器 | 已有部分实现 | LTR、RTL、竖向分页、条漫、连续纵向，单/双页/自动、缩放、裁边、适配、跳页、章列表、全局/作品覆盖及位置保存均已接入生产入口。缺长图分块/子采样、缓存淘汰、离线加载、连续跨章内容衔接、条漫页内滚动偏移恢复、自动滚动、完整可配置手势与点击区、亮度/滤镜/方向控制、图片保存分享等。 |
| 8. 作品更新 | 无用户闭环 | Updates 仍落入 `TabRootScreen` 占位。无章节差异检测、更新分组、智能/定时更新、待更新列表、更新错误列表、后台调度及更新通知。扩展版本检查不能算作品更新。 |
| 9. 下载、离线与本地漫画 | 无用户闭环 | 下载按钮禁用。无队列、暂停/取消/重试、重启恢复、下载目录管理、离线优先加载及本地漫画档案/文件导入。保存页码不代表保存图片；不能据此宣称离线阅读。 |
| 10. 迁移与合并 | 无用户闭环 | 数据层有同源换 key 和条目复制合并原语；用户层跨源迁移被显式 gate 禁用，书架迁移按钮置灰。无跨源候选搜索、选择范围、迁移进度、冲突处理及 Komikku 的多作品合并体验。 |
| 11. 外部追踪 | 无用户闭环 | 无 MAL、AniList、Kitsu、MangaUpdates、Shikimori、Bangumi 等追踪服务接入、授权、作品绑定和双向进度同步。 |
| 12. 备份、恢复与同步 | 无用户闭环 | 架构有版本化备份/WebDAV 设计，但生产没有导入导出、恢复、自动备份、远端同步、冲突合并及凭据管理流程。SQLite 持久化与安装事务回滚不能替代用户备份。 |
| 13. 更多、应用设置与平台能力 | 无用户闭环 | More 为占位。阅读器设置属于已实现子集；应用设置、账号与密钥、文件授权、网页登录、通知/小组件、应用更新流程及目标系统分发验收仍缺。 |

确认占位的依据：`AppScaffold.kt:199` 起只单独处理 Browse、Library、History，其余根页走 `TabRootScreen`；`ui/TabScreens.kt` 仍是说明文案与测试导航按钮。

## Venera 完整兼容是独立的大缺口

架构目标是现有 Venera-Next 脚本及源仓库原样导入，而非只实现相似的 JS 格式。当前已完成较扎实的脚本注册、初始化、安装替换、回滚、HTTP 基础映射以及图片回调句柄管理，但不足以宣称完整兼容。

- `VeneraSource.kt:22` 仅列出搜索/搜索下一页、详情、章节、缩略图、图片和标签回调，共 8 个路径；没有 explore、category、account、在线收藏、评论等能力入口。
- `VeneraBootstrap.kt:85` 的宿主消息只接受非 bytes 的 HTTP 请求，其他消息直接抛 Unsupported。`ComicSource` 没有原版源数据读写、源设置等完整宿主方法；`APP` 仅注入 version/locale/platform。
- HTML DOM/CSS 选择器、Convert、compute、UI 对话框、剪贴板、Cookie 管理、fetch、完整定时器等宿主兼容尚未形成实现。
- `VeneraKtorTransport.kt:46` 明确记录 cookie/session、cache-time、prevent-parallel、extra 等策略未实现。当前 sourceKey 随请求携带，但不等于实现了按源持久会话；手动 Cookie 请求头透传也不等于 Cookie 仓储和网页登录。
- `VeneraBootstrap.kt:271` 明确拒绝 `modifyImage`，需要图片处理的源会受限。
- 当前未改动 minimal_source.js 的 MockEngine 验证支持已覆盖的回调路径；它不能证明复杂真实源、账号站点及整套宿主 API 可用。

建议以真实脚本能力分层验收：纯 HTTP 源 → HTML 解析源 → 持久设置/数据源 → 登录/Cookie 源 → 图片处理源 → explore/category/评论/收藏等完整交互源。逐层使用原样脚本及跨进程恢复验证，不能仅检查导入成功。

## 阅读器成熟度与平台验收

阅读器是目前最接近首轮目标的部分。`ReaderScreen.kt` 已真正调用 Viewer、Chrome 和 ReaderPreferences，旧交付文档中的“未接线”描述已经过时。

但它仍有下列重要边界：

- 图片经 BitmapFactory/Skia 全图解码，`ReaderSession.pages` 持有已加载的当前章图片字节，主要在切章时清空；未见按内存预算淘汰的机制。大长图和超长章节的内存、解码耗时及流畅度仍需实测，本次未测得具体 OOM 或帧率结论。
- 条漫跨章依靠边界点按上报后重开章节，不是把前后章图片接入同一连续滚动数据集。位置持久化记录页键与页码，未覆盖长图内部的精确滚动位置。
- 点击区方案在 Viewer 中有类型与逻辑，但 `ReaderSettings.viewerOptions()` 没有将这些选项暴露到设置，用户仍使用默认方案。
- `docs/alignment/b-viewer/README.md` 明确说明原版侧画面来自源码推导，部分 MP4 是测试帧拼接。A 的外壳截图也为 JVM 离屏渲染；这些可作实现证据，不能替代项目要求的原版真机配对截图和实际操作录屏。
- Android/iOS/macOS 入口与公共服务装配存在，Windows/Linux 尚缺本次安装包运行证据。当前公共代码的编译阻断未解除，也无法声称三端最新集成已通过。

## 小说目标

`shared/media` 有 TXT/EPUB 解析、结构化正文、位置模型和大量夹具测试，但根 `settings.gradle.kts` 未包含该模块。生产导航、文件导入、小说阅读页面、JS novel 能力和书架小说流程尚未交付。应计为“独立解析基础已有，用户功能尚未接通”，按项目目标安排在漫画对齐之后。

## 建议的补齐顺序与完成条件

1. **恢复当前集成并修复无效操作。** 解决公共代码编译；让隐藏分类、显示开关、标记已读、空书架导航和无分章续读的真实仓储行为与 UI 一致。已有按钮应有可靠动作；本阶段必须恢复 app JVM 回归与三端编译。
2. **建立真实源可用性基线。** 补源设置/数据、HTML、Cookie/网页登录和关键图片处理宿主能力；挑选有代表性的原样脚本验证安装、搜索、取图与重启。同步修订不准确的“完整兼容/按源会话”表述。
3. **补章节数据与追漫主流程。** 先建立章节及已读/书签/下载状态，再做未读数、目录管理、更新扫描、更新页面和下载队列。用固定两章数据验证新章检测、标记状态、异常重试、重启恢复和断网阅读。
4. **补内容发现与迁移。** 全局搜索、分类/探索、Feed、保存搜索、推荐及批量收藏；随后实现有冲突策略和进度反馈的跨源迁移/多作品合并。
5. **补数据管理及长期使用能力。** 用户备份恢复优先，之后接 WebDAV、追踪、应用设置和平台服务。贯穿阶段 2–5 做大图内存与真实设备回归，不能把它们全部推迟到视觉收尾。
6. **完成原版配对与分发验收，再接小说。** Android 原版与 Koyura 同夹具、同画布、同主题逐项比对；补桌面鼠标/键盘/缩放和 iOS 生命周期、目标安装包验证。

不建议给出固定周数或把剩余功能估为少量页面工作：源兼容、章节数据、离线任务、登录、同步和跨平台适配都有独立的实现与验收成本。

## 目标文档需要同步

README、对齐计划仍将书架标为占位、阅读器标为仅 LTR；A/B/C 交付记录包含“未接线”和已被后续实现替代的限制。另一方面，架构表对备份编解码、HTTP 按源会话等措辞又超过实际交付范围。建议在修复集成后按“源码已实现 / 生产已接线 / JVM 已验证 / 平台已验证 / 原版已对齐”分别记录状态，附当前 SHA 和证据，避免下一轮据旧表重复开发或误判完成度。

## 主要参考

- 项目目标：`docs/komikku-alignment.md`、`docs/architecture.md`、`docs/technology-stack.md`。
- Komikku 功能范围：`reference/komikku/README.md` 的 Features；相关源码在 `reference/komikku/app/src/main/java/eu/kanade/`，含 `presentation/browse/FeedScreen.kt`、`GlobalSearchScreen.kt`、`presentation/updates/UpdatesScreen.kt`、`tachiyomi/data/backup/`、`tachiyomi/data/sync/` 和阅读器实现。
- 实现证据：`shared/app/src/commonMain/kotlin/ExtensionApp.kt`、`AppScaffold.kt`、`library/ProductionLibraryRepository.kt`、`library/LibraryTab.kt`、`browse/venera/VeneraBrowseService.kt`、`history/VeneraHistoryService.kt`。
- 功能状态：`shared/features/src/commonMain/kotlin/library/`、`reader/comic/`；数据与宿主：`shared/data/src/commonMain/`、`shared/source-runtime/src/commonMain/kotlin/`。
