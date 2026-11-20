# 夜间轨交付报告（满载独立批次）

分支 `feat/nightly-batch`（从 `origin/main` 拉出）。任务一至六已交付，弹性任务七整任务跳过（见第 4 节）。全部提交按 `AGENTS.md` 规范分组，共 26 个提交，`main` 未动。

参考仓库已按任务书钉死：`reference/komikku` @ `290267dd4c99f5b46812240fb0e4bc6b3ee8c562`、`reference/Venera-Next` @ `c0c45979d453c2a8740c45f08e95997c90717629`（`/reference/` 在 `.gitignore` 内，不入库）。

## 1. 逐任务状态与提交清单

### 任务一：分类管理页（已完成）

| 提交 | 内容 |
| --- | --- |
| `e84796a` `feat(categories): 分类管理独立页接真实书架契约` | `features/categories/CategoriesScreen.kt`：56dp 行（`DesignTokens.Sizes.ListItemHeight`），行体点击重命名，行尾 ↑/↓ 调 `setCategoryOrder`，隐藏/删除按钮；扩展 FAB 新建；空态 `(˘･_･˘)` 照 Updates 空态；数据走 `LocalLibraryRepository`（真实契约，无 Fake） |
| `01bee00` `feat(shell): 更多页分类入口接通独立页面路由` | `MoreScreen` 加 `onOpenCategories`，`AppRoute` 新增 `CategoriesRoute` 并注册 SavedState，`AppScaffold` 挂 `entry<CategoriesRoute>` |
| `00042e6` `test(categories): 分类管理页 UI 回归覆盖空态与增删改排` | `CategoriesUiTest` 4 例：空态、列表渲染、新建/重命名对话框、删除确认 + 上下移 |

### 任务二：统计页（已完成）

| 提交 | 内容 |
| --- | --- |
| `07c0202` `feat(design-system): 新增 SectionCard 分组卡片容器` | `SectionCard`：`surfaceContainer` + `Radius.Large` 圆角 + `Spacing.Medium` 内边距 |
| `1668a78` `feat(statistics): 统计页四张 SectionCard 与 Fake 数据源` | Models + `FakeStatisticsRepository` + `StatisticsSession`（三态，照 `UpdatesSession`）+ `StatisticsScreen`（标题在卡片外、三列指标、数值 `titleLarge` Bold、说明 `bodyMedium` 0.78、Overview 段 primary 图标）+ 双语 `statistics.xml` |
| `8dcb029` `feat(shell): 更多页统计入口接通独立页面路由` | `onOpenStatistics` + `StatisticsRoute` + `statisticsSession` 装配 |
| `32987b3` `test(statistics): 会话三态、格式串与四卡结构回归` | Session 3 例（Ready/Failed 保留上次结果/取消不算失败）+ UI 2 例（四段指标齐全、失败态重试恢复） |
| `1e5b5c1` `fix(statistics): 指标列加权重修饰符按等分三列排布` | `MetricColumn` 收 `modifier` 参数，调用侧传 `Modifier.weight(1f)` |

格式串：`formatReadDuration`（`4m 13s`）、`formatMeanScore`（无均分显示 `N/A`）。

### 任务三：历史页多选与批量删除（已完成）

| 提交 | 内容 |
| --- | --- |
| `03eae5b` `feat(history): 会话支持多选集合与批量删除` | `selection: Set<Pair<String,String>>` + `isSelected/toggleSelect/selectAllVisible/invertSelection/clearSelection/wantsBack/consumeBack/deleteSelected`；逐条删除、失败项留在选择集、`actionError` 记录不中断整批；刷新时选择集收敛 |
| `3ffb5f2` `feat(history): 历史页长按多选、选择顶栏与批量动作条` | 行改 `combinedClickable`（长按进多选、选择态点击切换），选择态行显 Checkbox 并隐藏行级按钮；顶栏计数、底栏删除/反选/清除（`tonal` Surface + 3dp）；删除前 `AlertDialog` 确认 |
| `f058027` `feat(shell): 历史多选隐藏外壳并消费返回键` | `hideChrome` 加 History 分支；`BackToLibraryHandler` 增 `consumeHistory`，返回键先清选择 |
| `71fe126` `test(history): 多选、批量删除与失败续删回归` | Session 4 例（选择切换/全选反选/失败续删/返回键）+ UI 2 例（长按进选择态、删除所选确认） |
| `0b4c0b9` `fix(history): 补齐选中文案资源导入` | 7 条选中文案补 `Res.string` 导入（生成访问器按名导入，非通配） |

