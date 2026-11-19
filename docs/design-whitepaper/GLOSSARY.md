# 术语表

按主题分组，供阅读时快速查阅。带 `→` 的条目表示推荐阅读章节。

## 设计系统

| 术语 | 解释 | 相关章节 |
| --- | --- | --- |
| **Material 3 / M3** | Google 的第三代 Material Design 系统，以语义角色、动态取色和自适应布局为核心。 | [01](01-设计定位与视觉DNA.md) |
| **M3 Expressive** | M3 的表达性分支，强调更鲜明的形状、动效与情感化反馈。Komikku 使用 `MaterialExpressiveTheme`。 | [01](01-设计定位与视觉DNA.md) |
| **ColorScheme** | Compose 中承载一整套颜色角色的对象；Komikku 的 19 套静态主题各自产出一个 `ColorScheme`。 | [02](02-设计令牌.md) |
| **语义角色（semantic role）** | 按用途命名的颜色，如 `primary`、`surfaceContainer`、`error`；组件只消费角色，不消费具体色值。 | [02](02-设计令牌.md) |
| **Design Token** | 颜色、字号、间距、圆角、动效等设计决策的变量化表达；本文的令牌表与 `design-tokens.json` 即此类。 | [02](02-设计令牌.md) |

## 色彩

| 术语 | 解释 | 相关章节 |
| --- | --- | --- |
| **Monet / 动态取色** | Android 12+ 从壁纸提取种子色并生成整套调色板；Android 11 及以下由应用用壁纸种子自行生成。Komikku 默认开启。 | [02](02-设计令牌.md) |
| **封面取色（cover-based color）** | 从当前漫画封面提取主色，作为局部主题的 seed，只影响详情页/阅读器。 | [02](02-设计令牌.md) |
| **Seed color** | 生成调色板的起始颜色；Custom Theme 允许用户自选，内置 13 组预设色板。 | [02](02-设计令牌.md) |
| **TonalSpot / Spec 2025** | `material-kolor` 中从 seed 生成协调色板的算法风格；决定色相偏移与饱和度。 | [02](02-设计令牌.md) |
| **AMOLED 主题** | 将背景与 surface 压到纯黑、同时保留 `surfaceContainer` 层次的暗色变体。 | [02](02-设计令牌.md) |
| **surfaceContainer 五档** | `Lowest / Low / 默认 / High / Highest`，用于表达浮层层级；AMOLED 下仍保留 `#0C0C0C` 等非纯黑值。 | [02](02-设计令牌.md) |
| **on\* 角色** | 位于某个容器之上的前景色，如 `onPrimary`、`onSurface`；保证对比度。 | [02](02-设计令牌.md) |
| **scrim** | 弹层背后的遮罩色，通常半透明黑。 | [02](02-设计令牌.md) |

## 布局与响应式

| 术语 | 解释 | 相关章节 |
| --- | --- | --- |
| **dp / sp** | Android 的密度无关像素 / 字体缩放像素；本文尺寸默认单位为 dp，字号为 sp。 | [03](03-布局与响应式系统.md) |
| **smallestScreenWidthDp** | 屏幕最短边的宽度，不随旋转变化；Komikku 用它判定是否进入平板形态。 | [03](03-布局与响应式系统.md) |
| **720dp 断点** | 平板形态的判定阈值：`smallestScreenWidthDp >= 720`。 | [03](03-布局与响应式系统.md) |
| **TwoPanelBox** | Komikku 的双栏容器，左栏宽 `min(可用宽度/2, 450dp)`，右栏吃剩余空间。 | [03](03-布局与响应式系统.md) |
| **master-detail** | 左列表、右详情的平板布局；设置页是典型例子。 | [03](03-布局与响应式系统.md) |
| **NavigationBar** | 手机底部导航栏；Komikku 自定义为 80dp、5 项等宽。 | [06](06-组件规格库.md) |
| **NavigationRail** | 平板左侧导航栏；80dp 宽，图标在上、文字在下，整体垂直居中。 | [06](06-组件规格库.md) |
| **edge-to-edge** | 内容绘制到系统栏之下，系统栏透明；由 `enableEdgeToEdge` 开启。 | [03](03-布局与响应式系统.md) |
| **insets** | 系统栏、手势区、刘海等占用的安全区域；由页面骨架统一消费。 | [03](03-布局与响应式系统.md) |
| **AdaptiveSheet** | 同一弹层组件在手机为底部抽屉、平板为 460dp 居中卡片。 | [06](06-组件规格库.md) |

