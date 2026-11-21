# 应用架构与迁移规划

2026-11-22 · [现行架构与包名规范](architecture.md) · [技术栈选型](technology-stack.md) · [验收基线](verification.md)

## 1. 目标与当前起点

本轮目标是把现有跨平台漫画应用整理为可独立演进的业务、展示和数据代码。沿用当前五个 Tab、页面外观、入口、重复点击动作、宽窄屏布局与返回路径。Komikku 提供功能与交互参考，验收以项目既有行为和本次确认的需求为准。作品、章节、收藏、阅读进度与下载记录继续兼容已有数据，Venera-Next 脚本、宿主回调和会话契约延续当前兼容基线。

| 已落地 | 本轮从哪里接着做 |
| --- | --- |
| Navigation 3 已有 Tab 根、共享子页和独立阅读器三层栈，五个 Tab 的重复点击动作已接线，路由键已分文件并显式注册 | 沿用栈与路由语义。缩小 `AppScaffold` 的职责，将页面构造逐步交给功能入口。 |
| Koin 已装配书架 ViewModel 及下载、更新、阅读器的 Store Holder | 保留这些容器。书架 ViewModel 目前包着 `LibrarySession`，仍须收拢状态和命令。确认各 Holder 的实际生命周期，尤其是目前由外壳创建的阅读器 Holder。 |
| `domain` 已有书架、历史、阅读、下载、更新和源相关契约，`data` 已有 SQLDelight、本地内容与任务实现 | 整理 `app` 内的 `Production*Repository` 和服务。把业务契约、数据实现与展示映射放回各层，以书架分类链路先验证。 |
| Browse、全局搜索、作品详情、历史的大量展示代码仍在 `app` | 和资源、预览、测试按业务一起迁入 `features`，以现有会话行为为迁移基线。 |
| 通用顶栏、弹层和页面状态已有设计系统组件。漫画阅读器还在 `features/reader/comic`，Browse 的封面接线引用了该包中的平台解码函数 | 页面迁移时复用设计系统。阶段 2 先收窄阅读器入口、解除封面依赖并验证退出保存，再拆出 `:shared:reader`。 |

现状入口见 [导航状态](../shared/app/src/commonMain/kotlin/navigation/AppNavigationState.kt)、[外壳装配](../shared/app/src/commonMain/kotlin/AppScaffold.kt)、[Koin 模块](../shared/app/src/commonMain/kotlin/di/AppKoin.kt)和[书架生产适配器](../shared/app/src/commonMain/kotlin/library/ProductionLibraryRepository.kt)。

## 2. 模块职责与依赖

箭头表示目标编译依赖。平台入口聚合在 `shared/app`，iOS 继续链接该模块产出的 `ReaderShared`。Android 当前为系统网页登录适配直接依赖 `shared/data`，这一条现有平台依赖单独记录，随平台适配迁移处理。

```text
apps/android、apps/desktop ──→ shared/app
apps/ios ──链接 ReaderShared──→ shared/app

shared/app ──→ shared/features、shared/reader、shared/domain、shared/data、shared/design-system
shared/features ──→ shared/domain、shared/design-system
shared/reader ──→ shared/domain、shared/design-system
shared/data ──→ shared/domain、shared/source-runtime、shared/network
shared/source-runtime ──→ shared/network
```

| 层 | 所有权与接口 |
| --- | --- |
| `app` | 主题与应用外壳、导航及目的地装配、会话开关、应用和平台能力的接线。Koin 在此组合生产实现，页面入口接收明确参数与回调。 |
| `features` | Compose 界面、页面状态、用户意图、展示模型和页面级任务。页面入口绑定容器，主体按状态渲染并上报事件。各功能包通过导航回调或 `domain` 契约协作。 |
| `reader` | 漫画阅读器页面入口及内部 MVI、Viewer、工具栏和手势。`app` 接入页面与所需契约，书架、详情和历史通过导航回调打开阅读器。 |
| `domain` | 业务身份、规则、用例和调用方所需的仓储及能力契约。签名使用业务值类型、`suspend` 和 `Flow`，保持可独立测试。用例承担跨仓储协调、业务校验或跨入口复用，简单读写可以直接用既有仓储契约。 |
| `data` | 仓储实现、SQLDelight 数据与事务、任务记录和恢复、外部数据映射、源与本地内容的适配。数据库行、HTTP DTO 和 JS 返回值在边界处转换为业务类型。 |
| `design-system` | 主题、设计令牌、通用 Compose 组件和资源。功能文案、业务行为和页面状态归功能包。 |
| `source-runtime` / `network` | 分别负责源脚本运行、宿主能力和协议，以及传输、按源会话和 Cookie。既有数据层与运行时依赖沿用，迁移时保持源回调和网络会话语义。 |

