# 前端表现层复刻任务书（对齐第二版白皮书）

基线：`docs/design-whitepaper/`（第二版，2026-11-20 入库，7667 行 / 83 张截图 / `tools/` 4 个复算脚本）+ `reference/komikku` @ `290267dd`。
目标：把 `shared/app`、`shared/features`、`shared/design-system` 的表现层从"结构对、细节偏"推到"逐页可对照截图验收"。

## 0. 为什么重排

上一轮按"页面铺开"推进（视觉轨 + 夜间轨），页面确实都立起来了，但**地基没校准**：

| 事实 | 证据 |
| --- | --- |
| `DesignTokens.kt` 仍是第一版的 6 处错值 | `UnreadDot=6dp`（应 8）、`DownloadProgress=24dp`（应 26）、`ProgressStroke=2dp`（应 13）、`ThemePreviewCard=112dp`（应 114）、`DetailCoverWidth/Height=96/144`（应 100dp 上限 + 2:3 推 150）、`LibraryListCoverWidth/Height=56/80`（应 40×40）`DesignTokens.kt:137-152`；白皮书 09 §9.1 明确"先做这一步" |
| 详情页顶栏只有一个 alpha 通道 | `SourceScreens.kt:613-615` 传 `titleAlpha = scrollFraction`，`ReaderTopAppBar.kt:62-63` 容器 alpha 同源；白皮书要求 title 由"首项完全滚出"驱动、background 由"是否开始滚动"驱动（README 结论 4、09 坑 6） |
| 阅读器页码不是描边文字 | `ReaderChrome.kt:217-227` 是 `bodyMedium` 纯文本；白皮书要求 12sp Bold + 1sp 字距 + 4px `#2D2D2D` 描边 + `primary` 填充（README 结论 5、06 §6.18） |
| 下载指示器不存在 | 全仓无 26dp/13dp 扇形组件，`DesignTokens` 里只有错的 24/2；白皮书 06 §6.7 要求 26dp + 13dp 描边 + `Butt` + 19dp 箭头 |
| 三套设置行只落了一套 | `components/SettingsItem.kt:44-70` 是 16/16 + 选中 24 圆角；24/10 的 `SettingsItems` 型与平板 8dp 外距未分（09 坑 4） |
| 设置偏好生产不持久 | `AppScaffold.kt:144` 取 `LocalAppSettingsSession ?: rememberSettingsSession()`（默认 Fake）；`ExtensionApp.kt:188-195` 造了 `ProductionSettingsRepository` 却只 provide 成 `LocalProductionSettings`，无人消费 |

所以本轮不按"再铺一遍页面"排，而按白皮书 §9.5 的依赖顺序：**先 token，再组件配方，页面只做差异项**。

## 1. 基线、证据与冲突裁决

**引用规则**（白皮书 README §证据标注）：`📍代码` 直接抄；`📐实测` 连设备参数一起抄（手机 360×720dp @2x；平板 1280×800dp @0.75x）；`🎯推断` 不当规范；`⚠️缺口` 需自行决策并登记。

**必须先裁决的数值冲突**（写进任务验收，避免各做各的）：

| 项 | 冲突 | 采用 | 依据 |
| --- | --- | --- | --- |
| 章节数标题字号 | 7.12 骨架注 22sp vs 尺寸表 16sp | **16sp**（titleMedium） | 07:658 尺寸表 |
| EmptyScreen 水平内边距 | 02 说 32dp vs 06 代码 24dp | **24dp** | 06:995 |
| 阅读器页码胶囊圆角 | 02 说 16dp vs 06 说 24dp | **24dp** | `design-tokens.json radius.readerPagePill=24` |
| 历史行封面 | 55×83dp vs 按行高算 53×80dp | **80dp 高 / 53dp 宽** | 06:563 算法 |
| 下载态数量 | 08 标题"4 个" vs 列 5 行 | **枚举 5 个、视觉 4 组**；QUEUE 无进度环 | 08:146 |
| 设置高亮时长 | 08 说 3s vs 02/06 说 200×5+600ms | 动画按 **200×5+600ms**，3s 视为 UX 超时 | 02:605 |