## 动效与交互

| 术语 | 解释 | 相关章节 |
| --- | --- | --- |
| **fadeThrough** | M3 的“先淡出、再淡入”转场，用于同级内容替换；总时长 200ms。 | [05](05-动效系统.md) |
| **shared axis X** | M3 的横向共享轴转场，用于页面前进/后退，表达层级关系。 | [05](05-动效系统.md) |
| **reselect** | 再次点击已选中的一级 Tab；Komikku 用它回到顶部或刷新列表。 | [04](04-导航与信息架构.md) |
| **BackHandler** | Compose 中接管系统返回键的机制；弹层的 `BackHandler` 注册在内容之前，保证优先关闭。 | [04](04-导航与信息架构.md) |
| **HapticFeedback** | 触觉反馈；本项目只在收藏、页码拖动、滑动删除、长按多选时触发。 | [05](05-动效系统.md) |
| **prefers-reduced-motion** | Web 侧的无障碍媒体查询；复刻时应降级为淡入淡出。 | [09](09-复刻指南.md) |

## 组件与页面

| 术语 | 解释 | 相关章节 |
| --- | --- | --- |
| **SectionCard** | 带分组标题的 `ElevatedCard`，用于统计、下载等页面；外圆角 `extraLarge` 28dp。 | [06](06-组件规格库.md) |
| **Badge / Pill** | 18dp 高的小型状态标记；Badge 表未读/下载数，Pill 表计数或筛选。 | [06](06-组件规格库.md) |
| **TabbedDialog** | 带 Tab 行的弹层；Tab 行容器为 `surfaceContainerHigh`，内容高度用 `animateContentSize()` 平滑变化。 | [06](06-组件规格库.md) |
| **Continue Reading** | 书架卡片上的继续阅读按钮；32dp 方形、`primaryContainer 90%`。 | [06](06-组件规格库.md) |
| **EmptyScreen** | 空状态组件：随机颜文字 + 一句解释 + 可选动作；不使用红色。 | [08](08-状态与反馈.md) |
| **InfoScreen** | 信息页骨架：48dp 图标、headlineLarge 标题、titleSmall 副标题、底部整宽按钮；引导页复用它。 | [06](06-组件规格库.md) |
| **Reader Activity** | 阅读器是独立 Activity，不复用主页面 Scaffold，默认纯黑、沉浸式、隐藏系统栏。 | [07](07-页面规格.md) |
| **全局状态横幅** | 顶栏下方的全宽色带，表达 Downloaded only、Incognito、Indexing、Updating 等持续模式。 | [08](08-状态与反馈.md) |
| **多选模式** | 长按进入；顶栏变计数 + 全选/反选/关闭，选中态即时生效、无动画。 | [08](08-状态与反馈.md) |
| **快速滚动条** | 仅滚动时出现的 12dp 宽主色滚动条，支持拖动跳转。 | [06](06-组件规格库.md) |

## 技术栈

| 术语 | 解释 | 相关章节 |
| --- | --- | --- |
| **Jetpack Compose** | Android 声明式 UI 工具包；本项目表现层全部由 Compose 实现。 | [01](01-设计定位与视觉DNA.md) |
| **Voyager** | Compose 多平台导航库；`TabNavigator` 承载 5 个一级 Tab，`Navigator` 承载页面栈。 | [04](04-导航与信息架构.md) |
| **Coil** | Compose 图片加载库；全局 300ms 交叉淡入。 | [05](05-动效系统.md) |
| **Haze** | Compose 背景模糊库；详情页封面背景使用 `blur(7dp)` / 大图预览 `10dp`。 | [05](05-动效系统.md) |
| **material-kolor** | 从 seed 生成 M3 调色板的库；Custom Theme 使用。 | [02](02-设计令牌.md) |
| **SAF** | Storage Access Framework；引导页通过目录选择器授予存储权限。 | [07](07-页面规格.md) |
| **LazyColumn / LazyGrid** | Compose 的懒加载列表/网格；转场时使用稳定 key 与 `contentType`。 | [05](05-动效系统.md) |
| **IntrinsicSize.Min** | Compose 中按内容最小高度对齐的测量方式；统计卡片三列等高依赖它。 | [06](06-组件规格库.md) |