`features` 的编译依赖限制在业务契约和设计系统，`data` 的公开方法也使用业务契约类型。`app` 可以看到两侧并完成装配。相同业务操作由不同页面调用时共享规则和数据来源，页面各自维护自己的展示状态。生产代码切换到领域契约后，按调用方逐步退役旧展示侧仓储接口与临时适配。

### 通用组件的归属

随页面迁移复用 `:shared:design-system` 的 `EmptyScreen`、`LoadingScreen`、`ErrorScreen`、顶栏、弹层和封面组件。仅在多个页面确实共享视觉与交互时提取或补齐通用组件。组件从主题读取视觉参数，接收展示值、`Modifier`、事件回调和必要的内容插槽。页面提供功能文案，将事件交给状态容器或外壳。书架多选栏、详情章节行和阅读器工具栏留在各自的功能代码中。迁移时沿用当前尺寸、颜色、无障碍语义和点击反馈。 [现有状态组件](../shared/design-system/src/commonMain/kotlin/components/EmptyScreen.kt)

## 3. 本轮目标目录

以 [settings.gradle.kts](../settings.gradle.kts) 中已有的模块为起点，阶段 2 新增漫画阅读器模块 `:shared:reader`。下方 `features/` 内的其余条目继续作为同一 Gradle 模块里的功能包。本轮目录收敛以此树为目标，迁移后在 [现行架构](architecture.md) 更新已落地的结构。

```text
apps/
├── android/                       Android 入口与打包
├── desktop/                       JVM 入口与打包
└── ios/                           Xcode 入口，链接 ReaderShared
shared/
├── app/
│   └── src/commonMain/kotlin/
│       ├── App.kt                 三端共用的 Kotlin 应用入口
│       ├── shell/                 AppScaffold、Tab 外壳、源宿主全局叠层
│       ├── navigation/            路由键、栈、状态恢复、目的地接线
│       └── di/                    Koin 模块、会话与具体实现装配
├── features/
│   └── src/commonMain/kotlin/
│       ├── library/               书架、筛选、多选、分类管理
│       ├── browse/                Browse Tab、pane 布局与入口
│       │   ├── sources/           图源列表、源内搜索、全局搜索
│       │   ├── feed/              Feed
│       │   ├── extensions/        扩展管理、仓库、扩展详情
│       │   └── migration/         换源候选与确认流程
│       ├── details/               作品详情、章节、收藏与下载入口
│       ├── history/               阅读历史和续读入口
│       ├── updates/               更新列表、更新策略入口和失败反馈
│       ├── downloads/             DownloadsStore 与队列界面
│       ├── settings/              设置导航内容、偏好编辑与分组子页
│       ├── more/                  更多入口、随入口维护的静态工具页
│       ├── statistics/            统计展示
│       ├── onboarding/            首启引导
│       └── account/               账号相关展示
├── reader/                        :shared:reader，漫画阅读器
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/comic/      页面入口、ReaderStore、Viewer、Chrome、手势
│       │   └── composeResources/ 阅读器文案与资源
│       ├── androidMain/           Android 专用阅读器实现
│       ├── iosMain/               iOS 专用阅读器实现
│       ├── jvmMain/               桌面专用阅读器实现
│       └── commonTest/            Store、分页、手势与工具栏测试
├── domain/                        按 library、downloads、reader 等业务组织契约和用例
├── data/                          按业务组织实现，SQLDelight schema 保留现行路径
├── design-system/                 共用主题、组件和资源
├── source-runtime/                JS 源运行与宿主契约
├── network/                       传输与按源会话
└── media/                         保持现有独立构建
```

