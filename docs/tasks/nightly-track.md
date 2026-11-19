# 夜间轨任务书：满载独立批次（DeepSeek 4.1 Flash / 云端）

你负责 Koyura 漫画阅读器一个完整的夜间交付批次：**七个任务按难度递进排列，每个独立可交付，做不完就停在完成点**。主 agent 不在线；每一步都有仓库内模板或克隆来的原版源码可对照，**卡住就如实登记并跳到下一任务，不要发明机制**。

## 工作区与产出

```bash
git clone https://github.com/Yuki-alice/koyura.git && cd koyura
git checkout -b feat/nightly-batch origin/main
# 参考仓库（只读，不提交、不入库；钉死与本项目文档同版）：
git clone https://github.com/komikku-app/komikku reference/komikku
git -C reference/komikku checkout 290267dd4c99f5b46812240fb0e4bc6b3ee8c562
git clone https://github.com/CyrilPeng/Venera-Next reference/Venera-Next
git -C reference/Venera-Next checkout c0c45979d453c2a8740c45f08e95997c90717629
```

- 开场先读根目录 `AGENTS.md`（红线与提交规范）与 `docs/verification.md`（验收基线）。
- **设计白皮书已入库**：`docs/design-whitepaper/`（74 张实机截图 + 令牌规格；`07-页面规格.md` 按页编号给骨架，`assets/phone|tablet/` 截图同名对应）。
- 全部提交落在 `feat/nightly-batch`；结束时推该分支到 origin 并覆盖写 `docs/tasks/nightly-report-latest.md`。`main` 只读。

## 执行守则

1. **照抄优先**：每任务列了模板路径，先读模板再动手；结构与命名跟随模板。
2. **小步提交**：每完成一个子步骤按 `AGENTS.md` 规范提交（`type(scope): 中文主题`，如实描述）。
3. **地盘**：可改 `shared/features`、`shared/app`（屏幕与装配）、`shared/design-system`；禁改 `shared/domain`、`shared/data`、`shared/source-runtime`、`shared/network`、`gradle/`、`docs/`（交付报告与新增资源文件除外）。
4. **禁库**：不引入 Koin / MVIKotlin / androidx ViewModel / Multiplatform Settings / Kermit / Turbine / Coil 3 / ZoomImage / Voyager / Decompose / Room / Retrofit / Paging 3。DI 维持手动构造器注入 + `CompositionLocal`。
5. 不重构无关代码；与现状不符时保留现状、报告登记。

## 环境验证

不要求三端构建（主 agent 晨间统一跑）。每任务交付前跑新增测试类（全部 Fake 驱动，不依赖外部夹具）：

```bash
./gradlew :shared:features:jvmTest --tests '<新增类>'   # JAVA_HOME 指向 JDK 21
./gradlew :shared:app:jvmTest --tests '<新增类>'
```

仓库部分既有 `Venera*` 测试需要本地夹具，云端缺失时失败属环境限制，不要修它们；完全无法构建就写明「未验证」。

---

## 任务一：分类管理页（More → 独立页面，接真实书架契约）

白皮书 `docs/design-whitepaper/07-页面规格.md` §7.11 + 截图 `assets/phone/31_categories.jpg`。现状：书架已有分类 CRUD 全套——`shared/features/src/commonMain/kotlin/library/LibrarySheets.kt:456` 起的「分类管理」弹层与 `LibrarySession` 的 `addCategory/renameCategory/setCategoryHidden/setCategoryOrder/removeCategory`（含测试）。

