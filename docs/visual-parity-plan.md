# Komikku 视觉表现层复刻规划 · 视觉 AI 任务书

2026-11-18 · 本文是 [Komikku 对齐计划](komikku-alignment.md) 在**视觉表现层**的执行细化，交给带视觉模型的实现 AI。功能行为与验收流程沿用对齐计划；本文只裁决"看起来像不像"。上一轮 a/b/c 视觉任务（[a-shell](alignment/a-shell/README.md)、[b-viewer](alignment/b-viewer/README.md)、[c-chrome](alignment/c-chrome/README.md)）已交付基线，本轮以白皮书为标尺**复核已有实现、补齐差距、扩展到白皮书覆盖的全部表面**。

## 0. 使命与铁律

**使命**：Koyura 的前端视觉表现层与 Komikku 对齐复刻；实现全部落在 Kotlin Multiplatform + Compose Multiplatform 现代栈上。

1. **复刻机制，不复刻色相**。Komikku 默认主题是 Monet 动态取色 + 封面取色，截图里的粉紫来自壁纸。要复刻的是"语义角色 → 组件"的映射（白皮书 00 章）。自检：切到 Monochrome/Matrix 主题界面仍成立，一换主题就崩说明有硬编码色。
2. **只消费语义令牌**。组件代码中不出现具体 hex；颜色一律 `MaterialTheme.colorScheme.*`，尺寸/圆角/动效来自 design-system 常量。数值出处标注白皮书章节（Code 级）还是推断（Inference 级）。
3. **现代栈重实现，禁止照搬结构**。不复制 `eu.kanade` 的包结构、Android View、Activity 拆分、Voyager 导航；见第 7 节映射表。
4. **只动表现层**。`:shared:source-runtime`、`:shared:data`、`:shared:network` 与各服务契约不碰；字符串新增走双语（`values/` + `values-b+zh+CN/`）。
5. **架构窗口未到**：Koin/MVIKotlin/ViewModel 已定排期但**本轮禁止引入**（迁移窗口在第一轮阅读器对齐验收后，见 [technology-stack.md](technology-stack.md)）。现有 session/state holder + 不可变 `UiState` 模式保持。
6. **命名不含产品名**：代码标识符与文件名按功能命名，禁止 `Komikku*`/`Koyura*` 前缀（历史遗留的清理映射见 P0）；`app.koyura` 基包、安装标识与注释引用除外。规则正文见 [architecture.md](architecture.md)。

**统一基线**：白皮书与本地 Komikku 代码已对齐至 `290267dd4c99f5b46812240fb0e4bc6b3ee8c562`（简称 `290267d`，v1.14.1；见[对齐计划](komikku-alignment.md)）。**数值冲突时以该版本源码为准**；截图复用时核对数据、主题与画布条件，历史记录继续保留原版本。

## 1. 输入材料地图（动手前先读）

