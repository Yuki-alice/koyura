# 夜间轨任务书：独立 UI 批次（DeepSeek 4.1 Flash / 云端）

你负责 Koyura 漫画阅读器的一个独立 UI 交付批次。这是一条夜间单轨：主 agent 不在线，无人回答问题。任务全部有仓库内现成模式可照抄；**任何一步卡住或与任务书描述不符，就停在当前完成点、如实写报告、继续下一个任务，不要发明新机制、不要绕过障碍。**

## 工作区与产出

- 仓库：`git clone https://github.com/Yuki-alice/koyura.git`，克隆后先通读根目录 `AGENTS.md`（选型红线与提交规范）与 `docs/verification.md`（验收基线）。
- 在新分支 `feat/nightly-batch` 上工作（从 `origin/main` 拉）。`main` 只读：禁止向 `main` 推送、禁止 force push。
- 结束时把 `feat/nightly-batch` 推到 origin，并覆盖写交付报告 `docs/tasks/nightly-report-latest.md`（见文末格式）。
- 本环境没有 `reference/` 参考仓库（未入库），不要试图找它；所需规格已内嵌在本任务书。

## 执行守则

1. **照抄优先**：每个任务都列了模板文件，先读模板再动手，结构与命名跟随模板，不要另起风格。
2. **小步提交**：每完成一个子步骤就按 `AGENTS.md` 的提交规范提交（`type(scope): 中文主题`，主题如实描述改动；禁止「保存进度」类主题）。
3. **不越界**：可以改 `shared/features`、`shared/app` 的屏幕、`shared/design-system`；禁止改 `shared/source-runtime`、`shared/network`、`shared/data`、`shared/domain`、`gradle/`、`docs/`（交付报告除外）。
4. **禁库**：不引入 Koin / MVIKotlin / androidx ViewModel / Multiplatform Settings / Kermit / Turbine / Coil 3 / ZoomImage / Voyager / Decompose / Room / Retrofit / Paging 3。DI 维持手动构造器注入 + `CompositionLocal`。
5. **不确定就保守**：与现状对不上时保留现状、在报告登记，等主 agent 裁决。

## 环境验证（先做一次）

本批次的改动全部是 commonMain 的 UI 与测试，**不要求跑三端构建**（主 agent 晨间统一验证）。验证方式：

```bash
# 环境有 JDK 21 时（JAVA_HOME 指向它）：
./gradlew :shared:features:jvmTest --tests '<你的新增测试类>'
./gradlew :shared:app:jvmTest --tests '<你的新增测试类>'
```

- 只跑你新增的测试类（全部设计为 Fake 驱动、不依赖外部夹具）。仓库里部分既有 `Venera*` 测试需要本地夹具目录，云端缺失时它们失败属**环境限制**，不要修它们，报告里注明即可。
- 环境完全无法构建时：写清「未验证」，照常交付，主 agent 晨间统一跑门。

## 任务一：统计页（More 导航行 → 独立页面，热身任务）

现状：More 页（`shared/features/src/commonMain/kotlin/more/MoreScreen.kt`）的导航行有「统计」入口但目标页未落地。视觉规格（白皮书 7.10）：四张 SectionCard——Overview、Entries、Chapters、Trackers；卡片间距 8dp，标题在卡片外；数值行 titleLarge Bold，说明 bodyMedium 透明度 0.78，图标用 primary 色放卡片底部。

步骤：

1. `shared/design-system` 新增 `SectionCard` 组件：卡片容器（surfaceContainer、圆角走 `DesignTokens`）+ 卡片外标题 + 内部数值行（label：bodyMedium 0.78 alpha；value：titleLarge Bold）。数值行字段清单本任务**不定稿**：每张卡放 2–3 行占位（Fake 提供），报告里登记「字段清单待主 agent 对照原版源码裁决」。
2. `shared/features` 新增 `statistics/`：`StatisticsModels.kt`（不可变 `UiState`：四组卡片的标题与数值行）、`FakeStatisticsRepository.kt`（返回样例数据）、`StatisticsSession.kt`（照抄 `updates/UpdatesSession.kt` 的三态加载模式）、`StatisticsScreen.kt`（照抄 `more/MoreScreen.kt` 的页面骨架与 `updates/UpdatesScreen.kt` 的 TopAppBar 模式）。
3. More 页接线：照抄 `MoreScreen.kt` 中 `onOpenDownloadQueue` 行的回调模式加 `onOpenStatistics`，装配层（`shared/app` 的 More 挂载处）推子路由——照抄下载队列页的子路由接线方式（在 `shared/app` 中找 `DownloadQueue` 的路由键与挂载分支，依样新增）。
4. 双语资源：`values/` 英文默认 + `values-b+zh+CN/` 中文，照抄 `more.xml` 的写法。
5. 测试：`StatisticsSessionTest`（三态 + 数值映射，照抄 `UpdatesSessionTest`）+ app 层 UI 测试一例（照抄 `updates/UpdatesMoreUiTest.kt` 的结构断言模式）。