### 任务四：智能更新设置（已完成，生产桥接待接线）

| 提交 | 内容 |
| --- | --- |
| `52e6a78` `feat(updates): 智能更新策略契约与内存实现` | `UpdatePolicy`（`enabled/skipCompleted/skipWithUnread/skipNotStarted/skipOutsideReleasePeriod/excludedTitleTerms`）+ `normalized()` + `toPreferences()/toUpdatePolicy()` 与引擎 `SmartUpdatePreferences` 逐字映射；`UpdatePolicyRepository` 契约 + `FakeUpdatePolicyRepository` + 三态会话 |
| `7abcda9` `feat(updates): 更新页溢出菜单接入智能更新设置对话框` | 溢出菜单「智能更新设置」入口；对话框 5 个开关行 + 标题排除多行输入（按换行/逗号/全角逗号切分）；全局/作品两条写路径照既有 `writeGlobal/writeOverride` 模式 |
| `7a897b6` `test(updates): 策略三态、归一化、引擎映射与对话框回归` | Session 5 例 + UI 2 例（溢出菜单打开并保存、取消不写） |

### 任务五：全局搜索（已完成）

| 提交 | 内容 |
| --- | --- |
| `cf029d4` `feat(browse): 全局搜索页按源并发扇出与分组结果` | `GlobalSearchService` 对真实 `BrowseService` 扇出（`coroutineScope { sources.map { async } }`，单源失败折进该组）、`GlobalSearchSession`、`GlobalSearchScreen`（`SearchField` + 提交键、按源分组、组头源名+状态+单源重试、行点击进详情） |
| `c01c668` `feat(shell): 接通书架空态与浏览顶栏的全局搜索入口` | 书架空态与浏览顶栏按钮的 `onGlobalSearch` 由 `null`（禁用）改为推 `GlobalSearchRoute` |
| `2a17737` `test(browse): 全局搜索并发、部分失败、空结果与分组回归` | Session 5 例（并发扇出、部分失败、空结果、重试单源、清空）+ UI 2 例 |
| `5605415` `fix(browse): 全局搜索会话改注入浏览服务而非会话` | `GlobalSearchSession` 收 `BrowseService?`；装配处从 `AppScaffold` 参数下传（`BrowseSession.service` 私有，不能当服务用） |

### 任务六：阅读器背景四档 + 点击区（已完成，点击区未持久化）

| 提交 | 内容 |
| --- | --- |
| `97bd970` `feat(reader): 根背景改读四档偏好并对暗档强制暗色 chrome` | 根背景读 `global.withOverride(override).background`；`readerBackgroundColor`（AUTOMATIC 暗 `#FF202125`/亮白、BLACK 纯黑、GRAY `#FF202125`、WHITE 纯白）；BLACK/GRAY 用 `ReaderTheme(darkTheme = true)` 包 chrome |
| `519149d` `feat(reader): 设置对话框加背景四档与点击区选择` | 全局段与覆盖段各加背景枚举行（覆盖段含「跟随全局」清除项）；新增「点击区」段：类型枚举 + 反转开关 |
| `dc5df54` `test(reader): 四档背景色值与设置对话框读写回归` | 背景色值 3 例 + 对话框读写 3 例（全局写入、覆盖写入、覆盖清除回落默认） |
| `c6ff598` `test(reader): chrome 测试宿主消费点击区动作` | `ChromeTestHost` 补 `SetTapZone`/`SetInvertTapZone` 分支 |
| `45ed830` `fix(reader): 根背景取全局偏好与作品覆盖合并后的值` | 合并结果取 `.background`（原先引用了不存在的 `effective`） |