各模块沿用短目录、完整 `app.koyura…` 包名和 `package.kt` 约定。符号与目录随功能命名。功能字符串和其他资源放在所属模块的 `composeResources/`，随页面迁移默认语言和中文资源。容器与业务测试随归属进入对应模块，跨功能的导航和生产装配测试留在 `app`。Android、iOS、JVM 专用实现继续进入对应 source set。

## 4. 页面状态与生命周期

### 4.1 MVVM + UDF

默认用于普通列表、详情和偏好读写。页面将事件交给 AndroidX `ViewModel`，ViewModel 在 `viewModelScope` 内调用用例或领域契约，输出只读 `StateFlow<UiState>`。加载、内容、错误和可重试状态由 `UiState` 表达。纯静态内容直接渲染，弹层显隐、焦点、滚动位置等局部状态由合适的 Compose 状态持有。

```text
用户事件 → ViewModel → 用例或仓储契约 → 更新 UiState → Compose 渲染
```

### 4.2 MVI

用于多步操作、有相互约束的状态转移或存在并发任务的页面。继续使用 MVIKotlin，沿用现有 `StoreFactory` 和薄 ViewModel Holder。`Intent` 描述输入，Executor 执行读写、取消和订阅并发出 `Message`，Reducer 将 `State` 与 `Message` 归约为新状态。Holder 随所属作用域清理时调用 `dispose()`。

```text
用户事件 → Intent → Executor → Message → Reducer → State → Compose 渲染
```

两类容器的 `Effect` 或 `Label` 用于即时导航、Snackbar 等展示动作。需要重建后继续显示的错误进入页面状态。收藏、换源、合并、排队和进度写入由业务契约完成，界面反馈由操作结果派生。可跨页面运行的任务由应用会话或后台调度拥有，持久任务以已提交的任务记录为准。迁移期间允许旧接口作为适配，每个生产入口从一个容器读取展示状态。

| 页面或流程 | 目标实现 |
| --- | --- |
| 书架、分类管理、历史、作品详情、设置、统计、账号页 | MVVM + UDF。页面组可以复用业务契约，按各自的作用域持有展示状态。 |
| 图源列表、Feed、源内搜索、全局搜索 | MVVM + UDF。查询变更时取消旧请求、重置分页，并阻止过期结果覆盖新查询。每个搜索页持有自己的查询状态。 |
| 更新、下载队列、阅读器 | 保留现有 MVI。归并同一页面的生产状态入口，更新、下载与阅读操作的业务职责按层迁移。 |
| 扩展安装与更新、换源流程 | MVI。展示确认、进行中、失败和可重试状态。源运行时事务和书架迁移事务由各自的业务契约承接。 |
| 更多与首启引导 | 入口列表或局部步骤用 Compose 状态。首启完成标记、需要跨页面共享的设置和其他持久读写由业务契约提供。 |

### 4.3 状态归属与恢复

| 作用域 | 内容与释放时机 |
| --- | --- |
| Compose 局部 | 焦点、滚动、手势与临时浮层。只对需要随界面重建保留的状态使用 `rememberSaveable`。 |
| Tab 根 | 书架筛选与多选、历史搜索、Browse 选中的 pane 等既有 Tab 状态在切换 Tab 时保留。持有者随 Tab 根或所属应用会话释放。 |
| 导航条目 | 详情、搜索、设置子页和阅读器各自的展示容器绑定导航条目。以 `lifecycle-viewmodel-navigation3` 管理条目的 ViewModel 作用域，条目移除时取消页面任务并清理 Store。绑定前用现有返回和恢复场景验证持有者生命周期。 |
| 应用会话或后台 | 数据库、源运行实例、按源网络会话、下载工作与后台更新调度按现有宿主生命周期管理。应用退出或会话关闭时释放资源，进程重启后的持久任务由数据层恢复。 |

路由键继续由 `app/navigation` 定义和注册，功能入口通过回调传递业务标识。现有三层栈和 `SavedStateConfiguration` 沿用。恢复优先使用可序列化的标识和必要参数，页面控制项按需保存，业务数据重新从契约读取。阅读器当前路由还携带章节列表，只有在目录重载能保持现有续读和离线路径后，才考虑缩减参数。任何路由键改名或迁包都要验证旧的序列化身份与恢复语义。

### 4.4 阅读器的调用边界

