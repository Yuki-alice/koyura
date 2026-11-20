# Komikku 视觉规格书（第二版 · 面向 CMP 复刻）

一份**给 Compose Multiplatform 实现者看**的视觉规格：每个数值都能追到 Kotlin 源码行，每张截图都能对上代码路径，每个"设计意图"都和实测像素一致。

- **对应代码**：`komikku-app/komikku` · commit `290267dd4c99f5b46812240fb0e4bc6b3ee8c562`（本仓库 `reference/komikku`）
- **版本**：versionName `1.14.1` / versionCode `82`（`app/build.gradle.kts:31-32`）
- **目标读者**：用 KMP + CMP 复刻 Komikku 视觉的 `project x` 实现者
- **截图**：37 张手机 + 37 张平板 + 9 张组件特写，全部为实机/模拟器截图；正文内嵌 14 张关键对照图，其余见 [附录 B 索引](10-附录.md)
- **可验证**：`tools/` 下 4 个脚本可复算全部色值、量取全部尺寸（见 [00](00-导读与方法.md) §0.1）

---

## 这一版修了什么

第一版把"看起来像"的东西写成了"代码里是"的东西。本版逐条回到源码与像素重新核对，修正了以下**会影响复刻结果**的错误：

| # | 第一版的说法 | 实际代码 / 截图 | 影响 |
|---|---|---|---|
| 1 | 平板截图是 1600×1000px（1280×800dp） | 文件实际是 **960×600px**。按 720dp 断点 + 80dp 导航轨实测 60px 反推，是 **1280×800dp @ 0.75 px/dp** 的模拟器。dp 尺寸对，px 尺寸错 | 按 px 量比例会全部偏 1.67 倍 |
| 2 | "设置项内边距 16dp / 16dp" | 存在**两套**：通用设置组件 `PrefsHorizontalPadding=16dp / PrefsVerticalPadding=16dp`；`SettingsItems.kt` 的条目是 **24dp / 10dp**；平板双栏主列表选中行是 `horizontal=8dp` + `RoundedCornerShape(24dp)` | 三套被混成一套 |
| 3 | 书架 360dp=2 列、411dp=**3** 列、600dp=**4** 列、720dp=**5** 列、1280dp=9 列；公式 `floor((w+4)/132)`；单列宽"约 139dp" | 公式**漏了两项**：平板上的 **80dp 导航轨**，以及每项 `padding(4dp)`。正确式子是 `floor((w − rail − 16 + 4) / 132)`，封面宽还要再减 `2×4dp`。结果：411dp 是 **2** 列不是 3，600dp 是 **3** 列不是 4，720dp 是 **4** 列不是 5；1280dp 恰仍是 **9** 列，但封面是 **120dp** 不是 139dp（`📐实测` 90px ÷ 0.75 = 120dp，与公式吻合） | 411/600/720dp 上的列数全错 |
| 4 | "19 套静态主题，定义角色 33/35/28/22 / 36" | 角色总数不是 36 而是 **48**（M3 1.5 起新增 12 个 `*Fixed`）。实测：Default **33**、Catppuccin/GreenApple/Lavender/Mocha/Monochrome/Strawberry/CottonCandy **35**、MidnightDusk/Tako/Teal/Tidal/YinYang/Yotsuba **28**、Nord **32 dark / 31 light**、Cloudflare/Doom/Matrix/Sapphire **22** | 分母错，缺口被低估 |
| 5 | "Default 33/36，缺 3 个" | Default 缺 `scrim` `surfaceBright` `surfaceDim` + 全部 12 个 `*Fixed` = **15 个** | 复刻时会意外继承 M3 基线紫 |
| 6 | 导航栏选中态 = "药丸 + 主色图标" | 是 M3 `NavigationBarItem` / `NavigationRailItem` 默认 indicator：**`secondaryContainer` 药丸 + `onSecondaryContainer` 图标**。图标不是 `primary` | 取错色会让选中态变灰 |
| 7 | "动效只有四档" | 实测 **7 类**：tab fade-through 200ms、reader slide 200ms / fade 150ms、sheet 350ms、sheet 内导航 220ms+90ms delay / 90ms、设置高亮 200ms×5 往返 + 600ms delay、Coil crossfade 300ms×动画缩放、M3 elevation 120/150ms | 漏掉的动效会变成生硬切换 |
| 8 | 详情页顶栏 = "滚动后 surface@3dp" | **两个独立 alpha**：`titleAlpha` 由"首项是否完全滚出"驱动，`backgroundAlpha` 由"是否开始滚动"驱动；底色 = `surfaceColorAtElevation(3.dp) × backgroundAlpha` | 合并成一个会丢掉渐显 |
| 9 | "全部 M3 默认字阶" | 对，但漏了**阅读器把 `bodyLarge` 覆写为 `bodySmall`**（`ReaderActivity.kt:321-323`），阅读器内 `bodyLarge` 实际 12sp/16sp | 阅读器排版整体偏大 |
| 10 | 组件特写 8 张 | 实际 **9 张** | 索引对不上 |

