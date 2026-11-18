# Komikku 对齐计划

当前目标是先交付跨平台的 Komikku 漫画阅读体验。以本地 `reference/komikku` 的 `383ffaa81667f45fc6c2975edb1760a7606531d3` 为界面、交互和漫画功能基线，以当前 `main` 的 `96aefb5` 为实现基线（`dev.reader.*` → `app.koyura.*` 重命名已完成）。继续使用 KMP、Compose Multiplatform 和官方 Material 3。JS 扩展沿用 Venera-Next 协议，原版 Komikku 的扩展安装入口和用户操作仍用于界面对照。小说应用接入与差异化功能安排在漫画对齐之后。 [架构设计](architecture.md) · [技术栈选型](technology-stack.md)

## 现状

- 应用外壳已有五个一级标签和底栏、导航轨。浏览、扩展仓库、搜索、作品详情和历史已接生产服务。书架、作品更新、更多目前是占位入口。
- 漫画阅读器已有横向从左到右单页、按需加载、重试、切章、桌面键盘和磁盘续读（已迁入 `:shared:features`，`ReaderService` 契约在 `:shared:domain`，生产装配与端到端测试留在 `:shared:app`）。现有测试验证了启动后从历史恢复同一章同一页。 [导航实现与截图](../shared/app/docs/app-shell.md) · [浏览实现与截图](../shared/app/docs/browse-extensions.md) · [漫画阅读器](../shared/app/docs/reader.md)
- TXT 和 EPUB 解析与阅读位置保存在独立的 `shared/media` 模块。 [小说模块](../shared/media/docs/usage.md)

## 第一轮 · 在线漫画主流程与阅读器

先在原版逐屏记录五个一级页面和阅读器的状态，再对齐「浏览或历史 → 作品详情 → 章节 → 阅读」的真实页面与应用外壳。参考文件位于 `reference/komikku/app/src/main/java/eu/kanade/`。

| 对齐项 | 当前进度 | 原版参考 | 可复现的验收 |
| --- | --- | --- | --- |
| 外壳、主题与导航 | 基础导航可用，待视觉复核 | `tachiyomi/ui/home/HomeScreen.kt` | 比对浅色与深色的工具栏、导航、选中态和角标。窄宽切换后检查选中标签、返回栈和滚动位置。 |
| 浏览、详情与历史 | 生产链路可用，待视觉复核 | `tachiyomi/ui/browse/BrowseTab.kt`、`presentation/browse/SourcesScreen.kt`、`ExtensionsScreen.kt`、`BrowseSourceScreen.kt`、`presentation/manga/MangaScreen.kt`、`presentation/history/HistoryScreen.kt` | 同一份作品和章节数据下比对封面、分组、行高、按钮位置、加载与错误态。完成从来源搜索进入指定章节、从历史继续阅读和返回原标签。 |
| 横向翻页、竖向分页 | 已支持从左到右单页 | `tachiyomi/ui/reader/setting/ReadingMode.kt`、`tachiyomi/ui/reader/viewer/pager/PagerViewers.kt`、`PagerViewer.kt` | 用同一组编号页验证从左到右、从右到左、竖向分页。手势、点按区和桌面按键的前进方向与模式一致。切换模式或改变窗口尺寸时仍在同一页。 |
| 条漫与连续纵向 | 待实现 | `tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt`、`tachiyomi/ui/reader/setting/ReadingMode.kt` | 分别对照 WEBTOON 和 CONTINUOUS_VERTICAL 的页间距、页面定位和跨章衔接。使用长图、混合页高及章节边界检查滚动、取图与续读。 |
| 双页、缩放与图片显示 | 已有图片加载和重试 | `tachiyomi/ui/reader/viewer/pager/PagerConfig.kt`、`PagerViewer.kt`、`tachiyomi/ui/reader/viewer/ReaderPageImageView.kt`、`presentation/reader/settings/ReadingModePage.kt` | 双页作为分页阅读的布局选项。用竖封、横跨页和奇数页对照配对、方向与偏移。用长图验证缩放、平移、适配、裁边和读图失败后的重试。 |
| 工具栏与阅读设置 | 已有简易控件 | `presentation/reader/appbars/ReaderAppBars.kt`、`ReaderTopBar.kt`、`ReaderBottomBar.kt`、`presentation/reader/ChapterListDialog.kt`、`ReadingModeSelectDialog.kt`、`settings/ReaderSettingsDialog.kt` | 对照全屏及菜单显示状态、页码和跳页、章列表及前后章、模式与设置面板。检查全局默认和作品覆盖的优先级，重启后恢复设置以及同一章同一页。 |