### 弹性任务七：详情页章节批量下载（整任务跳过）

按任务书「时间不足整任务跳过」处理，未产生任何代码改动。跳过理由：批量下载需要把 `ExtensionSession.downloads.enqueue` 注入详情页并新增长按多选与底部动作条，装配链路（下载队列状态流 + 逐章入队）与任务五不同源，剩余时间不足以做完并跑通回归，故整任务留待主 agent 排期（见第 4 节）。

### 收尾

| 提交 | 内容 |
| --- | --- |
| `2ff86b7` `test(app): 新增测试类包名对齐生产包路径` | 8 个新增测试类的 `package` 与生产类对齐（`app.koyura.features.*` / `app.koyura.app.*`），此前按目录名声明导致未解析 |

## 2. 验证情况

环境与任务书假设的差异见第 3 节。实际跑过的命令与结果：

```bash
# 编译门（JDK 21）
JAVA_HOME=/opt/tools/jdk-21.0.13+9 ./gradlew :shared:features:compileKotlinJvm   # BUILD SUCCESSFUL
JAVA_HOME=/opt/tools/jdk-21.0.13+9 ./gradlew :shared:app:compileKotlinJvm        # BUILD SUCCESSFUL

# 桌面 Compose UI 测试需要 X 显示，先起虚拟屏
Xvfb :99 -screen 0 1920x1080x24 &
DISPLAY=:99 JAVA_HOME=/opt/tools/jdk-21.0.13+9 ./gradlew :shared:features:jvmTest   # 101 例，0 失败
DISPLAY=:99 JAVA_HOME=/opt/tools/jdk-21.0.13+9 ./gradlew :shared:app:jvmTest        # 324 例，0 失败
```

本轮新增 35 例（features 11 + app 24），全部 Fake 驱动，无外部夹具：

| 模块 | 测试类 | 例数 |
| --- | --- | --- |
| features | `StatisticsSessionTest` / `UpdatePolicySessionTest` / `ReaderBackgroundTest` | 3 / 5 / 3 |
| app | `CategoriesUiTest` / `StatisticsUiTest` / `HistorySelectionTest` / `HistorySelectionUiTest` / `UpdatePolicyUiTest` / `GlobalSearchSessionTest` / `GlobalSearchUiTest` / `ReaderSettingsDialogTest` | 4 / 2 / 4 / 2 / 2 / 5 / 2 / 3 |

既有回归未受影响：`VeneraHistoryFlowTest`（3 例）等全部既有用例在本机全绿，不需要外部夹具，任务书预留的「夹具失败」未出现。

**未验证项（如实声明）**：

- **三端构建未跑**：本机无 Android SDK，`:apps:android:assembleDebug`、`:apps:desktop:classes`、iOS framework 链接均未执行。
- **实机/真屏视觉未核对**：统计页与分类页只做了 JVM 离屏渲染下的语义断言（testTag 存在性、文本、结构），未与白皮书截图逐像素比对；背景四档只断言色值常量，未截屏比色。

## 3. 与任务书不符的现状偏差