**截图编号陷阱**：编号是各设备独立流水号，且 14–17 段手机/平板同号不同页（手机 `16_updates`=平板 `15_updates`；手机 `12_chapter_list`=平板 `12_reader`）。任务与验收**一律写"设备/文件名"**，禁止只写编号。

## 2. 现状盘点（决定"不重排什么"）

### 已完成、本轮不重做

- 主题层：19 套静态主题（`theme/StaticColorSchemes.kt`）、materialkolor 动态取色（`VisualTheme.kt:46`）、AMOLED 变体、封面主色提取（`CoverSeedColor.kt`）。
- 组件层：`SectionCard`、`ListGroupHeader`、`ActionIconButton`、`Reader*` 家族、`EmptyScreen/LoadingScreen/ErrorScreen`、`SettingsItem`、`Cover`/`CrossfadingCover`、`FastScroller`。
- 页面层（真实实现）：Library、Updates、History、Browse（Sources/Extensions）、Store、ExtensionDetail、SourceSearch、SourceDetail、Reader、Downloads、Categories、Statistics、Settings（主页 + 10 子页）、GlobalSearch。
- 验收层：15 页 × 4 态矩阵（`visual/VisualParityMatrixTest.kt`）+ 像素采样断言（`GoldenDiffAssertions`）。

### 真实缺口（有代码证据）

| 页/区 | 缺口 | 证据 |
| --- | --- | --- |
| 引导 Onboarding | **完全没有路由与屏幕**，仅测试目录里一个 `implemented=false` 键 | `VisualMatrixCatalog.kt:31-37`；全仓无 onboarding 屏 |
| 更多 → 5 个工具页 | `update-errors`/`data-storage`/`batch-add`/`about`/`help` 全是统一颜文字占位 | `ui/UtilityPage.kt:22-27`、`AppScaffold.kt:442-448` |
| Tab 内 `DetailRoute` | 仅标题 + `#id` | `ui/TabScreens.kt:81-106` |
| 浏览 Feed / Migrate | 占位面板，18+ 开关是页内 `remember` | `BrowseScreen.kt:191,200`、`BrowseExtraPanes.kt:31-72` |
| 详情页动作 | Tracking / WebView 空回调 + `enabled=false`；章节下载 disabled | `SourceScreens.kt:824-839,1078-1081` |
| 设置子页 | 12 处 `onClick = {}`（默认分类、背景色、下载目录、跟踪服务、同步间隔、语言、恢复备份、存储用量、清缓存、代理、许可证、帮助） | `SettingsSubpages.kt:162/245/262/299/330/352/382/390/396/437/458/459` |
| 设置持久化 | `ProductionSettingsRepository` 造了但无人消费，生产落 Fake 内存态 | `ExtensionApp.kt:188-195`、`AppScaffold.kt:144` |

## 3. 任务分解

> 估算按单人对齐后的节奏（含测试），共 **约 26 人日**（4 + 8 + 10 + 2 + 2），不含返工与双端逐页核对。任务可并行处标注 `∥`；每个任务的验收都要求"改哪个页面就核哪个页面"，不整轮铺开。

### T0 地基校准（4 任务 / 约 4 人日）

