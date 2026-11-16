# A · 应用壳、浏览、详情与历史 · Komikku 视觉对齐

任务分支：`feat/komikku-shell-visual`，基线 `6fef228`（`提取漫画阅读器到 :shared:features`）。
对照原版：本地 `reference/komikku`，固定参考提交 `383ffaa81667f45fc6c2975edb1760a7606531d3`；
原版文件均相对于 `reference/komikku/app/src/main/java/eu/kanade/`。

本任务只改 A 范围：`shared/design-system` 的主题、导航与共享视觉组件，
`shared/app` 的 `AppScaffold.kt` 外壳布局、`browse/` 与 `history/` 表现层、对应 app 字符串与非阅读器 UI 测试。
`AppScaffold.kt` 中的 `ComicReaderRoute` 维持既有调用和全屏行为；沿用 Navigation 3 的标签状态、
返回栈与现有服务。C 的设置存储接线、B 的 Viewer 接线由主 agent 处理。

## 对照实现

| 对齐项 | 原版参考 | 本项目实现 |
| --- | --- | --- |
| 五栏顺序、底栏/导航轨、选中态、角标、主题、安全区 | `tachiyomi/ui/home/HomeScreen.kt`、`presentation/theme/TachiyomiTheme.kt` | `NavTab` 顺序 Library/Updates/History/Browse/More 不变；`ReaderNavigationBar`/`ReaderNavigationRail` 改用 Material 3 `NavigationBar`/`NavigationRail` 容器（与原版同类容器一致），选中指示、Updates/Browse 角标、五栏顺序保留；`ReaderTheme` 采用 Komikku `TachiyomiColorScheme` 的浅色/深色色板并增加 AMOLED 深色选项；`AppScaffold` 维持底栏/导航轨按 720 dp 切换、内容内边距按导航归属拆分、全屏阅读器隐藏导航 |
| 来源、扩展、搜索、扩展详情 | `tachiyomi/ui/browse/BrowseTab.kt`、`presentation/browse/SourcesScreen.kt`、`ExtensionsScreen.kt`、`BrowseSourceScreen.kt`、`ExtensionDetailsScreen.kt` | `BrowseScreen` 的分组头改为 `KomikkuListGroupHeader`（semibold bodyMedium + onSurfaceVariant）；来源行/扩展行改为 `KomikkuBrowseRow`（40 dp 图标占位 + bodyMedium 标题 + 次级行 + Latest/安装/更新/卸载/详情动作），扩展更新箭头 `→ x.y` 与 NSFW 标记保留；仓库预览沿用 Venera 流程（`ExtensionStoresScreen` + `StoreCatalogDialog`），用 Komikku 的列表与状态样式呈现；搜索结果行改为封面占位 + 标题/副标题；扩展详情改为居中头图/名称/版本-语言-仓库信息行 + 卸载/更新 + 来源列表 |
| 作品头部、目录行 | `presentation/manga/MangaScreen.kt`、`presentation/manga/components/MangaChapterListItem.kt` | `SourceDetailScreen` 改为封面占位 + titleLarge 标题 + 副标题/来源 + 可展开简介（Show more/Less）+ 开始/继续按钮 + `共 N 章`分组头 + 目录行（`start 16 / top 12 / end 8 / bottom 12`，标题 bodyMedium + 章节 id 次级行），加载/空态/失败重试保留，直接点目录进入阅读器 |
| 历史分组、加载、空态及失败重试 | `presentation/history/HistoryScreen.kt` | 分组头改为 `KomikkuListGroupHeader`（今天/昨天/日期/未知时间）；历史行改为 96 dp 固定行（56×80 封面占位 + semibold bodyMedium 标题 + 章节·第 N 页次级行 + 来源行 + 继续按钮）；加载/空态/无结果/失败重试保留，历史续读动作（`onContinue` → `ComicReaderRoute`）与目录直达不变 |

共享视觉组件新增 `shared/design-system/.../KomikkuLists.kt`：
`KomikkuListGroupHeader`（对应 `ListGroupHeader`）、`KomikkuBrowseRow`（对应 `BaseBrowseItem`）、
`KomikkuIconPlaceholder`/`KomikkuCoverPlaceholder`（对应无图时的图标/封面槽位尺寸与节奏）。

字符串新增（中英）：`browse_nsfw_short`、`browse_show_more`、`browse_show_less`、
`extension_details_version_title`、`extension_details_language_title`、`extension_details_store_title`。

## 验收

### 1. 截图矩阵（420 dp / 1280 dp × 浅色/深色）

渲染方式：JVM `ImageComposeScene` 离屏渲染（`KomikkuShellScreenshotsTest`），
`Density(1f)`，420 dp 走底栏、1280 dp 走导航轨。浅色/深色由 `ReaderTheme(darkTheme=…)` 切换，
与原版 `TachiyomiTheme` 的浅色/深色对照。原版设备实拍未在本任务执行
（无可用原版 Android 设备；对照以参考提交 `383ffaa` 的源码与布局数值为准，
画布尺寸与主题/语言/字体比例在两侧保持一致：语言 zh_CN、默认字体比例、可用画布宽度 420/1280 dp）。

截图同时存放在构建输出 `build/komikku-a/` 与本文档目录 `screenshots/`（已提交，便于审阅）。