1. **统计页在原版不存在**：`reference/komikku` @ 钉死版 `grep -rl "StatisticsScreen"` 无结果，仅有 `exh/md/dto/StatisticsDto.kt`（扩展 API 的 DTO，不是统计页）。四张卡字段取自 `docs/design-whitepaper/07-页面规格.md` §7.10 与 `assets/phone/30_statistics.jpg` 的标签，字段名与粒度是照截图推断的，需与主 agent 手上的原版对齐（见第 4 节）。
2. **点击区无持久化契约**：`shared/domain/.../reader/preferences/ReaderPreferences.kt` 的 `ReaderSettings/Override/Patch` 没有 tap-zone 字段，`shared/domain` 属禁改区，因此点击区选择（`ComicTapZoneMode` + 反转）只落在 chrome 会话的 `rememberSaveable(sourceKey, workId)`，进程内有效、不落盘、不参与全局/作品覆盖写路径。背景四档走的是真实偏好链（全局 + 覆盖两条写路径都通）。
3. **分类拖拽未做**：按任务书第 2 条用行尾上移/下移按钮调 `setCategoryOrder`，未实现拖拽排序。
4. **任务四生产桥接未接**：`LocalUpdatePolicyRepository` 默认 `null`，`AppScaffold` 未注入生产实现，因此对话框在未注入时读不到策略（`FakeUpdatePolicyRepository` 只在测试与预览里用）。真实桥接需要把 `LibraryUpdateScanner.preferences()/setPreferences()` 包成仓储（禁改 `shared/data`，只能在 `shared/app` 装配层做适配）。
5. **全局搜索的服务注入口径**：`BrowseSession` 的 `service` 是私有的，装配处改为从 `AppScaffold` 的 `browseService` 参数直接下传 `BrowseService?`；`LocalBrowseService` 实际未被 provide（默认 `null`），所以走构造参数而不是读 `CompositionLocal`。
6. **环境与任务书假设不符**（均为本机限制，未改仓库文件）：
   - 仓库文档写 JDK 21 在 `~/devtools/jdk-21`，本机无此路径；实际装在 `/opt/tools/jdk-21.0.13+9`（adoptium 官方包走清华镜像下载，两次直连下载损坏后换镜像）。
   - 桌面 Compose UI 测试在无显示环境下抛 `ExceptionInInitializerError: X11GraphicsEnvironment`，28 例误失败；用 `Xvfb :99` + `DISPLAY=:99` 后全绿。
   - `dl.google.com` 在 Gradle 默认 10s 连接超时下频繁超时（curl 直连正常），需 `-Dorg.gradle.internal.http.connectionTimeout=60000 -Dorg.gradle.internal.http.socketTimeout=120000`；`--offline` 缺 `sqldelight:compiler-env`、`skiko-awt-runtime-all` 等构件，首拉必须联网。
   - `~/.gradle/init.gradle` 存在语法错误（`mavelCentral()`），无法编辑，临时改名为 `init.gradle.disabled` 才能配置阶段通过。这是本机用户目录改动，不在仓库内，交付后建议主 agent 复核是否影响其他构建。

## 4. 待主 agent 裁决 / 接线项

1. **任务四策略仓储生产桥接**：`UpdatePolicyRepository` 需要包一层 `LibraryUpdateScanner.preferences()/setPreferences()` 的适配器并在 `AppScaffold`（或 `ExtensionApp`）provide 给 `LocalUpdatePolicyRepository`；字段已与 `SmartUpdatePreferences` 逐字对齐，映射函数 `toPreferences()/toUpdatePolicy()` 已就位。
2. **点击区是否进 domain**：若要持久化与全局/作品双写，需在 `shared/domain` 的 `ReaderSettings/Patch/Override` 增设 tap-zone 字段（含旧配置兼容测试），本次受禁改约束未动；当前只在会话内生效。
3. **统计页字段核对**：四段（Overview/Entries/Chapters/Trackers）字段来自白皮书截图标签，原版无实现可对照；若主 agent 手上有更准的原版字段表，`StatisticsModels.kt` 与 `FakeStatisticsRepository` 需要同步改字段名与口径。
4. **任务七下载装配**：详情页章节批量下载（长按多选 + 底部「下载所选」逐章 `enqueue`）整任务未做，需 `ExtensionSession.downloads` 的装配注入与下载队列状态流消费方式一起排期。
5. **分类拖拽排序**：若要在 56dp 行上做长按拖拽（而非上下移按钮），建议与书架分类弹层一起统一交互。