1. `shared/features/categories/CategoriesScreen.kt`：56dp 行（名称 bodyMedium + 隐藏/默认标记），新建/重命名/删除对话框照抄 `LibrarySheets` 分类区的既有实现。
2. 排序用行尾上移/下移按钮调 `setCategoryOrder`（拖拽不做，报告登记）。
3. 数据走 `LocalLibraryRepository.current`（真实契约，不建 Fake）；空态照抄 `updates/UpdatesScreen.kt` 的 EmptyScreen。
4. More 接线照抄 `MoreScreen.kt` 的 `onOpenDownloadQueue` 行回调模式 + app 层子路由（照下载队列的挂载分支依样新增）；双语资源照 `more.xml`。
5. UI 测试覆盖：空态/列表/新建/重命名/删除确认/上下移，照 `UpdatesMoreUiTest.kt` 驱动方式。

## 任务二：统计页（More → 独立页面，对 Fake）

白皮书 §7.10 + `assets/phone/30_statistics.jpg`：四张 SectionCard——Overview、Entries、Chapters、Trackers；卡片间距 8dp，标题在卡片外；数值 titleLarge Bold、说明 bodyMedium 0.78 alpha、图标 primary 置底。

1. **先看原版**：在 `reference/komikku` 里定位统计页实现（`grep -r "StatisticsScreen" app/src/main/java`），按它的四张卡字段清单提炼 Fake 数据形状（原版字段直接沿用，不要自造）。
2. `shared/design-system` 新增 `SectionCard` 组件（surfaceContainer 容器 + 圆角走 DesignTokens）。
3. `shared/features/statistics/`：Models + Fake + Session（照抄 `updates/UpdatesSession.kt` 三态模式）+ Screen（骨架抄 `more/MoreScreen.kt`、顶栏抄 `updates/UpdatesScreen.kt`）。
4. More 接线与双语资源同任务一模式。
5. 测试：Session 三态 + UI 结构各一例。

## 任务三：历史页多选与批量删除

模板：`shared/features/src/commonMain/kotlin/library/LibraryScreen.kt` 的完整多选（`selection`、底部动作条 `LibrarySelectionBarTag`、计数/反选/清除、返回键消费）；历史现状在 `shared/app/src/commonMain/kotlin/history/`（已有 `delete(entry)` 与 `clearAll`，无多选）。

1. `HistorySession` 加 `selection: Set<Pair<String, String>>`（键 `(sourceKey, workId)`）+ `toggleSelect/selectAllVisible/clearSelection/consumeBack`（顺序照 `LibrarySession.consumeBack`）+ `deleteSelected()`（循环 `delete`，单项失败记 `actionError` 不中断整批）。
2. `HistoryScreen`：长按进入多选（照书架）、选择态顶栏计数、底部动作条（删除/反选/清除，结构照书架）、删除 AlertDialog 确认；选择态隐藏外壳在 `AppScaffold.kt` 照书架 `selection` 的同一处逻辑接入。
3. 测试：Session 选择/批量/失败续删/返回键 + UI 多选一例（照书架多选测试驱动）。

## 任务四：智能更新设置（契约先行，对照原版）

引擎已交付更新扫描的跳过规则偏好（读 `shared/domain/src/commonMain/kotlin/updates/LibraryUpdates.kt` 与 `shared/data/.../updates/LibraryUpdateScanner.kt`——`preferences()/setPreferences()` 的偏好模型即契约），**UI 尚未接入**。

1. 对照原版：`reference/komikku` 的书架设置更新区（`grep -rn "skip" app/src/main/java/eu/kanade/presentation/library/LibrarySettingsDialog.kt` 一带）确认开关组语义（跳过已完结/未读积压/只取一次/标题排除等）。
2. `shared/features/updates/` 新增 `UpdatePolicyRepository` 契约（`policy(): UpdatePolicy` + `apply(policy: UpdatePolicy)`，字段按引擎偏好模型逐字对齐）+ `FakeUpdatePolicyRepository`；`UpdatePolicy` 数据类含全部开关与排除词列表。
3. UI：Updates 页顶栏溢出菜单加「智能更新设置」入口（照更新错误入口的溢出模式），设置对话框开关组 + 标题排除多行输入。
4. 测试：Session/策略映射 + 对话框 UI 各一例。生产桥接由主 agent 晨间完成（同账号区模式）。