> **色值本身没有错。** 本版用脚本重新解析 19 个 `*ColorScheme.kt`，与第一版 `assets/design-tokens.json` 逐字段比对，**0 处色值差异**。错的是**叙述、尺寸归属、缺失项和动效清单**：它把不同组件的数值串成了一条规则，把"代码没写"说成"用了默认值"，把像素换算算错了。

---

## 怎么用这份文档

### 三种读法

**A. 只要一套能用的 token（1 小时）**
1. [02 设计令牌](02-设计令牌.md) → `ColorScheme`、`Padding`、`Shapes`、`Typography`
2. [09 复刻指南](09-复刻指南.md) §9.2 CMP 主题骨架代码
3. [10 附录](10-附录.md) §A 19 套完整色值

**B. 完整复刻双端形态（3–5 天）**
1. [01 视觉 DNA](01-视觉DNA与设计原则.md) → 先定"哪些结构不能动"
2. [03 布局与响应式](03-布局与响应式.md) → 断点、双栏、网格
3. [04 导航与信息架构](04-导航与信息架构.md) → 5 Tab、返回栈、转场
4. [06 组件规格库](06-组件规格库.md) → 逐组件按表实现
5. [07 页面规格](07-页面规格.md) → 逐页对照截图
6. [08 状态与反馈](08-状态与反馈.md) → 空/错/载/多选/横幅

**C. 逐像素验收**
- [10 附录](10-附录.md) §D 验收清单（每条带代码出处）
- [10 附录](10-附录.md) §B 截图索引（含每张验证要点）

### 证据标注约定

所有数值按证据强度标注，**不混写**：

| 标记 | 含义 | 例子 |
|---|---|---|
| `📍代码` | 直接来自 Kotlin 源码，附 `文件:行` | `Padding.medium = 16.dp`（`Constants.kt:18`） |
| `📐实测` | 从截图像素量取，附换算过程 | 平板导航轨 60px ÷ 0.75 = 80dp |
| `🎯推断` | 基于代码+截图的分析，**非官方说法** | "左栏 450dp 上限是避免大屏空栏" |
| `⚠️缺口` | 代码里没有、需复刻者自己决定 | 桌面端无对应实现 |

**引用规则**：`📍代码` 可直接抄；`📐实测` 要连同设备参数一起引用；`🎯推断` 不得当规范。

---

## 关键结论（六条，全部可验证）

### 1. 不要复刻粉紫色
默认 `appTheme = MONET`（`UiPreferences.kt:20-23`）、`themeMode = SYSTEM`。截图的粉紫来自模拟器壁纸。**要复刻的是"语义角色 → 组件"的映射**（[02](02-设计令牌.md) §2.5），不是某组色值。

### 2. 形态切换只有一处，在 720dp
`isTabletUi()` 只读 `smallestScreenWidthDp >= 720`（`DisplayExtensions.kt:13,21-23`）。这一处决定：底栏↔导航轨、单栏↔双栏、底部弹层↔居中卡片。**没有第二套断点**。

### 3. 双栏左栏 = `min(width/2, 450dp)`
`TwoPanelBox.kt:31`。1280dp：450/830；900dp：450/450；720dp：360/360。这是平板上**唯一**的宽度公式。

### 4. 详情页顶栏有两个独立 alpha
标题在首项完全滚出时 0→1；背景在首项开始滚动时 0→1；底色 `surfaceColorAtElevation(3.dp)`。`MangaScreen.kt:467-474`。

### 5. 阅读器 chrome 是半透明 elevated surface
上下栏 = `surfaceColorAtElevation(3.dp).copy(alpha = 亮 0.95 / 暗 0.90)`（`ReaderAppBars.kt:104-106`）。页码是**描边文字**（`Color(45,45,45)` 4px stroke + `primary` 填充），不是胶囊底（`ReaderPageIndicator.kt:38-46`）。

### 6. 自绘的品牌语言只有三处
空状态颜文字（12 个随机表情，`displayMedium` 45sp）、5 个 Tab 的 `AnimatedImageVector`、封面占位色 `#1F888888`。其余全是 Material 3 + Material Icons。