这轮按依赖先验收默认横向模式和菜单，再扩展到 RTL、竖向分页、条漫和连续纵向，补齐双页、缩放与显示选项。模式切换、窗口大小改变与重启共用现有按页键保存的进度。各模式的行为分别通过后才标记该项完成。

### 首轮任务边界

| 负责人 | 交付范围 |
| --- | --- |
| A | 应用外壳、主题和浏览、详情、历史的视觉对齐。主要负责 `shared/design-system` 与 `shared/app` 的相关非阅读器页面。 |
| B | 漫画页面布局、阅读模式、手势、双页、缩放和位置映射。负责阅读器 Viewer 与阅读状态，以真实编号图验证。 |
| C | 阅读器顶栏、底栏、跳页、章列表、阅读模式和设置面板。负责阅读器控件和设置，按共享阅读器状态和操作回调渲染。 |
| 主 agent | 先确定 Viewer 和控件共用的模式、当前页与操作回调。负责阅读器总入口、三端应用接线、组合回归和逐项验收。 |

## 后续漫画对齐

- **书架与详情**实现真实收藏、网格和列表、分类与筛选、作品操作和从书架续读。参考 `tachiyomi/ui/library/LibraryTab.kt`、`presentation/library` 和 `presentation/manga/MangaScreen.kt`。
- **更新与离线**实现作品更新分组、更新失败入口、下载队列和离线漫画阅读。参考 `presentation/updates/UpdatesScreen.kt`、原版下载流程和阅读器的本地加载器。
- **浏览与更多**补齐原版的全局搜索、源分类、Feed、保存搜索、设置、追踪和备份等用户可见路径。对应的 Venera 源能力按原样脚本验收。平台特有能力记录可达操作与验证结果。 [原版功能清单](../reference/komikku/README.md#features)

## 任务与验收记录

每份任务提示词引用本页的一项或几项，并写清原版入口、参考文件、操作序列、验收命令及本轮允许修改的目录。审阅通过后在对应项记录合并 SHA、截图或录屏路径和平台验证结果。有功能入口的页面要验证真实动作和异常状态，应用占位页只登记为待实现。

- **视觉**使用同一份作品数据、语言、字体比例、主题和可用画布尺寸，成对保存原版和本项目的截图。涉及滚动、手势、菜单显隐和切换模式时补操作录屏。窄屏与宽屏都核对，桌面测试覆盖鼠标、滚轮、键盘和窗口缩放。
- **阅读**使用固定编号的普通页、横跨页、长图、损坏页与两章夹具，断言当前章和页、翻页方向、失败重试、切换保位与跨进程续读。
- **平台**分别记录 Android、桌面 JVM 和 iOS 的构建与运行验证。发布验证沿用技术栈选型的目标系统范围。

从主工作树运行当前集成验收。独立工作树使用 `-PveneraReferenceDir` 指向主工作树的参考仓库。

```bash
./gradlew :shared:app:jvmTest :shared:data:jvmTest :shared:source-runtime:jvmTest \
  :apps:android:assembleDebug :apps:desktop:classes \
  :shared:app:linkDebugFrameworkIosSimulatorArm64 \
  -PveneraReferenceDir="$PWD/reference/Venera-Next"
```

### P1 外壳与形态切换（2026-11-18）

对照 `reference/komikku`（`383ffaa816`）：`tachiyomi/ui/home/HomeScreen.kt`、
`tachiyomi/util/system/DisplayExtensions.kt`、`presentation-core/.../material/NavigationBar.kt`、
`NavigationRail.kt`、白皮书 03/04 章。落点：`AppScaffold.kt`、`App.kt`、
`design-system/navigation/`、相关测试。形态改最小宽度判定 + AUTOMATIC（700 竖/600 横）
四模式；Tab 切换 fadeThrough 200ms 先出后进；同 Tab 重按先回根再发射事件；Rail 常量收敛进
`DesignTokens`；`updatesBadgeCount` 接线预留（数据源随更新页落地）。
截图 `docs/alignment/p1-shell/screenshots/`（外壳四形态 + 浏览两页 × 窄/宽 × 浅/深 12 张，
Browse 角标可见）；审阅 `docs/reviews/2026-11-18-visual-p1-shell.md`；
`:shared:design-system`、`:shared:app`、`:shared:features` jvmTest 全绿。