| # | 任务 | 范围与参照 | 验收 |
| --- | --- | --- | --- |
| T0.1 | **令牌纠错与补缺** | 改 `DesignTokens.kt`：`UnreadDot 8`、`DownloadIndicator 26`、`DownloadProgressStroke 13`、`DownloadArrow 19`、`ThemePreviewCard 114`、`DetailCoverMaxWidthPhone 100`、`LibraryListCoverSize 40`；删除 96/144、56/80、24/2、112/6 旧常量并改引用点。参照 09 §9.1 修正表（`MangaChapterListItem.kt:128`、`ChapterDownloadIndicator.kt:158/271/281`、`AppThemePreferenceWidget.kt:95`、`MangaInfoHeader.kt:622`、`CommonMangaItem.kt:469`） | 新增 `DesignTokensTest`：逐常量断言 8/26/13/19/114/100/40；`grep` 旧常量零引用；相关页面截图尺寸不回归 |
| T0.2 | **主题三路 + AMOLED 规则** | 核对 `VisualTheme.kt` 的 Static / Monet / Custom 三路；AMOLED 在非 Monet 下压平五档容器、Monet 下只改 `background/surface`（`BaseColorScheme.kt:17-41`，02 §2.4）；19 套由 JSON 生成不手抄 | 三路切换测试 + AMOLED 容器色断言（`#0C0C0C/#131313/#1B1B1B`）；`compare_tokens.py` 0 差异 |
| T0.3 | **形态判定单一来源** | `ProvideSmallestWidth` 只在应用根调用一次；桌面/iOS 用窗口宽度换算（09 §9.3 方案 A）；确认 `TabletMinSmallestWidthDp=720` 只此一处；底栏 80dp 内容高、去掉 M3 两端 8dp spacer；Rail 最小 80dp、项距 4dp、垂直居中（06 §6.2） | 360/720/1280 三档形态测试：底栏↔轨、单栏↔双栏、底部 Sheet↔居中卡片同时切换 |
| T0.4 | **转场与 AppBar 三态** | Tab 切换 `materialFadeThrough` 200ms 且 `initialScale=1f`（09 坑 9）；页面 `materialSharedAxisX`；AppBar 静息/搜索/动作三态（搜索 18sp Normal、无容器无边框；动作模式 3dp；标题 22sp 单行；副标题 marquee 2000ms）（06 §6.1、04 §4.6） | 常量断言 + AppBar 三态 UI 测试；切换 tab 无缩放（截帧比对） |

### T1 组件配方（8 任务 / 约 8 人日，`∥` 大部分可并行）

| # | 任务 | 范围与参照 | 验收 |
| --- | --- | --- | --- |
| T1.1 | **选中态与徽标** | 网格选中 = `drawRect(secondary)` + 封面 alpha 0.76（不是 `border`）；列表选中 `secondary` 0.22/0.16；`BadgeGroup` 18dp 整组 `clip(4dp)`、子块 `RectangleShape`、padding 3×1dp（08 §3、06 §6.6） | 组件测试 + 截图像素采样（选中矩形覆盖、徽标无缝） |
| T1.2 | **Pill 与 Chip** | `Pill` 28dp 圆角、padding 6×1dp、计数底 `onBackground` 0.08/0.12、字号 10sp；`Chip` 8dp 圆角、高 32dp、1px `outline` 边框、透明底、`labelLarge`（06 §6.10-6.11） | 尺寸断言 + 书架标题 Pill 与详情标签截图对照 |
| T1.3 | **三套设置行与三种组头** | A 型 16/16（已有，复核）；B 型 `SettingsItems` 24/10、控件间距 24；C 型平板选中行外 8dp + 24dp 圆角 + `surfaceVariant`；`ListGroupHeader` / `PreferenceGroupHeader` / `header` 三套并存按页面选（06 §6.8-6.9、07 §7.16） | 三种行同屏测试，量 16/16、24/10、8+24 |
| T1.4 | **下载指示器** | 26dp 直径、13dp 描边、`StrokeCap.Butt`、`gapSize=0`、箭头 19dp；5 态（QUEUE 只箭头无环）；失败 26dp `error`（06 §6.7、08 §4） | 5 态截图 + 尺寸断言 |
| T1.5 | **阅读器页码与 chrome** | 页码双层 Text：底层 `Stroke(4f)` `#2D2D2D`、上层 `primary`、12sp Bold + 1sp 字距；chrome `surfaceColorAtElevation(3dp) × 0.95/0.90`（已有，复核）；阅读器内 `bodyLarge → bodySmall`（09 坑 7、README 结论 5） | 页码截图放大比对；chrome alpha 断言；阅读器字阶断言 |
| T1.6 | **底部动作栏与多选顶栏** | 仅上圆角 16dp、`surfaceContainerHigh`、按钮 48×48、Row 8/12；长按 1s 展开 `labelSmall` 11sp、权重 1f→2f；多选顶栏 ✕ + 计数 + 全选/反选、elevation 3dp（06 §6.14、08 §3） | 长按 1s 展开测试；截图量圆角与按钮 |
| T1.7 | **自适应弹层** | 手机底部 Sheet：28dp 圆角、`surfaceContainerHigh`、下滑 56dp 关闭、tween 350ms；平板 460dp 居中卡片、点遮罩/返回关闭、fade 220ms+90ms delay / 90ms（06 §6.12-6.13、09 §9.4 配方 3） | 双端尺寸断言（平板应 ≈458.7dp）；下滑关闭手势测试 |
| T1.8 | **横幅与空态** | 7 种横幅语义色 + `expand/shrinkVertically` + 状态栏 inset；多条堆叠时状态栏只算一次；`EmptyScreen` 45sp 颜文字、强制 LTR、12 个随机表情、说明 baseline +24dp、动作按钮上图下文（06 §6.15-6.16、08 §2） | 堆叠横幅截图（状态栏不翻倍）；空态在 LTR/RTL 下位置一致 |

