# 浏览：来源、扩展与仓库

`shared/app` 中的浏览一级页已从占位页替换为可完整演练的 Komikku 风格界面。所有服务调用通过可注入的 `BrowseService` 边界发生；未注入时界面显示明确空态，其余外壳照常可用。

## 结构

| 文件 | 职责 |
| --- | --- |
| `browse/BrowseModels.kt` | 不可变状态：`BrowseState`、`ExtensionInfo`、`ExtensionStoreInfo`、分组、`validateStoreUrl` |
| `browse/BrowseService.kt` | UI 与运行时之间的唯一接口，全部返回完整快照 |
| `browse/BrowseSession.kt` | 数据加载、条目级操作状态、去重与旧结果防护 |
| `browse/BrowseScreen.kt` | 来源 / 扩展两页、搜索、分组与条目操作 |
| `browse/ExtensionStoresScreen.kt` | 仓库增删改、启停、目录预览 |
| `browse/ExtensionDetailsScreen.kt` | 已安装扩展详情与卸载 |
| `navigation/AppRoute.kt` | 新增 `ExtensionDetailsRoute`、`ExtensionStoresRoute` |

## 主 agent 接真实服务所需的接口

### `BrowseService`

```kotlin
interface BrowseService {
    suspend fun loadSnapshot(): BrowseSnapshot
    suspend fun addStore(url: String): BrowseSnapshot
    suspend fun saveStore(storeId: String, name: String, indexUrl: String): BrowseSnapshot
    suspend fun removeStore(storeId: String): BrowseSnapshot
    suspend fun setStoreEnabled(storeId: String, enabled: Boolean): BrowseSnapshot
    suspend fun previewStore(storeId: String): StoreCatalog
    suspend fun installExtension(extensionKey: String): BrowseSnapshot
    suspend fun updateExtension(extensionKey: String): BrowseSnapshot
    suspend fun uninstallExtension(extensionKey: String): BrowseSnapshot
    suspend fun updateAllExtensions(): BrowseSnapshot
}
```

约定：

- 每个写操作返回**操作完成后的完整快照**；UI 不用增量事件拼状态。
- 失败抛 `BrowseServiceException(message)`，`message` 直接展示给用户。
- 可以慢，但不要乱序返回：会话层用 generation 丢弃被更新调用取代的旧快照，所以服务只需保证单次调用语义正确。

### 注入方式

```kotlin
App(browseService = realService)            // 推荐：显式传入
// 或测试/预览中用 CompositionLocal
CompositionLocalProvider(LocalBrowseService provides realService) { App() }
```

`App()` 无参重载传入 `null`，这是当前生产入口的状态。

## 事件与状态

| 状态 | 说明 |
| --- | --- |
| `BrowseState.pane` | `Sources` / `Extensions`，随标签栈保留 |
| `BrowseState.sourceQuery` / `extensionQuery` | 两个搜索词，分别保留 |
| `BrowseState.load` | `Loading` / `Ready` / `Failed(message)` / `Unavailable` |
| `BrowseState.itemOperations` | 扩展 key → `Running` / `Succeeded` / `Failed(kind, message)` |
| `BrowseState.storeOperations` | 仓库 id（或 `AddStoreTarget`）→ 同上 |
| `BrowseState.catalogStates` | 仓库 id → `Loading` / `Ready(catalog)` / `Failed(message)` |
| `BrowseState.updateCount` | 有更新的扩展数，接到浏览角标 |

操作规则都在 `BrowseSession`：

- 同一目标正在运行时的第二次提交被忽略（返回 `false`，不调用服务）。
- 每次写操作递增快照 generation；只有最新 generation 的响应才替换列表，旧响应不会覆盖新结果。
- 反馈按目标存放，一行失败不会污染其他行。

### 需要主 agent 补齐的数据映射

- `extensionKey = "$storeId::$packageName"`：同包名跨仓库变体因此可辨识，UI 与路由都用它。
- `ExtensionInfo.hasUpdate` 由 `availableVersionName != versionName` 推导，服务只需填这两个字段。
- 仓库预览的 `StoreCatalogEntry` 只含展示所需字段；真正安装仍走 `installExtension(key)`。

## 验收

`./gradlew --max-workers=2 :shared:app:jvmTest :apps:android:assembleDebug :apps:desktop:classes :shared:app:linkDebugFrameworkIosSimulatorArm64`

测试覆盖：

| 测试类 | 范围 |
| --- | --- |
| `BrowseScreensTest` | 两页渲染、按语言分组、搜索过滤、详情入口、切栏与跨 720 dp 状态保留、同包名双仓库可辨识、无服务空态 |
| `BrowseSessionTest` | 加载失败重试、条目级成功/失败、防重复提交、旧快照不覆盖新结果、仓库 CRUD、目录预览失败重试、全部更新、URL 校验、分组排序 |
| `BrowseOperationsUiTest` | 添加/编辑/启停/移除仓库、重复 URL 拒绝、目录预览与从预览安装、安装→更新→卸载、行内失败与重试、更新角标 |
| `BrowseJourneyTest` | 添加仓库 → 预览（失败重试）→ 从目录安装 → 更新 → 卸载；跨切栏与跨宽度后详情页与查询保留；无服务入口仍可达 |

截图（`shared/app/docs/screenshots/`）：

- `browse-narrow-sources.png`：420 dp 底栏布局，来源分组
- `browse-wide-extensions.png`：1100 dp 导航轨布局，扩展三分组与角标计数
- `browse-wide-stores.png`：1100 dp 仓库管理

复跑截图：`./gradlew :shared:app:jvmTest --tests '*BrowseScreenshotTest' -PreaderScreenshotDir=shared/app/docs/screenshots`

## 与 Komikku 的交互差异

1. **没有 Feed、迁移与全局搜索页**：原版浏览是来源 / Feed / 扩展 / 迁移四页 + 全局搜索入口；本轮按任务范围只做来源与扩展两页。
2. **没有安装系统权限与私有扩展确认**：原版 Android 端处理 `REQUEST_INSTALL_PACKAGES`、私有扩展卸载确认与 trust 对话框；这里统一走服务层安装，界面只呈现结果。
3. **仓库页面位置不同**：原版从「更多 → 设置 → 浏览」或扩展页溢出菜单进入；这里从浏览页顶部的「扩展仓库」按钮直接进入，返回仍在浏览栈内。
4. **无下拉刷新与悬浮滚动条**：原版用 `PullRefresh` / FastScroll；这里用普通 `LazyColumn`，顶部提供显式「刷新仓库」与状态反馈。
5. **无每仓库筛选与 NSFW 过滤**：原版支持 `nsfwOnly`、过滤页和安装器过滤；本轮不提供。
6. **无源固定与「最新」跳转**：原版来源项可固定、可进入 Latest 列表；这里来源仅展示，点击留给后续书架/浏览源任务。
7. **扩展详情是精简版**：原版详情含来源开关、隐身模式、清 Cookie、应用信息跳转；这里保留标识信息、来源列表、更新与卸载。
8. **安装/更新为同步反馈**：原版显示下载进度与可取消步骤（`InstallStep`）；这里的 `OperationStatus` 只有运行中/成功/失败，真实实现接入后可扩展为进度。
