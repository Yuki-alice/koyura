# 术语表

按主题分组，供阅读时快速查阅。`→` 表示推荐阅读章节。

---

## 证据与文档约定

| 术语 | 含义 |
|---|---|
| `📍代码` | 数值直接来自 Kotlin 源码，附 `文件:行`。可直接抄进实现。 |
| `📐实测` | 从截图像素量取 + 换算。必须连同设备 dp 一起引用。 |
| `🎯推断` | 基于代码+截图的分析结论，**不是官方说法**。不得当规范。 |
| `⚠️缺口` | 代码里不存在的能力，复刻者需自行设计。 |
| **v1 / 第一版** | 2026-11 的初版白皮书，含 10 类事实错误，**已作废**。 |
| **v2 / 本版** | 勘误重写版。所有冲突以本版为准。 |

---

## 色彩与主题

| 术语 | 含义 |
|---|---|
| **ColorScheme** | M3 的颜色角色集合。M3 1.5 有 **48** 个角色（35 个基础/表面 + 12 个 `*Fixed` + `scrim`）。 |
| **颜色角色** | 语义化的颜色名（`primary`、`surfaceContainerHigh`），不是具体色值。组件只读角色。 → [02](02-设计令牌.md) §2.5 |
| **Monet** | Android 12+ 的系统壁纸动态取色。Komikku 的**默认主题**。 |
| **TonalSpot** | materialkolor 的一种调色板风格，Monet 在 API 27–30 的回退方案。 |
| **PaletteStyle** | materialkolor 的 9 种调色风格：`TonalSpot` `Neutral` `Vibrant` `Expressive` `Rainbow` `FruitSalad` `Monochrome` `Fidelity` `Content`。 |
| **封面取色** | 从漫画封面的主色生成**局部**主题。默认 `Vibrant`，`animate = true`。 → [02](02-设计令牌.md) §2.6 |
| **getBestColor** | Komikku 的封面色选择启发式：面积优先 + 饱和度门槛。不是直接取 vibrant。 → [02](02-设计令牌.md) §2.6 |
| **AMOLED** | 纯黑模式。背景/surface 变 `#000000`，但容器保留 `#0C0C0C/#131313/#1B1B1B`。 → [02](02-设计令牌.md) §2.4 |
| **overrideDarkSurfaceContainers** | AMOLED 是否压平 5 档容器色。非 Monet 主题为 `true`，Monet 为 `false`。 |
| **`*Fixed` 角色** | M3 1.5 新增的 12 个"不随亮暗翻转"的强调色。Komikku 的 19 套主题**全部未定义**。 |
| **半残主题** | 只定义了部分角色的主题（如 Cloudflare 只有 22/48）。缺失角色会落到 M3 基线紫。 |
| **active 色** | 唯一的硬编码色，用于"筛选已激活"图标。亮 `#FFC107` / 暗 `#FFEB3B`。 |

---

## 布局与形态

| 术语 | 含义 |
|---|---|
| **smallestScreenWidthDp** | 屏幕最短边。Komikku **唯一**的形态判定依据。 |
| **isTabletUi()** | `smallestScreenWidthDp >= 720`。控制底栏↔轨、单栏↔双栏、底部弹层↔居中卡片。 → [03](03-布局与响应式.md) §3.1 |
| **TabletUiMode** | 用户覆盖：`AUTOMATIC` / `ALWAYS` / `LANDSCAPE` / `NEVER`。 |
| **700 / 600** | 仅用于 `AUTOMATIC` 模式的阈值（竖屏/横屏），**不是布局断点**。 |
| **TwoPanelBox** | 双栏容器。左栏 `min(w/2, 450dp)`，右栏吃剩余。 → [03](03-布局与响应式.md) §3.4 |
| **450dp 上限** | 双栏左栏的最大宽度。900dp 屏是临界点（左右各 450）。 |
| **Adaptive(128.dp)** | 网格列数规则。需配合 `2×8dp` 内容边距 + `2×4dp` 项内边距才算得对。 |
| **开始栏（startBar）** | Komikku 给 Scaffold 新增的槽位，放导航轨。CMP 的 Scaffold 没有。 |
| **contentWindowInsets** | Scaffold 内容区的系统栏内边距。 |
| **edge-to-edge** | 内容延伸到系统栏下方，由各组件自行消费 insets。 |