### T2 页面差异（11 任务 / 约 10 人日，按页面族 `∥`）

> 原则：**只改与 07 不符的差异项**，不整页重写；每页先跑现有 UI 测试确认基线，再改。

| # | 页面 | 差异清单（对照 07 与截图） |
| --- | --- | --- |
| T2.1 | 书架（07 §7.2-7.3） | 标题 + 计数 Pill（10sp，0.08/0.12）；筛选激活 `#FFC107`/`#FFEB3B`；分类 Tab 显示条件（`categories.size>1` 或首分类非系统）与 `edgePadding=0`、自加分割线、未选中 `onSurface`；`Adaptive(128dp)`（已有）；四种显示密度；筛选/排序/显示/分组 Sheet 四等分 tab、排序项 24dp 空白占位、仅当前项显示升降序箭头；下拉刷新 1s 假状态（不要真刷新） |
| T2.2 | 详情（07 §7.11） | **拆两个 alpha**：title 由首项完全滚出、background 由开始滚动；封面 100dp 上限；背景 `blur(7dp)+alpha(0.2)+surfaceTint(0.4)`、渐变自 `height/2`；动作行等分、图标 20dp/标签 12sp、激活 primary 其余 0.38、WebView/Merge 恒 primary；描述折叠 3 行（有笔记 6 行）+ 24dp 渐变遮罩；标签 chip 8dp；关联卡 96dp；FAB 可左右拖动贴边；平板 `min(w/2,450dp)` 左栏、封面 65% 栏宽居中、右栏独立滚动 |
| T2.3 | 章节列表（07 §7.12 + 06 §6.7） | 头部 16sp + `▤`，点头部开**章节筛选 Sheet**（平板有 `tablet/37`，手机无图，按 7.3 结构类推并登记）；行 padding 16/12/8/12、未读点 8dp、已读 alpha 0.38、滑动阈值 56dp、指示器 26dp、状态层 40dp |
| T2.4 | 阅读器（07 §7.13） | chrome 四态与顶/底栏尺寸；页码（T1.5）；底栏 4 图标 `primary` + navigationBars inset；页面横 padding 手机 8dp / 平板 24dp；五种模式；点击区（已有，核默认行为）；平板导航条 Bottom/VerticalLeft/VerticalRight；`bodyLarge→bodySmall` |
| T2.5 | 历史（07 §7.5 + 06 §6.8） | 96dp 行、封面 2:3 fillMaxHeight（≈53×80dp）、标题 SemiBold 2 行、`●` + 章节·时间、收藏/删除图标；Today/Yesterday/更早分组；搜索/筛选/多选三入口；空态 `information_no_recent_manga`，搜索无结果 `no_results_found` |
| T2.6 | 更新（07 §7.4） | 斜体 "Last updated"（`bodyMedium` + Italic，padding 16/8）；日期 `ListGroupHeader`；封面 2:3 + 标题/章节行；按作品分组可折叠；项左右滑动动作（已读/书签/下载）；顶栏三图标（显示模式 / Upcoming / 立即检查）；空态 `Σ(ಠ_ಠ)` |
| T2.7 | 浏览与源浏览（07 §7.6-7.9） | 4 Tab 顺序可配（Feed 可前置/隐藏）；搜索框 50dp 全圆、左右 16dp；源项 16/8、图标 40dp、副标题「国旗 + 语言」、NSFW `error` 大写；权限横幅 16dp 居中两行；源浏览分区标题 16dp、横向卡 96dp（全景 205dp）、标题 3 行、右下 `ExtendedFAB`；**Feed 与 Migrate 需真实化**（当前占位） |
| T2.8 | 更多 / 关于 / 工具页（07 §7.10、§7.16 About） | Logo 64dp（上下 56dp，总高 176dp）、开关行 56dp、7 导航行、社交图标 32dp ×3；关于页 Logo + 版本/构建时间 + 1px `outline` 分隔 + 社交行；**5 个工具页真实化**（update-errors 接更新失败集、data-storage 接存储用量、batch-add 接批量添加、about 接关于页、help 接帮助链接） |
| T2.9 | 设置（07 §7.14-7.16） | 主页 11 行（图标 24dp/标题 16sp/副标题 12sp 0.78/最小 56dp/垂直 16dp）；外观：分段按钮全宽、主题卡 114dp 9:16、外框 4dp、外 17/内 13 圆角、内 padding 4dp、间距 8dp、标签固定 2 行、`CheckCircle`；10 子页分组与 5 种控件；平板双栏左 450dp（`getPalerSurface`）、选中 8dp+24dp 圆角、子页 `replaceAll` 而非 push；**12 处空回调接线**；**SettingsRepository 生产桥接**（`ExtensionApp` 的 `ProductionSettingsRepository` 接入 `LocalAppSettingsSession`） |
| T2.10 | 统计 / 分类 / 下载队列（07 §7.17） | 统计：`ElevatedCard` 28dp 圆角、卡边距 16/8、内 16、组标题 `titleSmall` 横 32、概览 22sp Bold、次要 14sp Bold、标签 11sp、图标 24dp 底部对齐；分类：`primaryContainer` FAB、拖拽排序 + 右滑删除（按钮替代保留）；队列：空态 `(︶︿︶)`、项=封面+作品+章节+进度+暂停/取消、顶栏批量 |
| T2.11 | 引导（07 §7.1） | **新建**：四步（主题/存储/权限/指南）+ 顶部进度 + 底部按钮；图标 48dp、标题 32sp、副标题 14sp 0.78、内容卡 8dp、`Hairline` 分隔、`materialSharedAxisX` 转场；完成门槛（存储必选、权限必给）；接入首启路由与设置「重看引导」入口；平板无截图，按单栏实现并登记 `⚠️缺口` |