## 任务二：分类管理页（More 导航行 → 独立页面，接真实书架契约）

现状：书架已有完整分类 CRUD——`shared/features/src/commonMain/kotlin/library/LibrarySheets.kt:456` 起是「分类管理」弹层（新增/重命名/隐藏/删除/排序），`LibrarySession` 有 `addCategory/renameCategory/setCategoryHidden/setCategoryOrder/removeCategory` 全套方法且有测试。本任务把这套能力提升为 More 导航行进入的独立页面（白皮书 7.11：56dp 标准行、删除确认 AlertDialog 280–560dp 宽）。

步骤：

1. `shared/features` 新增 `categories/CategoriesScreen.kt`：列表行 56dp（名称 bodyMedium + 隐藏/默认小标记 chip），底部/顶栏新增动作照抄 `LibrarySheets` 分类区的对话框模式（新建文本框、重命名、删除确认）。
2. 排序：白皮书为拖动手柄，本批次**不做拖拽**，用行尾上移/下移图标按钮调 `setCategoryOrder`（报告登记「拖拽待后续手势轮次」）。
3. 数据走真实契约：页面直接消费 `LocalLibraryRepository.current`（`shared/features/.../library/AccountLocals 同目录的 LibrarySession 模式`），不再另建 Fake；空态照抄 `updates/UpdatesScreen.kt` 的 EmptyScreen 模式（颜文字 + 引导）。
4. More 接线与双语资源同任务一模式。
5. 测试：app 层 UI 测试覆盖（空态/列表/新建/重命名/删除确认/上下移），照抄既有书架分类测试 + `UpdatesMoreUiTest` 的驱动方式。

## 任务三：历史页多选与批量删除

现状：历史页（`shared/app/src/commonMain/kotlin/history/`）已有单条删除（`HistorySession.delete`）与清空（`clearAll`），无多选。书架有完整多选范式：`shared/features/src/commonMain/kotlin/library/LibraryScreen.kt`（`selection` 状态、`LibrarySelectionBarTag` 底部动作条、计数、反选、清选、返回键消费——全部照抄这套结构）。

步骤：

1. `HistorySession` 增加多选状态（`selection: Set<Pair<String, String>>`，键用 `(sourceKey, workId)`）、`toggleSelect`、`selectAllVisible`、`clearSelection`、`consumeBack`（选择态优先于搜索/过滤器，照 `LibrarySession.consumeBack` 的顺序模式）与 `suspend fun deleteSelected()`（循环调 `delete`，任一失败记 `actionError` 不中断整批，完成后重读快照）。
2. `HistoryScreen` 增加多选 UI：长按条目进入（照书架长按模式）、选择态顶栏计数替换标题、底部动作条（删除 + 反选 + 清除，照书架 `LibrarySelectionBar` 的结构、间距与按钮图标）、删除带 AlertDialog 确认（照任务二的确认模式）、选择态下隐藏底栏由装配层读取——在 `shared/app` 的 `AppScaffold.kt` 找书架 `selection` 隐藏外壳的同一处逻辑并接入历史会话。
3. 测试：`HistorySessionTest` 补选择/批量删除/失败续删/返回键消费；UI 测试一例照书架多选测试驱动方式（在 `shared/app/src/jvmTest` 搜索书架多选测试类）。

## 弹性任务四：全局搜索页（对 Fake，时间不足可整任务跳过）

现状：书架空态与浏览顶栏的「全局搜索」按钮已存在但禁用（`onGlobalSearch = null` 即禁用）。目标：独立搜索页，对已装源逐一搜索并按源分组展示（真实聚合引擎由主 agent 后续接入）。

步骤（全部对 Fake）：`shared/features` 新增 `globalsearch/`——搜索框（照 `design-system` 的 `SearchField`）+ 按源分组结果（组头为源名 + 状态（加载/失败/空/结果数），作品行照浏览搜索行的 `BrowseRow` 模式）+ Fake（两源返回样例、一源失败、一源空）。UI 测试覆盖分组与失败展示。**不要**改动聚合并发语义、不要接真实 BrowseService。

## 交付报告（结束时覆盖写）

`docs/tasks/nightly-report-latest.md`，包含四节，全部如实：

1. 逐任务的完成状态（完成/部分完成/跳过）与提交清单（SHA + 主题）。
2. 验证情况：跑过哪些命令、结果；环境限制（夹具缺失/无 JDK）如实列出。
3. 与任务书描述不符的现状偏差（每条：文件、现象、你的处置）。
4. 待主 agent 裁决项（统计字段清单、分类拖拽、任务四取舍等）。