| 调用方 | 接口 |
| --- | --- |
| 书架、详情与历史 | 调用 `onOpenReader(...)`，传入作品、章节和现有续读所需参数。`app` 负责构造现有路由、打开阅读器和处理返回。 |
| 应用外壳 | 调用一个公开的阅读器 Compose 入口，传入阅读参数、所需业务契约与退出回调。入口在导航条目作用域内绑定 Holder。 |
| 阅读器与设置页 | 共享 `domain` 的 `ReaderPreferences` 契约，读写全局偏好与单作品覆盖。 |
| 阅读器内部 | 通过 `ReaderService` 打开章节、加载图片、续读和保存进度。Store、Reducer、Viewer、翻页、缩放、重试和工具栏组件保持模块内可见。 |

[ReaderService](../shared/domain/src/commonMain/kotlin/reader/ComicReader.kt) · [现有阅读器路由](../shared/app/src/commonMain/kotlin/navigation/ReaderRoutes.kt)

## 5. 按业务链路迁移

这些阶段描述推荐实施顺序。每次交付以一个完整行为或一组配套移动为单位，使用 [验收基线](verification.md)。迁移现有行为时优先沿用已有测试，再补旧测试无法覆盖的回归。各阶段的实施顺序、代码定位、测试和技能入口见 [阶段规划索引](architecture-refactor/README.md)。

### 阶段 1 · 书架与分类样板

1. 从「更多 → 分类管理」和书架分类操作开始。复用 `domain` 的 `LibraryRepository` 及 `data` 的 `LibraryStore`，对照目前 `ProductionLibraryRepository` 的行为，先迁分类读写和分类变更后的刷新。
2. 分类管理页面归入 `features/library`。保留书架和分类页各自的 `UiState`，两侧从同一个业务契约读取已提交的分类数据。数据层负责写入和查询，展示层负责筛选、排序控件和展示映射。
3. 沿同一边界迁移收藏、批量命令、封面与继续阅读查询。保留各操作原有的失败、撤销和刷新语义。逐步把 `LibrarySession` 中的展示状态和命令收拢到现有 `LibraryViewModel`，待生产入口和测试都已切换后移除旧状态入口。

优先复用 `LibraryStoreTest`、`LibraryViewModelTest`、`CategoriesUiTest` 及书架重启回归。核对新增、改名、排序或删除分类后书架可见结果，以及已有收藏和读档是否正常。

### 阶段 2 · 下载、更新与阅读器

阅读器按以下顺序迁移。前三步在现有模块内完成，第四步再拆出独立 Gradle 模块。

1. **收口接口。** 按 §4.4 将路由和退出接线留在 `app`。收拢外壳对 Holder 与 Store 的直接使用，通过单一阅读器入口接入 MVI。在现有书架、详情和历史入口验证打开与返回，沿用原有路由参数、`ReaderService` 和 `ReaderPreferences`。
2. **解除封面解码依赖。** 沿用 `CoverDecoder` 契约，将 Browse 的 `CoverImageLoaders` 接到独立的封面解码实现，移除对 `features.reader.comic.decodeToImageBitmap` 的引用。保留封面加载、缓存和失败占位行为，以及阅读器图片解码、裁边和失败重试。[当前封面装配](../shared/app/src/commonMain/kotlin/browse/CoverImageLoaders.kt)
3. **验证退出保存。** 当前退出会发出异步 `SaveCurrent` 后立即返回。改用导航条目持有 Store 时，在出栈前明确最后一页保存的完成点，或将包含作品、章节、页键与页索引的快照交给生命周期长于页面的保存路径。验证翻页后快速返回、切章、切换作品和重启续读，确保 Store 清理时最后一笔有效进度仍能落盘，且保存行为遵守现有无痕模式。[当前退出逻辑](../shared/features/src/commonMain/kotlin/reader/comic/ReaderScreen.kt)
4. **拆出 `:shared:reader`。** 迁入阅读器入口、Store、Viewer、Chrome、三端专用实现、资源及阅读器测试。在 `settings.gradle.kts` 注册模块，由 `app` 依赖并装配，iOS 继续通过 `ReaderShared` 使用。生产服务按职责留在数据或平台接线中。更新全量 JVM 命令覆盖 `:shared:reader:jvmTest`，并在模块接入后验证 Android、桌面和 iOS 构建。