### T3 状态与反馈（3 任务 / 约 2 人日）

| # | 任务 | 范围 | 验收 |
| --- | --- | --- | --- |
| T3.1 | 空/错/载统一 | `EmptyScreen` 三分类文案（书架/搜索/筛选/更新/历史各用指定 key）；`Snackbar` Short + 撤销（删除书架、标记已读/未读、删除历史给撤销；清缓存、加入书架不给）；`AlertDialog` 280–560dp；加载居中无骨架 | 各页四态测试 + 撤销行为测试 |
| T3.2 | 多选与返回键 | 书架/历史/更新/详情/源浏览统一：进入多选隐藏外壳，返回先清选择再退页；关闭选择后顶栏复位；下拉刷新在选择态禁用 | 每页返回键序列测试（选择态 → 清选择 → 返回） |
| T3.3 | 全局模式横幅 | Downloaded only / Incognito / 更新中三态横幅语义色 + 互斥只显一个；多任务时状态栏只算一次（T1.8 复用） | 三态截图 + 互斥测试 |

### T4 验收基线（3 任务 / 约 2 人日）

| # | 任务 | 内容 |
| --- | --- | --- |
| T4.1 | 矩阵扩到 18 页 | `VisualMatrixCatalog` 去掉 3 个 `implemented=false` 占位键（onboarding / update-errors / about 真实化后转正），补 Feed、Migrate、5 个工具页中的关键页；断言从"6 个边缘采样点"升级为"尺寸采样"：书架卡宽 162dp@360 / 120dp@1280、左栏 450dp、平板弹层 458.7dp、指示器 26dp、页码字形 |
| T4.2 | 双端截图核对 | 37 手机 + 37 平板逐页与 `assets/` 对照，差异记入 `docs/tasks/frontend-parity-diff.md`（新增资源，允许）；重点核 14–17 段同号不同页的错位 |
| T4.3 | 全量门与三端 | `:shared:domain/features/data/source-runtime/app:jvmTest` + `:apps:android:assembleDebug`、`:apps:desktop:classes`、`linkDebugFrameworkIosSimulatorArm64`；视觉矩阵连续两遍稳定 |

## 4. 依赖与并行