| 材料 | 路径 | 用途 |
| --- | --- | --- |
| 设计白皮书 13 章 | `reference/komikku-design-whitepaper-md/`（README 有索引） | 视觉规格唯一权威：01 十原则、02 令牌、03 布局、04 导航、05 动效、06 组件库、07 页面规格、08 状态、09 复刻指南 |
| 全量令牌 JSON | `…/assets/design-tokens.json` | 19 套主题色值 + 字号/间距/圆角/动效/断点，机器可读 |
| 实机截图 74 张 | `…/assets/phone/`（37，720×1440=360dp）、`…/assets/tablet/`（37，1600×1000=1280dp）、`…/assets/crops/`（9 组件特写） | 视觉真值；每张有图注编号，07/10 章引用 |
| Komikku 源码 | `reference/komikku/app/src/main/java/eu/kanade/`（presentation、ui 两层） | 布局/数值核对；`tachiyomi/ui/home/HomeScreen.kt`、`presentation/…` 见各任务 |
| 本项目现有实现 | `shared/design-system/…`（ReaderTheme、Komikku* 组件、navigation/*）、`shared/app/…`（AppScaffold、browse/、history/、library/、navigation/）、`shared/features/…`（library/、reader/comic/ 含 chrome/） | 落点，见各任务"现状与落点" |
| 已有基线 | `docs/alignment/`（a-shell/b-viewer/c-chrome 三组截图与任务书）、`shared/app/docs/`（app-shell/browse-extensions/reader 实现文档） | 复核起点，不重做已通过项 |
| 补充参照 | `reference/komikku实机截图/`（29 张，无标注） | 仅作氛围参照；**对齐以白皮书 assets 为准** |

## 2. 工作流（每个任务的迭代循环）

1. **读规格**：任务书引用的白皮书章节 + 原版参考文件 + 对应 `design-tokens.json` 键。
2. **改代码**：只改任务书声明的落点范围；包名/短目录约定见 [architecture.md](architecture.md)。
3. **跑起来**：`./gradlew :apps:desktop:run`（JDK 21 在 `~/devtools/jdk-21`）。固定夹具数据（`FakeLibraryRepository`、`FakeReaderService`、`:shared:app/src/jvmTest` 的 ViewerFixture 约定）。
4. **成对截图**：窗口 420（窄）/1280（宽）× 浅色/深色，命名沿用 docs/alignment 现有约定（`<屏>-<状态>-<宽>-<主题>.png`），归档 `docs/alignment/<任务>/screenshots/`。
5. **比对**：项目截图 vs 白皮书对应图注并排比对（视觉模型逐项核对：布局结构、密度、颜色语义、圆角、留白、层级），差距回到第 2 步。
6. **勾验收**：任务级截图矩阵 + 全局验收门（第 3 节）+ 章末复刻检查清单。
7. **记录与提交**：差距分析与修复记录写 `docs/reviews/YYYY-MM-DD-visual-<任务>.md`；提交 conventional commits 中文主题（`fix(browse): …`、`feat(library): …`）；测试不绿不算完成（受影响模块 `:shared:app:jvmTest` 等，见 [technology-stack.md](technology-stack.md) 排期段）。

## 3. 全局验收门

以白皮书 [09 复刻指南](../reference/komikku-design-whitepaper-md/09-复刻指南.md) 9.5 的 **30 条验收清单**为整轮出口，不逐条复制到本文；每任务另勾选对应章末"复刻检查清单"。抽查要点：无硬编码 hex（01）、720dp 形态切换无跳动（06）、左栏 450dp 封顶（07）、fadeThrough 先出后进（09）、顶栏滚动渐显（11）、未读/已读 100%/38% 对比（13）、封面 2:3 无 layout shift（15）、空状态颜文字（17）、触摸目标 ≥48dp（20）、字体 200% 不截断（29）。

## 4. 任务分解

> 顺序按白皮书 9.4 六阶段（令牌 → 骨架 → 列表 → 详情 → 弹层 → 状态）+ 阅读器 chrome 复核。P0–P2 是硬依赖前置，P3–P6 可并行。

### P0 · 设计令牌审计与补齐（先行，阻塞全部）

- **规格**：白皮书 [02 设计令牌](../reference/komikku-design-whitepaper-md/02-设计令牌.md) 全章 + `assets/design-tokens.json` + 09.1 tokens.css。
- **审计结果（2026-11-18 已完成核对）**：`ReaderTheme.kt` 浅/深/AMOLED 三套色板与 tokens.css **逐值一致**（含 surface-container 五档、outline 双档、AMOLED 容器分层）；`KomikkuTokens.kt` 的间距/圆角/透明度/高度/动效四档/断点（720、450、460、128）/尺寸全量在位且带出处标注；白皮书 02.6 确认**无自定义字体与 Typography**（M3 默认字阶即正确方案，唯一自定义样式 `header` 已有等价实现）；组件层 hex 扫描零违规。**令牌本体无需再补。**
- **剩余收尾（已完成 2026-11-18）**：KDoc 已修正、alpha 常量已收敛进 `DesignTokens.Alpha`、命名清理已按映射全部落地（`KomikkuTokens.kt` → `DesignTokens.kt` 嵌套结构；`KomikkuCover*` → `Cover*`；`KomikkuActionIcon` → `ActionIcon`；`Typography.komikkuHeader` → `Typography.header`；文件 `Covers/Lists/ActionIcon/SearchField.kt`、测试 `ShellScreenshotsTest`）。全仓代码零 `Komikku[A-Z]` 标识符残留（注释引用来源不受限）；`:shared:design-system` `:shared:app` `:shared:features` 三模块 jvmTest 全绿。**P1 放行。**
- **命名映射**（已执行完毕，此处留档备查；标识符去产品前缀；注释引用来源不受限；`app.koyura` 基包与安装标识不动）：

| 现名 | 改为 |
| --- | --- |
| `KomikkuTokens.kt` 七对象 `KomikkuSpacing/Radius/Alpha/Elevation/Motion/Breakpoints/Sizes` | `DesignTokens.kt`，嵌套为 `DesignTokens.Spacing/Radius/Alpha/Elevation/Motion/Breakpoints/Sizes` |
| `KomikkuActionIcon`（70 处调用，最大头） | `ActionIcon` |
| `KomikkuCover` / `KomikkuCoverImageTag` / `KomikkuCoverPlaceholder` / `KomikkuCoverPlaceholderTag` / `KomikkuCoverCornerRadius` | `Cover` / `CoverImageTag` / `CoverPlaceholder` / `CoverPlaceholderTag` / `CoverCornerRadius` |
| `KomikkuListGroupHeader` / `KomikkuBrowseRow` / `KomikkuIconPlaceholder` / `KomikkuDetailBackdrop` / `KomikkuSearchField` | `ListGroupHeader` / `BrowseRow` / `IconPlaceholder` / `DetailBackdrop` / `SearchField` |
| `Typography.komikkuHeader` | `Typography.header`（与原版同名） |
| `KomikkuTabletRule` / `KomikkuTabletBaseline` | `TabletRule` / `TabletBaseline` |
| 文件 `KomikkuCovers.kt` `KomikkuLists.kt` `KomikkuActionIcon.kt` `KomikkuSearchField.kt`、测试类 `KomikkuShellScreenshotsTest` | `Covers.kt` `Lists.kt` `ActionIcon.kt` `SearchField.kt`、`ShellScreenshotsTest` |

  `ReaderTheme` 本身不含产品名、不违反规则（历史命名，实为应用级主题）；是否顺手改 `AppTheme` 由 P1 决定，不阻塞。
- **验收**：亮/暗/AMOLED 三套切换全部页面成立；全局搜无组件级 hex、无 `Komikku` 标识符残留（注释除外）；AMOLED 导航 `surfaceContainer = #0C0C0C` 非纯黑；重命名后三模块 jvmTest 全绿。
- **允许修改**：`shared/design-system` 全部、消费方调用点（`shared/app`、`shared/features` 表现层与测试）；不改服务与数据层。

### P1 · 外壳与形态切换复核（对照白皮书 03/04 章）

- **规格**：03.1 断点（`smallestScreenWidthDp >= 720`，自动模式竖 700/横 600）、03.2/03.3 手机/平板骨架、6.2 NavigationBar/Rail、4.1 五 Tab（图标、角标、reselect 行为）、4.3 fadeThrough 200ms、04 顶栏三态。
- **原版参考**：`tachiyomi/ui/home/HomeScreen.kt`、`presentation/components/…`。
- **现状与落点**：`shared/app/…/AppScaffold.kt`（已按 720dp 切换底栏/导航轨）、`shared/design-system/…/navigation/`（NavTab、ReaderNavigationBar、ReaderNavigationRail）。基线：a-shell 截图矩阵。
- **差距要点**：返回优先级（Tab → 多选 → 子页 → 弹层，04.2）；Tab 切换动画是否 fadeThrough（非交叉淡入）；五 Tab 滚动位置保留（saveable）；Rail 选中药丸与 tonal elevation；底栏 80dp 规格与触摸目标。
- **验收**：03 章检查清单全勾 + 截图矩阵（外壳四形态）更新进 `docs/alignment/`。

### P2 · 书架（信息密度最高页，与在产线工作衔接）

- **规格**：07.2 书架 + 03.5 列数公式 `floor((可用宽+4)/(128+4))` + 03.6 三种密度 + 6.9 封面卡片 + 6.4 徽标 + 8.2 多选。
- **原版参考**：`tachiyomi/ui/library/LibraryTab.kt`、`presentation/library/…`、`presentation/components/common/…MangaCover…`。
- **现状与落点**：`shared/features/…/library/`（LibraryScreen、LibraryListing、LibrarySheets、LibrarySession）+ `shared/app/…/library/LibraryTab.kt` + `shared/design-system/…/KomikkuCovers.kt`。书架生产化在飞（近期提交），**先与在产分支对表再动手**。
- **差距要点**：卡片 2:3 + extraSmall(4dp) 圆角 + 加载前后零跳动；标题 12sp/18sp 两行；徽标 18dp 高、左上未读（secondary）/下载数（tertiary）、右上语言/来源；分类 Tab 计数 Pill 与选中指示；多选即时态（封面 0.76 + secondary 描边，**无过渡动画**）；360dp 2 列 / 1280dp 9 列；空状态颜文字 + 帮助入口（无底部按钮）；Continue Reading 按钮右下。
- **验收**：07.2 状态表五种状态（空/内容/多选/筛选激活/分类 Tab）截图矩阵；03 章列数检查项；9.5 条目 04/05/14/16/26。

### P3 · 作品详情（视觉最重页）

- **规格**：07.4 详情 + 6.6 按钮/FAB + 6.10 章节列表项 + 03.4 TwoPanelBox。
- **原版参考**：`presentation/manga/MangaScreen.kt`、`presentation/manga/components/MangaChapterListItem.kt`。
- **现状与落点**：`shared/app/…/browse/`（SourceDetailScreen，a-shell 已做头部/目录行对照）。
- **差距要点**：封面取色背景（封面 fill + **blur 7dp + alpha 0.2** + surfaceTint 40% + 垂直渐变 transparent→background 自 50% 高度）——CMP 侧 blur 的三端实现按 [technology-stack.md](technology-stack.md) 做 POC（`Modifier.blur` 桌面/iOS 可用性实测，不可用时降级为 surfaceTint + 渐变，不臆造）；`DynamicMaterialExpressiveTheme(seedColor=封面主色)` 的 CMP 等价；顶栏滚动渐显（透明 → surface@3dp，标题 alpha 0→1）；手机封面左置（≤100dp）/平板居中（0.65 宽）；动作行 4 等宽 TextButton；章节行 16/12 内边距 + 未读 secondary / 已读 0.38 对比；FAB Start/Resume 左右吸附；平板 `min(width/2, 450dp)` 双栏。
- **验收**：07.4 检查项 + 截图矩阵（浅/深 × 窄/宽，封面取色与纯色占位封面各一组——后者验证"页面不靠封面图撑设计"，01 章检查清单）。

### P4 · 弹层自适应（推广既有 ModeDialog 模式为共享组件）

- **规格**：03.7 弹层自适应 + 6.7 弹层与对话框。
- **原版参考**：`presentation/components/AdaptiveSheet`（手机 bottom sheet：顶圆角 extraLarge 28dp、`surfaceContainerHigh`、下拉关闭阈值 56dp、350ms tween；平板同内容 460dp 居中卡片、点遮罩关闭、禁拖拽）。
- **现状与落点**：`shared/features/…/reader/comic/chrome/ModeDialog.kt` 已实现窄屏贴底/宽屏 460dp 居中。**抽取为 design-system 共享 AdaptiveSheet**，供筛选/排序/多选批量面板复用；对话框统一 280–560dp。
- **验收**：03 章检查项（同一内容两形态）+ 04 章返回优先关弹层；键盘与返回行为正确。

### P5 · 状态与反馈

- **规格**：08 章全章 + 6.12 全局横幅 + 6.8 空状态。
- **差距要点**：全局横幅色带（顺序 Indexing/Updating/Syncing/Restoring → Downloaded only(tertiary) → Incognito(primary)，整条色带非图标）；空状态 12 个随机颜文字 `displayMedium` 0.78 + bodyMedium 0.78 文案 + 可选动作（**不用 error 色**）；下载六态（未下载/排队/下载中/已下载/失败/暂停，进度环 2dp 描边、确定进度外圈仍旋转）；加载圆环常转；章节左右滑（primaryContainer 背景 + 过阈值执行）；下拉刷新。
- **落点**：横幅在 `AppScaffold` 层；空状态组件进 `shared/design-system`（`EmptyScreen`，与原版同名、按功能命名）；多选顶栏按各屏接入。
- **验收**：08 章检查清单 + 9.5 条目 17/18/25/26。

### P6 · 阅读器 chrome 复核（c-chrome 基线之上）

- **规格**：07.5 阅读器 + 05 章动效 + 6.10。
- **原版参考**：`presentation/reader/appbars/…`（ReaderAppBars/TopBar/BottomBar）。
- **现状与落点**：`shared/features/…/reader/comic/chrome/`（ReaderChrome、ChapterListDialog、ModeDialog、ReaderSettingsPanel），c-chrome 基线已交付。
- **差距要点**：顶/底栏 `surface@3dp`、alpha **0.90（暗）/0.95（亮）**；栏动画滑入 200ms + 淡入 150ms、消失反向、无弹跳；页码药丸滑块 24dp 圆角、水平 padding 8dp（窄）/24dp（宽）；翻页按钮 FilledIconButton（容器=栏背景、图标 primary）；底部 4 图标 48dp SpaceEvenly；页码拖动逐格触觉；背景 Black/Gray/Automatic/White；阅读器控件强制 LTR（9.5 条目 23）。
- **边界**：Viewer 内部（翻页方向、缩放、长图分块）是对齐计划的阅读器功能任务（接 ZoomImage，见 [technology-stack.md](technology-stack.md) 排期表），**本任务只管 chrome 表现**，不动 `ReaderSession`/Viewer 契约。

### P7 · 后续轮清单（本轮不实现，防臆造）

引导（7.1）、更新（7.7）、更多（7.8）、设置 master-detail（7.9）、统计（7.10）、分类（7.11）、下载队列（7.12）、更新错误（7.13）、关于（7.14）与 7.15 同构页（全局搜索/迁移/扩展详情/追踪/调色板/…）。白皮书已给规格，本轮只登记待办；**同构页面复用现有组件，不另起设计**。

## 5. 技术栈映射（Komikku → Koyura）

| Komikku（Android） | Koyura（KMP/CMP 现代栈） |
| --- | --- |
| Voyager TabNavigator | Navigation 3（`navigation/`，五栏独立返回栈已实现） |
| ReaderActivity 独立栈 | `ComicReaderRoute` 全屏路由（`AppScaffold` 内） |
| XML 遗留页（下载队列等） | Compose 同构重写（后续轮） |
| ViewPagers（PagerViewer） | CMP `HorizontalPager`/`VerticalPager`（Viewer 任务） |
| SubsamplingScaleImageView | ZoomImage（已定排期即用，接自管下载管线） |
| Coil 3 + 自定义解码 | 封面 `CoverImageLoaders`；Coil 3 已定排期（书架网格生产化时接入） |
| MaterialExpressiveTheme + Monet | 官方 `material3` 1.13.0-beta02 + `ReaderTheme` 三套色板；动态取色按 P3 POC |
| Haze blur | `Modifier.blur` 三端 POC，降级路径见 P3 |
| AnimatedVectorDrawable 导航图标 | 静态图标 + fadeThrough（CMP 无 AVD 等价，不阻塞） |
| enableEdgeToEdge + insets | CMP window insets + AppScaffold 统一消费 |
| haptic（收藏/滑块/滑动删除/长按） | 平台 haptic expect/actual（触觉只在状态改变时） |

## 6. 禁止事项

- 不引入 Koin、MVIKotlin、ViewModel、Coil、Voyager、Decompose（排期见 [technology-stack.md](technology-stack.md)；ZoomImage 例外：阅读器轮次即用）。
- 不改 `:shared:source-runtime`、`:shared:data`、`:shared:network`、服务契约与 SQLDelight schema。
- 不复制 `eu.kanade` 包结构；包名与短目录按 [architecture.md](architecture.md)。
- 不臆造 P7 与 7.15 未截图页面的视觉。
- 每任务交付前受影响模块 `jvmTest` 全绿；截图矩阵不全不算过。

## 7. 执行顺序与依赖

```text
P0 令牌 ──→ P1 外壳 ──→ P2 书架 ──→ P4 弹层（可与 P2 并行）
                ├──→ P3 详情（依赖 P0 令牌 + P4 AdaptiveSheet）
                └──→ P6 chrome 复核（独立，随时可做）
P5 状态横幅（依赖 P0；空状态组件随各屏接入）
```

每完成一个 P：截图矩阵归档、[对齐计划](komikku-alignment.md) 对应项登记合并 SHA 与截图路径、审阅记录进 `docs/reviews/`。全部 P 通过后跑对齐计划的全量集成验收命令（含 Venera 脚本回归），再勾 9.5 的 30 条全局清单收口。