按 [验收基线](verification.md)复用 Store、导航与阅读回归。核对书架、详情和历史入口、返回顺序、宽窄屏、五种阅读模式、切模式保位、手势、工具栏和离线续读。封面解码改动按图片路径回归口径验证。

同阶段的下载与更新沿用已有 Store。下载队列和详情下载入口共享 `ChapterDownloads` 能力，分别安置 `ProductionDownloadQueueRepository` 的数据读取和展示映射。更新查询与展示转换按层迁移，保持后台扫描、新章节触发下载、离开页面持续运行和重启恢复。

### 阶段 3 · Browse 与详情

1. 先迁图源列表、Feed、源内搜索和全局搜索的展示、资源与测试。`BrowseScreen` 继续控制 pane 布局和选中状态。把查询、翻页、取消和结果状态迁入对应 ViewModel，源查询实现通过业务契约提供。
2. 扩展管理与仓库页面迁入 `features/browse/extensions`，将 `BrowseSession` 中的安装、更新、启用、卸载和预览状态移到对应容器。安装提交、失败回滚和重启恢复继续使用现有扩展服务。
3. 把 `SourceScreens.kt` 中的作品详情迁入 `features/details`。详情 ViewModel 通过书架、下载及来源契约获取状态，导航回调仍由 `app` 接线。换源流程的展示状态放进 `browse/migration` 的 Store，书架数据的迁移和合并继续归书架业务。按消费者逐步收拢 `BrowseSession` 与现有展示侧服务接口。

复用 `BrowseSearchPagingTest`、`BrowseConvergenceTest`、扩展一致性、`LibraryMigrationTest` 和详情下载测试。核对 query 竞态、历史入口打开详情、扩展失败后旧源可用，以及换源后收藏、分类和进度的延续。

### 阶段 4 · 历史、设置与外壳收敛

1. 将历史页与展示模型迁入 `features/history`。以 ViewModel 管理搜索、分组和多选状态。继续阅读从现有历史和章节契约取得定位值，由应用外壳处理导航。
2. 将设置、统计、账号的异步页面状态归入对应 ViewModel，首启和更多保留各自入口角色。设置保持现有落盘格式和宽窄屏路径。Multiplatform Settings、Kermit、Turbine 依 [已定排期](technology-stack.md#选型升级排期)在架构收尾另行接入。
3. 将外壳、目的地接线与 DI 归入目标目录。把 `AppScaffold` 内仍在执行的业务查询、修改和展示映射迁给各功能入口。收拢同一会话的仓储与容器装配，按消费方替换生产路径上的临时 `CompositionLocal` 与旧 Session。Compose 主题和平台宿主所需的局部能力继续依现有用途持有。
4. 按目标目录核对资源、包名、依赖和测试归属。平台改动独立交付。有合适的改动窗口时把 Android 网页登录适配收进 `shared/app` 的 Android 接线，验证捕获与会话交接后移除 `apps/android → shared/data` 的直接依赖。

复用历史持久化、设置重启、AppShell 和路由恢复测试。走一遍五个 Tab 的重复点击、切换、返回、首启、设置宽窄屏与阅读器退出路径。入口或平台改动按验收文档构建 Android、桌面和 iOS framework。

## 6. 交付检查

- 最终目录符合 §3。`app` 负责装配与导航，功能业务走 `domain` 契约，`data` 返回业务类型，`features` 与 `reader` 持有各自的展示状态。阅读器调用遵循 §4.4，封面接线通过它自己的适配获取解码能力。
- 每个动态页面具有明确的状态容器和生命周期。书架、历史、详情等走 MVVM + UDF，阅读器、下载等按上表走 MVI。退出页面后验证页面任务释放，持续任务仍按业务生命周期运行。
- 受影响模块先跑 JVM 快速回归，交付前按 [验收基线](verification.md)跑全量 JVM 门。移动测试时检查实际任务覆盖，以行为断言和现有夹具验证回归。
- 涉及入口或平台代码时跑三端构建。涉及 `source-runtime`、网络会话桥或宿主回调时，按验收文档跑 Venera 原版脚本 SHA-256 和全回调链校验。其中涉及 HTML、会话或图片路径的改动，同时覆盖原版社区源夹具。
- 涉及界面时按项目既有行为与本次确认的交互要求核对。每个提交对应一条行为迁移或一组配套重构。