---

## 组件

| 术语 | 含义 |
|---|---|
| **CompactGrid** | 默认书架模式。标题叠在封面底部 33% 的渐变上。 → [06](06-组件规格库.md) §6.3 |
| **ComfortableGrid** | 标题在封面下方，固定 2 行。 |
| **ComfortableGridPanorama** | KMK 新增。宽封面（h/w ≤ 0.75）切 3:2。 |
| **CoverOnlyGrid** | 只有封面，无标题。 |
| **BadgeGroup** | 18dp 高的徽标容器。整组裁 4dp 圆角，单个徽标是 `RectangleShape`。 → [06](06-组件规格库.md) §6.6 |
| **UnreadBadge** | 未读数徽标，`secondary` 底。 |
| **DownloadsBadge** | 已下载数徽标，`tertiary` 底。 |
| **LanguageBadge** | 语言徽标。有图标时 25×18dp 透明底；无图标时 `tertiary` 文字。 |
| **SourceIconBadge** | 源徽标。缺失源用 `errorContainer` + `error` 图标。 |
| **Pill** | 计数胶囊。28dp 圆角，6×1dp padding。 → [06](06-组件规格库.md) §6.11 |
| **TabText** | Tab 标签 + 可选计数 Pill（10sp）。 |
| **AdaptiveSheet** | 手机底部抽屉 / 平板 460dp 居中卡片。 → [06](06-组件规格库.md) §6.13-6.14 |
| **TabbedDialog** | 带 Tab 行的弹层（筛选/排序/显示/分组都用它）。 |
| **MangaBottomActionMenu** | 详情页章节多选的动作栏。16dp 上圆角，48dp 按钮。 |
| **LibraryBottomActionMenu** | 书架多选的动作栏。 |
| **扇形进度** | 26dp 直径 + 13dp 描边 + `Butt` 的下载指示器。不是细环。 → [06](06-组件规格库.md) §6.7 |
| **页码描边** | 阅读器页码的双层文字：底层 `Stroke(4f)`，上层 `primary` 填充。 |
| **AppBar 三态** | 静息（elevation 0）/ 搜索（18sp 输入）/ 动作模式（elevation 3dp）。 → [04](04-导航与信息架构.md) §4.6 |
| **双 alpha 顶栏** | 详情页特有：标题 alpha 与背景 alpha 由不同条件驱动。 |

---

## 导航

| 术语 | 含义 |
|---|---|
| **5 Tab** | Library(0) / Updates(1) / History(2) / Browse(3) / More(4)。index 固定，隐藏不重排。 |
| **onReselect** | 重复点击已选中 Tab 的动作。**不是滚到顶**。 → [04](04-导航与信息架构.md) §4.3 |
| **Fade Through** | Tab 切换转场。200ms，`initialScale = 1f`（无缩放）。 |
| **Shared Axis X** | 页面 push/pop 转场。X 轴滑动 + 淡入淡出。 |
| **Voyager** | Komikku 用的导航库（`cafe.adriel.voyager`）。`TabNavigator` + `Navigator`。 |
| **Navigation 3** | `project x` 选型的导航库。`NavBackStack` + `NavKey`。 |
| **replaceAll** | 平板设置页的导航方式：右栏替换，不叠栈。 |
| **AnimatedImageVector** | Tab 图标的动画矢量。选中时播放一次（300–500ms）。 |

---

## 动效

