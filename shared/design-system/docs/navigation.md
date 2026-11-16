# 一级导航共享组件

`app.koyura.designsystem.navigation` 提供 Komikku 一级导航的跨平台共享实现：同一套五标签类型，以及窄屏底栏、宽屏导航轨两个 Composable。宽窄屏判定、返回栈、首选项和角标数据由应用层负责。

## 公开 API

```kotlin
package app.koyura.designsystem.navigation

enum class NavTab { Library, Updates, History, Browse, More }

enum class NavLabelMode { AlwaysShow, SelectedOnly }

@Composable
fun ReaderNavigationBar(
    selectedTab: NavTab,
    onTabClick: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    visibleTabs: Set<NavTab> = NavTab.entries.toSet(),
    labelMode: NavLabelMode = NavLabelMode.AlwaysShow,
    updatesBadgeCount: Int = 0,
    browseBadgeCount: Int = 0,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
)

@Composable
fun ReaderNavigationRail(
    selectedTab: NavTab,
    onTabClick: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    visibleTabs: Set<NavTab> = NavTab.entries.toSet(),
    labelMode: NavLabelMode = NavLabelMode.AlwaysShow,
    updatesBadgeCount: Int = 0,
    browseBadgeCount: Int = 0,
    windowInsets: WindowInsets = NavigationRailDefaults.windowInsets,
)
```

行为约定：

- 标签顺序固定为「书架 → 更新 → 历史 → 浏览 → 更多」。`visibleTabs` 是集合，渲染前按该规范顺序重新排序，隐藏标签不会打乱其余顺序。
- 点击任何可见标签都回调一次 `onTabClick`，包括当前已选中标签，由调用方决定切页还是处理重选。
- `updatesBadgeCount`、`browseBadgeCount` 仅在大于 0 时显示，显示实际计数；角标文案用 Compose Resources 复数资源。
- 标签文案和图标无障碍名称来自 Compose Resources，英文为默认资源，另有简体中文（`values-b+zh+CN`）。
- 标签单行省略，样式为 `labelLarge`；选中指示与配色沿用 Material 3 默认值。

## 视觉参数

| 项目 | 取值 |
| --- | --- |
| 底栏内容区高度 | 80 dp |
| 底栏项宽度 | 由 M3 `NavigationBarItem` 内部 `weight(1f)` 等分 |
| 导航轨最小宽度 | 80 dp |
| 导航轨垂直内边距 / 项间距 | 4 dp |
| 导航轨容器标高 | 3 dp |
| 选中指示 | M3 `NavigationBarItem` / `NavigationRailItem` 默认 |

## 与 Komikku 的差异

1. **图标动画**：原版用 `AnimatedVectorDrawable`（`anim_library_enter.xml` 等）做选中过渡，Android 专属。这里改为按动画终态拆分的静态 XML 矢量：未选中用起始轮廓，选中用 `valueTo` 终态。无过渡动画。
2. **历史图标**：原版动画是箭头组旋转回原位，起止视觉相同，因此只保留一份 `nav_history.xml`，不区分选中态。
3. **更多图标**：原版动画是三点依次弹跳，起止视觉相同，同样只保留一份 `nav_more.xml`。
4. **角标语义**：原版在角标 `Text` 上设置 `contentDescription`。这里保持一致；但 M3 在 `alwaysShowLabel = true` 时会清除图标/角标语义、只暴露标签文本，因此仅在「仅选中项显示标签」模式下角标描述可被无障碍读取。原版底栏同样受 M3 该行为影响。
5. **间距来源**：原版 `NavigationRail` 用 `MaterialTheme.padding.extraSmall`（项目自定义 4 dp）。这里用模块内常量 `NavigationRailVerticalPadding = 4.dp`，避免依赖应用层主题扩展。
6. **图标资源格式**：`nav_library_*`、`nav_more` 的原始 viewport 是 1080×1080，已原样保留为 `24dp` 显示尺寸，与原版渲染一致。

## 验证

截图位于 `docs/screenshots/`：

| 文件 | 场景 |
| --- | --- |
| `navigation-bar.png` | 窄屏底栏，书架选中，更新/浏览均有角标 |
| `navigation-rail.png` | 宽屏导航轨，历史选中，更新/浏览均有角标 |
| `navigation-bar-hidden-tabs.png` | 隐藏更新与历史后保持原顺序 |
| `navigation-bar-zero-badges.png` | 角标为 0 时不显示 |
| `navigation-bar-selected-only-label.png` | 仅选中项显示标签 |
| `navigation-rail-selected-only-label.png` | 导航轨仅选中项显示标签 |
| `navigation-bar-zh.png` | 简体中文资源 |

截图由 JVM 离屏 `ImageComposeScene` 渲染，非手工绘制。验证同时覆盖：五个标签逐一点击与重复点击当前标签各回调一次、隐藏标签的顺序重组、仅选中项显示标签时的无障碍名称与选中语义、角标描述包含实际计数。