```
T0.1 ─┬─> T1.1 ─┬─> T2.1 ─> T2.3 ─> T2.4
      ├─> T1.2 ─┤
      ├─> T1.3 ─┼─> T2.9 ─> T2.8
      ├─> T1.4 ─┤
T0.2 ─┼─> T1.5 ─┘
T0.3 ─┼─> T1.6 ─> T3.2 ─> T4.1
T0.4 ─┴─> T1.7 ─> T2.2 ─> T2.5/T2.6
              T1.8 ─> T3.1/T3.3
T2.7 / T2.10 / T2.11 与 T2.1 之后并行
```

硬约束：**T0.1 未完成前不开 T1**（旧 token 会被新组件继承）；T1.5 未完成不验收 T2.4；T2.9 的生产桥接与 T4.1 的矩阵转正必须同批。

## 5. 不做什么（红线）

- 不复刻粉紫：默认 `AppTheme=MONET`，复刻的是"角色 → 组件"映射，不是某组色值（README 结论 1）。
- 不复刻截图里的紫色下划线（无障碍焦点指示器，07:917-921、09 坑 8）。
- 不做装饰性动画：只有 7 类（05 §5.1），其余交给 M3 默认。
- 不改 `shared/domain`、`shared/data`、`shared/source-runtime`、`shared/network`、`gradle/`；设置持久化只在装配层接线，不动仓储实现。
- 不引入 Koin / MVIKotlin / androidx ViewModel / Multiplatform Settings / Kermit / Turbine / Voyager / Decompose / Room / Retrofit / Paging 3。
- 不为"看起来更现代"改结构：8 条不可动结构见 01 §1.2（运行时主题、单一 720 断点、`min(w/2,450)` 双栏、双 alpha 顶栏、半透明 chrome、状态即颜色、M3 indicator、动效克制）。

## 6. 风险与登记项

| 风险 | 处置 |
| --- | --- |
| 平板引导无截图（07:84 `⚠️缺口`） | 按手机单栏实现，双栏不做，登记 |
| 章节筛选 Sheet 只有平板图（`tablet/37`） | 手机按 7.3 的 `TabbedDialog` 类推，登记为推断 |
| Migrate 只有平板图、无独立小节 | 桌面/手机按同构布局，登记 |
| 桌面 / iOS 六项平台缺口（palette、HSL、swipe、motion、configuration、wallpaper） | 已有自实现或降级方案（09 §9.7），本轮只复核桌面宽度判定与手势替代 |
| 白皮书内部数值冲突 | 按第 1 节裁决表执行，冲突处代码注释写明依据 |
| 设置仓储桥接涉及装配层 | 先做 `LocalAppSettingsSession` 注入 + 一轮重启持久化测试，再动 UI |
| 视觉矩阵像素断言易被抗锯齿干扰 | 只断言尺寸与"允许色集合"，不做逐像素 golden；采样点避开文字边缘 |

## 7. 交付验收清单（摘自 10 §D 与 09 检查清单，逐条签字）

- [ ] `DesignTokens` 6 处错值已改，旧常量零引用
- [ ] 主题三路可用，Monet + AMOLED 不压容器
- [ ] 720dp 单点切换：底栏↔轨、单栏↔双栏、底部↔居中弹层
- [ ] 书架 360dp 2 列 / 1280dp 9 列，卡宽 162 / 120dp
- [ ] 双栏左栏 450dp（900dp 时 450/450）
- [ ] 平板弹层 460dp（实测 ≈458.7dp）
- [ ] 下载指示器 26dp + 13dp 描边 + `Butt`
- [ ] 阅读器页码双层描边，chrome 0.95/0.90
- [ ] 详情页两个独立 alpha，封面 100dp
- [ ] 三套设置行按组件选，不按页面选
- [ ] 选中态是 `drawRect` 不是 `border`，徽标组无圆角裂缝
- [ ] 空态 45sp 颜文字 + 强制 LTR
- [ ] 7 类动效参数与 05 一致，Tab 切换无缩放
- [ ] 5 个占位页转正，onboarding 可走通
- [ ] 设置偏好重启后保留
- [ ] 18 页 × 4 态矩阵连续两遍全绿
- [ ] 三端构建通过（Android / Desktop / iOS 模拟器）
