# 浏览：来源、扩展与仓库

`shared/app` 中的浏览一级页已从占位页替换为可完整演练的 Komikku 风格界面。所有服务调用通过可注入的 `BrowseService` 边界发生；未注入时界面显示明确空态，其余外壳照常可用。

## 结构

| 文件 | 职责 |
| --- | --- |
| `browse/BrowseModels.kt` | 不可变状态：`BrowseState`、`ExtensionInfo`、`ExtensionStoreInfo`、分组、`validateStoreUrl` |
| `browse/BrowseService.kt` | UI 与运行时之间的唯一接口，全部返回完整快照 |
| `browse/BrowseSession.kt` | 数据加载、条目级操作状态、去重、快照收敛 |
| `browse/BrowseScreen.kt` | 来源 / 扩展两页、搜索、分组与条目操作 |
| `browse/ExtensionStoresScreen.kt` | 仓库增删改、启停、目录预览 |
| `browse/ExtensionDetailsScreen.kt` | 已安装扩展详情与卸载 |
| `navigation/AppRoute.kt` | 新增 `ExtensionDetailsRoute`、`ExtensionStoresRoute` |

## 主 agent 接真实服务所需的接口

### `BrowseService`

```kotlin
interface BrowseService {
    suspend fun loadSnapshot(): BrowseSnapshot

    // name 是用户输入的仓库名称；留空时由实现从索引推导
    suspend fun addStore(name: String, indexUrl: String): BrowseSnapshot
    suspend fun saveStore(storeId: String, name: String, indexUrl: String): BrowseSnapshot
    suspend fun removeStore(storeId: String): BrowseSnapshot
    suspend fun setStoreEnabled(storeId: String, enabled: Boolean): BrowseSnapshot
    suspend fun previewStore(storeId: String): StoreCatalog

    suspend fun installExtension(extensionKey: String): BrowseSnapshot

    // 目录变体安装：携带 repositoryId / sourceKey / scriptUrl / repositoryIndexUrl
    suspend fun installCatalogVariant(variant: CatalogVariantId): BrowseSnapshot

    suspend fun updateExtension(extensionKey: String): BrowseSnapshot
    suspend fun uninstallExtension(extensionKey: String): BrowseSnapshot
    suspend fun updateAllExtensions(): BrowseSnapshot
}
```

约定：

- 每个写操作返回**操作完成后的完整快照**。会话层只把返回值当作「提交已发生」的信号，真正的 UI 状态由随后一次 `loadSnapshot()` 决定，因此实现必须让 `loadSnapshot()` 反映已提交的写入。
- 失败抛 `BrowseServiceException(message)`，`message` 直接展示给用户。
- 可并发调用；会话层允许不同目标并行。同一目标运行中的重复提交在 UI 层被拒绝，实现无需自行去重。
- `installCatalogVariant` 是安装目录条目的唯一入口；`installExtension(extensionKey)` 保留给已安装源的直接重装/修复场景。
- `CatalogVariantId.repositoryIndexUrl` 是**该条目实际预览时的仓库索引地址**。仓库随后被编辑或删除不会改写已打开的变体身份；实现应用它核验脚本来源，而不是重新读取当前仓库地址。
- 预览返回的 `StoreCatalog.repositoryIndexUrl` 由会话层填入预览开始时的索引地址；`variantRefs()` 会把它带到每个变体上。

### 错误状态

| 场景 | 界面表现 |
| --- | --- |
| `loadSnapshot` 抛错 | `LoadState.Failed(message)`，整页显示错误与「重试」 |
| 单个写操作抛错 | 该条目 `OperationStatus.Failed(kind, message)`，行内错误文案；其他行不受影响 |
| `previewStore` 抛错 | 该仓库 `CatalogState.Failed(message)`，预览对话框内显示错误与重试 |
| 未注入服务 | `LoadState.Unavailable`，显示「浏览尚未连接」空态 |
| 调用被取消 | 不写入失败状态；目标清空状态后可立即重试，已提交的数据保持不变 |
| 刷新被取消 | 不转为 `LoadState.Failed`；保留原状态（首次加载则回到 `Ready` 空态）并允许重试 |
| 预览被取消 | 清除该仓库的 `Loading`，不写 `Failed`，可重新预览 |
| 预览关闭 / 仓库地址变更 / 仓库移除 | 立即作废该仓库的预览与在途请求；晚到的响应不会重新打开或替换 |

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
| `BrowseState.itemOperations` | 扩展 key 或 `CatalogVariantId.key` → `Running` / `Succeeded` / `Failed(kind, message)` |
| `BrowseState.storeOperations` | 仓库 id（或 `AddStoreTarget`）→ 同上 |
| `BrowseState.catalogStates` | 仓库 id → `Loading` / `Ready(catalog)` / `Failed(message)` |
| `BrowseState.updateCount` | 有更新的扩展数，接到浏览角标 |

操作规则都在 `BrowseSession`：

- 同一目标正在运行时的第二次提交被忽略（返回 `false`，不调用服务）。
- **快照收敛**：每次写操作完成后重新读取 `loadSnapshot()`，页面内容、操作状态与更新角标都对齐最终提交结果。并发调用无论完成顺序如何，UI 都收敛到服务的最新快照。
- 并发期间若已有更晚的写入落地，较早返回的读取结果被丢弃，不会把新提交覆盖回旧值。
- 失败只写自己的行状态，不替换任何已提交的数据。
- **取消不等于失败**：协程取消清除该目标的状态而不是写 `Failed`，刷新、预览和单项写操作都如此；已提交的条目、操作结果和角标不受影响，目标可立即重试。
- **预览生命周期**：每个仓库维护一个预览有效性标记。地址修改、仓库移除和关闭预览都会提升标记并清空缓存，因此在途请求即使更晚返回也不会写入，重新打开预览的结果也不会被旧请求覆盖。

### 身份模型

两层身份必须分开：

| 层 | 类型 | 组成 | 用途 |
| --- | --- | --- | --- |
| 已安装源 | `ExtensionInfo.key` | `"$storeId::$packageName"` | 列表行、详情路由、更新/卸载 |
| 目录变体 | `CatalogVariantId` | `repositoryId + sourceKey + scriptUrl + ordinal` | 预览行、变体安装 |

`CatalogVariantId` 的 `ordinal` 只在同一仓库列出完全相同的 `sourceKey` 与 `scriptUrl` 时用于区分，保证列表 key 唯一且稳定。`StoreCatalog.variantRefs()` 按条目顺序分配，供 UI 直接消费。

`StoreCatalogEntry` 携带：

- `packageName`：包名，用于与已安装源关联；
- `sourceKey`：仓库内脚本 key，默认等于包名；
- `scriptUrl`：解析后的脚本地址，可为空；
- `summary`：可选描述。

其余映射：

- `ExtensionInfo.hasUpdate` 由 `availableVersionName != versionName` 推导，服务只需填这两个字段。
- 预览中的安装事件调用 `installCatalogVariant(variant)`，实现据 `repositoryId` 找仓库、据 `scriptUrl` 取脚本；若 `scriptUrl` 为空，应回退到 `sourceKey` 解析。

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