| 场景 | 420 浅色 | 420 深色 | 1280 浅色 | 1280 深色 |
| --- | --- | --- | --- | --- |
| 外壳（书架占位 + 底栏/导航轨） | `screenshots/a-shell-420-light.png` | `screenshots/a-shell-420-dark.png` | `screenshots/a-shell-1280-light.png` | `screenshots/a-shell-1280-dark.png` |
| 来源（Sources） | `screenshots/a-browse-sources-420-light.png` | `screenshots/a-browse-sources-420-dark.png` | `screenshots/a-browse-sources-1280-light.png` | `screenshots/a-browse-sources-1280-dark.png` |
| 扩展（Extensions） | `screenshots/a-browse-extensions-420-light.png` | `screenshots/a-browse-extensions-420-dark.png` | `screenshots/a-browse-extensions-1280-light.png` | `screenshots/a-browse-extensions-1280-dark.png` |
| 作品详情 | `screenshots/a-detail-420-light.png` | `screenshots/a-detail-420-dark.png` | `screenshots/a-detail-1280-light.png` | `screenshots/a-detail-1280-dark.png` |
| 历史 | `screenshots/a-history-420-light.png` | `screenshots/a-history-420-dark.png` | `screenshots/a-history-1280-light.png` | `screenshots/a-history-1280-dark.png` |

状态组（420 浅色）：加载 `screenshots/a-state-browse-loading-420-light.png`、
失败 `screenshots/a-state-browse-error-420-light.png`、
浏览空态 `screenshots/a-state-browse-empty-420-light.png`、
历史空态 `screenshots/a-state-history-empty-420-light.png`、
历史失败 `screenshots/a-state-history-error-420-light.png`、
详情失败 `screenshots/a-state-detail-error-420-light.png`、
扩展详情 `screenshots/a-state-extension-details-420-light.png`。

### 2. 跨断点与主流程复跑

- 同一窗口来回跨越 720 dp 断点：由 `AppShellStateRetentionTest`（窄↔宽滚动保留、各标签独立滚动位）
  与 `VeneraBrowseFlowTest.narrowAndWideLayoutsKeepBrowseState`（719→720 dp 后当前标签、子路由、
  查询与结果行保留）覆盖，测试通过。
- 「仓库预览 → 安装 → 搜索 → 详情 → 章节」：`VeneraBrowseFlowTest.browseToSearchToDetailThroughRealShell`
  用真实 QuickJS + MockEngine 全链路复跑（预览→安装→来源搜索→`comic.loadInfo` 详情→章节行→返回保留搜索结果→跨标签返回仍在详情栈），通过。
- 「历史 → 继续阅读 → 返回历史」：`VeneraHistoryFlowTest.historyRestoresSavedChapterAndPageAfterRestart`
  复跑（阅读到第 3 页→历史离线渲染→从历史继续→返回历史标签），通过；
  另有源丢失与章节丢失的失败态覆盖（行保留、错误提示、进度不丢），通过。

### 3. 验收命令与结果

在任务工作树根目录运行（`REF` 指向主工作树的 `reference/Venera-Next`）：

```bash
REF='/Users/kurisu/project x/reference/Venera-Next'
./gradlew :shared:app:jvmTest --rerun \
  :shared:design-system:assembleAndroidMain :apps:android:assembleDebug \
  :apps:desktop:classes :shared:app:linkDebugFrameworkIosSimulatorArm64 \
  -PveneraReferenceDir="$REF" -PreaderScreenshotDir="$PWD/build/komikku-a"
```

结果：`BUILD SUCCESSFUL`。`shared/app/build/test-results/jvmTest/` 共 30 个套件，0 失败/0 错误。
其中任务点名的三个套件：

- `AppShellStateRetentionTest`：2/2 通过（`scrollSurvivesNarrowToWideAndBack`、`eachTabKeepsItsOwnScrollPosition`）。
- `VeneraBrowseFlowTest`：7/7 通过（含 `browseToSearchToDetailThroughRealShell`、`narrowAndWideLayoutsKeepBrowseState` 等）。
- `VeneraHistoryFlowTest`：3/3 通过（含 `historyRestoresSavedChapterAndPageAfterRestart` 等）。

## 仍存在的原版差异（非本任务范围）

- 书架、更新、更多仍是占位入口与外壳（`TabRootScreen`/`DetailScreen`），仅对齐了入口与外壳；
  真实收藏/更新/下载/设置/追踪/备份等功能未实现。
- 浏览仅有来源/扩展两页；原版的 Feed、迁移、全局搜索、源过滤、保存的搜索等未实现。
- 详情页无真实封面加载（Venera 夹具 `cover` 为空时用首字占位）、无收藏/追踪/分类/分享/下载入口、
  无选择模式与滑动操作；章节行仅显示标题 + 章节 id 次级行（无已读点、书签、下载态）。
- 历史无封面加载（同上占位）、无选择模式/批量删除/过滤器；分组日期为本地日历天（与原版 `relativeDateText` 一致）。
- 主题仅实现默认 `TachiyomiColorScheme` + AMOLED 开关；原版的 Monet/Custom/Catppuccin 等十余套主题、
  字体排印 Expressive、封面取色主题与设置持久化由主 agent / C 接线。
- 导航图标为静态 outline/selected 资源；原版为 `AnimatedImageVector`（选中态为动画终帧，视觉一致，
  切换无过渡动画）。标签重选仅透出事件（`onTabReselect`），未做原版式的重选滚动到顶/打开全局搜索。