---

## 与 `project x` 技术栈的映射

所有代码示例都写成 KMP/CMP 可直接用的形式，对齐 `docs/technology-stack.md`：

| Komikku（Android） | `project x`（KMP/CMP） | 备注 |
|---|---|---|
| `androidx.compose.material3` BOM `2026.06.01` | `org.jetbrains.compose.material3:material3:1.13.0-beta02` | **已验证**含 `MaterialExpressiveTheme`（[09](09-复刻指南.md) §9.1） |
| `com.materialkolor:material-kolor:5.0.0-alpha07` | `com.materialkolor:material-kolor:5.0.1` | **已验证** 5.0.1 有 `DynamicMaterialExpressiveTheme`，发布 iOS/桌面变体，`PaletteStyle` 9 值齐全 |
| `androidx.palette.graphics.Palette` | 需自实现 | `getBestColor()` 启发式见 [02](02-设计令牌.md) §2.6，⚠️KMP 无官方 Palette |
| Coil 3 `crossfade(300ms)` | Coil 3（已选型） | 一致；记得乘 `animatorDurationScale` |
| `isTabletUi()` 读 `LocalConfiguration` | 自算 `smallestScreenWidthDp` | CMP 桌面/iOS 无该字段，需用 `WindowSizeClass` 或窗口 dp 推导（[03](03-布局与响应式.md) §3.2） |
| `soup.compose.material.motion` | ⚠️无官方 CMP 版 | 用 `AnimatedContent` + 自写 `ContentTransform` 复刻（[05](05-动效系统.md)） |
| `me.saket.swipe:SwipeableActionsBox` | ⚠️仅 Android | 用 `anchoredDraggable` 自写（[06](06-组件规格库.md) §6.7） |
| `dev.chrisbanes.haze` 10dp | haze 支持 CMP | 仅详情页封面对话框用，非核心 |

---

## 文件索引

| 文件 | 内容 | 关键产出 |
|---|---|---|
| [00 导读与方法](00-导读与方法.md) | 证据链、核对方法、如何自验 | 验证脚本 |
| [01 视觉 DNA 与设计原则](01-视觉DNA与设计原则.md) | 识别特征、密度、层级、克制点 | 8 条不可动结构 |
| [02 设计令牌](02-设计令牌.md) | 三层色彩、19 套色值、AMOLED、字体、间距、圆角、透明度 | `ColorScheme`/`Padding`/`Shapes` |
| [03 布局与响应式](03-布局与响应式.md) | 720dp、双栏、网格、insets、横幅 | 双端骨架 |
| [04 导航与信息架构](04-导航与信息架构.md) | 5 Tab、返回优先级、转场、顶栏三态 | 路由与转场表 |
| [05 动效系统](05-动效系统.md) | 7 类动效的精确参数与触发条件 | 动效表 |
| [06 组件规格库](06-组件规格库.md) | 18 类组件的解剖、数值、状态 | 组件配方 |
| [07 页面规格](07-页面规格.md) | 17 个页面的入口、骨架、双端差异 | 页面规格 |
| [08 状态与反馈](08-状态与反馈.md) | 空/错/载、多选、下载态、手势、横幅 | 状态机 |
| [09 复刻指南](09-复刻指南.md) | CMP 主题骨架、组件配方、顺序、坑 | 可编译代码 |
| [10 附录](10-附录.md) | 19 套色值、截图索引、代码索引、验收清单 | 查表 |
| [GLOSSARY.md](GLOSSARY.md) | 术语（含 Komikku 特有概念） | — |

---

## 资源

- `assets/phone/` — 37 张，720×1440px（360×720dp @2x，18:9，手势导航）
- `assets/tablet/` — 37 张，960×600px（1280×800dp @0.75x，横屏）
- `assets/crops/` — 9 张组件特写
- `assets/design-tokens.json` — 19 套主题完整色值 + 字号/间距/圆角/透明度/动效/断点（本版已按源码重生成，见 `_meta`）

---

## 渲染说明

- Mermaid 图在 Typora / Obsidian / VS Code / GitHub 可渲染。
- 表格较宽，建议桌面端阅读。
- 截图用相对路径，离线可用。

---

## 授权与免责

- Komikku 代码与截图版权归原作者；本文档仅用于设计与技术分析。
- 引用数值请注明 commit `290267dd4c` 与设备形态（手机 360dp / 平板 1280dp）。
- `🎯推断` 为分析结论，不代表 Komikku 官方立场。
- **第一版存在事实错误，已作废。** 与其他资料冲突时以本版（含行号）为准。
