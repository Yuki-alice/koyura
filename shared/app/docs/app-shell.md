# 应用外壳与五栏导航

Android、桌面 JVM 和 iOS 都通过 `dev.reader.app.App()` 进入同一套五栏外壳。外壳复用 `shared/design-system` 的 `ReaderNavigationBar`、`ReaderNavigationRail` 和 `NavTab`，不修改其公开 API。

## 结构

| 文件 | 职责 |
| --- | --- |
| `App.kt` | `App()` 入口、主题、基于 `BoxWithConstraints` 的宽度判定 |
| `AppScaffold.kt` | 底栏 / 导航轨两种布局、安全区分摊、`NavDisplay` 接线、跨栏返回处理 |
| `navigation/AppRoute.kt` | 可序列化路由键 `TabRootRoute`、`DetailRoute` 与 `ReaderSavedStateConfiguration` |
| `navigation/AppNavigationState.kt` | 选中标签 + 五条独立返回栈，以及重选、压栈、返回规则 |
| `ui/TabScreens.kt` | 五个资源化根页与占位详情子页 |

## 宽度规则

内容可用宽度低于 720 dp 用底栏，达到 720 dp 用导航轨。720 dp 取自 Komikku `DisplayExtensions.kt` 的 `TABLET_UI_REQUIRED_SCREEN_WIDTH_DP`。本轮采用实时窗口宽度（`BoxWithConstraints.maxWidth`），不读取设备最小宽度，因此桌面拖拽缩放和移动端分屏都会即时切换。判定收敛在纯函数 `usesNavigationRail(width)`，719 / 720 dp 边界可直接测试。

宽度变化只切换布局，不重建导航状态：五条栈与选中标签由 `rememberNavBackStack` 和 `rememberSaveable` 持有，切换时保留。

## 返回规则

| 场景 | 行为 |
| --- | --- |
| 当前标签有子页 | 弹出当前标签的子页，其他标签不受影响 |
| 当前标签在根页且不是书架 | 切到书架 |
| 书架在根页 | 不消费，交回平台 |

子页返回由 `NavDisplay` 的返回处理负责，跨栏返回由外壳的 `BackHandler` 负责，两者启用条件互斥，避免同一次手势被两个处理器争抢。

## 重选语义

点击当前已选标签只触发一次重选回调，不压入路由，也不清空该标签的栈。`AppShell(onTabReselect = ...)` 暴露该事件，后续任务可用来打开设置页、滚动到顶部或开始搜索。

## 资源与文案

五个根页的标题与占位正文都走 Compose Resources，英文为默认资源，另有简体中文（`values-b+zh+CN`）。标题同时作为页面内可见大标题，便于在当前阶段辨识所在栏目。

## 安全区

系统栏内边距按「谁会覆盖该边」分配，不做整体 `safeDrawingPadding`：

- 底栏布局：底栏自己消费水平与底部内边距；内容区只消费顶部与水平内边距。
- 导航轨布局：导航轨消费垂直与起始边；内容区消费顶部、结束边与底部。

这样内容不会被系统栏遮挡，也不会出现同一内边距叠加两次造成的双倍留白。

## 测试

`:shared:app:jvmTest` 共 24 个用例：

| 测试类 | 覆盖 |
| --- | --- |
| `AppNavigationStateTest` | 默认书架、五栏各有根页、切换不破坏他栏、重选只发一次事件、子页优先弹出、其他栏根页返回书架、书架根页不处理 |
| `ShellWidthTest` | 719 / 720 / 0 / 360 / 721 / 1280 dp 的布局判定 |
| `AppShellUiTest` | 底栏与导航轨渲染、根页标题、子页压栈与返回、重选点击、布局切换后保留标签与栈、导航选中态 |
| `AppShellWidthUiTest` | 真实 719 dp 底栏、720 dp 导航轨、跨边界缩放后标签与栈保留 |
| `ScreenshotCaptureTest` | 输出 `docs/screenshots/` 下的三张 PNG |

截图命令：

```bash
./gradlew :shared:app:jvmTest --tests 'dev.reader.app.ScreenshotCaptureTest' \
  -PreaderScreenshotDir=shared/app/docs/screenshots
```

## 与 Komikku 的差异

1. **未使用 Voyager**：原版用 Voyager 的 `TabNavigator` 管理五个 Tab；这里按 `docs/technology-stack.md` 采用 Navigation 3，每个标签一条 `NavBackStack`，用可序列化路由键恢复。
2. **宽度判定来源不同**：原版 `isTabletUi()` 读 `smallestScreenWidthDp`，另有偏好可强制平板/手机布局。这里按 720 dp 应用到实时内容宽度，暂不提供强制布局偏好。
3. **返回处理分层不同**：原版只用 `BackHandler` 处理「非书架时回书架」，子页返回交给 Voyager。这里子页返回交给 `NavDisplay`，跨栏返回单独处理，并按启用条件互斥。
4. **重选动作未落地**：原版各 Tab 的 `onReselect` 分别打开设置、下载队列、全局搜索等。本轮只暴露事件，不实现具体动作。
5. **页面内容为占位**：五个根页只有标题和占位正文；书架数据、浏览子页和设置功能留给后续任务。
6. **无标签切换动画**：原版用 `AnimatedContent` + Material fade through。这里沿用 `NavDisplay` 默认场景过渡，未定制五栏之间的淡入淡出。