| 术语 | 含义 |
|---|---|
| **七类动效** | Tab fade / 页面 shared-axis / 阅读器 slide+fade / sheet / sheet 内导航 / 设置高亮 / 图片 crossfade。 → [05](05-动效系统.md) §5.1 |
| **animatorDurationScale** | 系统"动画时长"设置。Coil crossfade 必须乘它。 |
| **animateItemFastScroll** | 只做位置动画、关掉 fade 的列表项动画。快速滚动时不闪。 |
| **定位高亮** | 从搜索跳到某设置项时的闪烁：200ms × 5 往返，600ms 延迟。 |
| **LongPress / TextHandleMove** | 唯二使用的触觉类型。普通点击无触觉。 |

---

## 状态

| 术语 | 含义 |
|---|---|
| **Downloaded only** | 全局模式，`tertiary` 横幅。 |
| **Incognito mode** | 全局模式，`primary` 横幅。暂停历史与追踪同步。 |
| **互斥横幅** | 索引/恢复/同步/更新四者只显示**一个**，不是叠加。 |
| **selectedBackground** | 列表选中底色。`secondary` @ 22%（亮）/ 16%（暗）。 |
| **selectedOutline** | 网格选中描边。实际是 `drawRect` 填充，被 `clip` 切成视觉描边。 |
| **0.76** | 网格选中时的封面 alpha（`GRID_SELECTED_COVER_ALPHA`）。 |
| **56dp 阈值** | 章节滑动 / Sheet 下拉的判定距离。 |
| **4 态下载** | `NOT_DOWNLOADED` / `QUEUE` / `DOWNLOADING` / `DOWNLOADED` / `ERROR`（5 个枚举，4 个视觉态）。 |

---

## 平台与实现

| 术语 | 含义 |
|---|---|
| **CMP** | Compose Multiplatform。`project x` 的 UI 技术栈。 |
| **KMP** | Kotlin Multiplatform。 |
| **materialkolor** | `com.materialkolor:material-kolor`。提供 `DynamicMaterialExpressiveTheme` 与 9 种 `PaletteStyle`。 |
| **MaterialExpressiveTheme** | M3 Expressive 主题入口。CMP 1.13 有。 |
| **CMP 1.13.0-beta02** | `project x` 选定的 Compose Multiplatform 版本。已验证含 `MaterialExpressiveTheme`。 |
| **material-kolor 5.0.1** | `project x` 选定版本。已验证含 `DynamicMaterialExpressiveTheme`，有 iOS/桌面变体。 |
| **soup.compose.material.motion** | Komikku 用的 M3 motion 库（`materialSharedAxisX`、`materialFadeThrough`）。**无 CMP 版**。 |
| **me.saket.swipe** | Komikku 用的滑动库（`SwipeableActionsBox`）。**仅 Android**。 |
| **androidx.palette** | Komikku 用的取色库（`Palette.from(bitmap)`）。**KMP 无官方版**。 |
| **haze** | 模糊库（`dev.chrisbanes.haze`）。详情页封面对话框用 10dp。 |
| **DesignTokens.kt** | `project x` 现有令牌文件，从 v1 白皮书抄来，有 6 处错误值。 → [09](09-复刻指南.md) §9.1 |

---

## 项目特有

| 术语 | 含义 |
|---|---|
| **Komikku** | 本白皮书分析的对象。Mihon + TachiyomiSY 的 fork。`applicationId = app.komikku`。 |
| **Mihon** | Komikku 的主上游。代码里无标记，字符串用 `MR`。 |
| **TachiyomiSY** | 副上游。代码标记 `// SY -->` … `// SY <--`，字符串用 `SYMR`。 |
| **KMK 标记** | Komikku 自己的改动。`// KMK -->` … `// KMK <--`，字符串用 `KMR`。 |
| **`project x`** | 用 KMP + CMP 复刻 Komikku 视觉的目标项目。 |
| **Venera-Next** | `project x` 的 JS 源兼容基线（不是 Komikku 的一部分）。 |