## 任务五：全局搜索（真实聚合，接通全应用禁用入口）

现状：书架空态与浏览顶栏的「全局搜索」按钮已存在但 `onGlobalSearch = null` 即禁用。对照原版：`reference/komikku` 的 `GlobalSearchScreen` 与其 ScreenModel（`grep -rl "GlobalSearch" app/src/main/java/eu/kanade/presentation`）——按源并行搜索、部分失败展示、空态。

1. `shared/app` 新增 `browse/globalsearch/`：`GlobalSearchService`（对**真实** `BrowseService` 做扇出：`coroutineScope { sources.map { async { ... } } }` 逐源调既有搜索，返回每源结果或异常）+ `GlobalSearchSession`（照 BrowseSession 模式）+ `GlobalSearchScreen`（搜索框照 `SearchField`、按源分组、组头为源名+状态、作品行照 `BrowseRow`）。
2. 结果行点击进既有作品详情路由；进行中显示每源加载态，失败源显示错误行与重试该源。
3. 装配层接通：书架空态与浏览顶栏的 `onGlobalSearch` 回调推新路由（`AppScaffold`/`LibraryTab` 现有接线处照详情路由模式）。
4. 测试：Session 并发扇出/部分失败/空结果（用 Fake BrowseService 注入）+ UI 分组一例。

## 任务六：阅读器背景四档 + 点击区设置（契约已就位）

契约：`shared/domain/.../reader/preferences/ReaderPreferences.kt` 的 `ReaderBackground`（AUTOMATIC 默认/BLACK/GRAY/WHITE），主 agent 已扩展好 `ReaderSettings/Override/Patch` 全链（含旧配置兼容测试）。根背景现状：`shared/features/src/commonMain/kotlin/reader/comic/ReaderScreen.kt` 用 Automatic 固定映射并**留有四档耦合注释**（Black/Gray 须用 `ReaderTheme(darkTheme = true)` 包 chrome——原版语义见 `reference/komikku` 的 `util/system/ContextExtensions.kt:111-118`）。

1. 根背景改读解析后的 `settings.background`：AUTOMATIC 维持现映射（暗 `#FF202125`/亮白）；BLACK 纯黑；GRAY `#FF202125`（两档同时用暗色 `ReaderTheme` 包 chrome，注释已指路）；WHITE 纯白（chrome 跟随主题）。
2. 阅读设置对话框（`reader/comic/chrome/ReaderSettingsDialog.kt`）加背景四档选择段（照对话框内既有选项段的样式）。
3. 点击区自定义：`ComicViewerOptions` 已有点击区类型与逻辑，但未暴露到设置——对照 `reference/komikku` 的阅读设置 tap-zone 段（`presentation/reader/settings/` 下搜索 tap），在设置对话框加点击区选择（默认维持现行为）。
4. 全局/作品覆盖两条写路径照设置对话框既有的 `writeGlobal/writeOverride` 用法（对话框里已有模式）。
5. 测试：背景映射四档像素/色值断言 + 设置对话框选项读写一例。

## 弹性任务七：详情页章节批量下载（时间不足整任务跳过）

详情页章列表（`shared/app/src/commonMain/kotlin/browse/SourceScreens.kt`）加长按多选 + 底部「下载所选」动作，逐章调 `ExtensionSession.downloads.enqueue`（装配注入，照任务五的接线模式）。下载按钮状态流照下载队列页既有消费方式。UI 测试一例。

---

## 交付报告（结束时覆盖写 `docs/tasks/nightly-report-latest.md`）

1. 逐任务完成状态与提交清单（SHA + 主题）。
2. 验证情况：跑过的命令与结果；环境限制如实列出。
3. 与任务书不符的现状偏差（文件、现象、处置）。
4. 待主 agent 裁决项（生产桥接清单：任务四的策略仓储、任务七的下载装配；分类拖拽；统计字段如与原版有出入等）。
