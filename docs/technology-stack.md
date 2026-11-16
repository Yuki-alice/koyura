# 技术栈选型

2026-11-15 · Greenfield 项目 · [架构设计与项目目录](architecture.md)

项目用 Kotlin Multiplatform 实现业务和 JS 源系统，用 Compose Multiplatform 复刻 Komikku 的界面、交互和可见功能。小说接入与差异化功能按[当前对齐计划](komikku-alignment.md)安排在漫画对齐之后。账号、书架、备份格式和内部数据模型独立设计。JS 脚本、宿主 API、源仓库和更新行为以 Venera-Next 为兼容基线。

候选库来自本地 [kmp-awesome `33879a1`](https://github.com/terrakok/kmp-awesome/blob/33879a1b240c15cee9b1b82cd260a77680382021/README.MD#libraries) 的网络、存储、架构、依赖注入、Compose UI、文件、序列化、测试和工具条目。该列表优先收录 Android 与 iOS 库，选型同时以库作者文档、发布物和桌面 ABI 为依据。

## 平台与构建基线

| 目标 | 构建方式 | 首次验证范围 |
| --- | --- | --- |
| Android | KMP Android library + 独立 Android application | API 26 起，arm64 真机与 x86_64 模拟器 |
| iOS | Kotlin/Native + Xcode 应用壳 | iOS 16 起，arm64 真机与 Apple Silicon 模拟器 |
| macOS | Compose Desktop JVM + jpackage | macOS 13 起，arm64 |
| Windows | Compose Desktop JVM + jpackage | Windows 10 起，x64 |
| Linux | Compose Desktop JVM + jpackage | Ubuntu 22.04 起，x64 |

平台和系统下限是本次设计基线。先跑 Android 与 macOS 的端到端样例，同时验证 iOS 的原生链接，再补 Windows 与 Linux 的安装包回归。浏览器版需要重新验证脚本执行、跨域请求与文件存储，单独立项。桌面启动器统一走 JVM，各安装包在目标系统构建。 [Compose 平台支持](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) · [桌面打包](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)

| 构建项 | 起始版本或约定 |
| --- | --- |
| Kotlin、KMP Gradle 插件、Compose Compiler 插件 | `2.4.21`。本机 Gradle 插件、Compose Compiler 和 Kotlin/Native 同版本均已缓存。终端独立 `kotlinc` 是 `2.4.10`，项目构建由 Wrapper 和版本目录解析编译器 |
| Compose Multiplatform 与 Material 3 | 按 JetBrains 官方 `1.13.0-beta02` 组件表选用 Compose 插件、Runtime、UI、Foundation 与 Resources，直接使用 `org.jetbrains.compose.material3:material3:1.13.0-beta02`。本机已有对应缓存 |
| Android Gradle Plugin / Gradle Wrapper | `9.2.1` / `9.4.1`，复用本机 AGP 缓存与已解压的 Wrapper。共享库用 `com.android.kotlin.multiplatform.library`，应用壳用 `com.android.application` |
| JDK | 复用 Amazon Corretto `21.0.12.1`，当前 Gradle 已用 `~/devtools/jdk-21` 启动。Android Studio 自带 JBR `25.0.3`，运行 IDE 时沿用其自带配置 |
| Android SDK | `compileSdk 37.1`。官方 Compose Android 构件的 AAR 元数据要求 37.1，本机主 SDK 已安装 `platforms;android-37.1`。复用现有 Build Tools `36.0.0`、NDK `28.2.13676358`、SDK CMake `3.22.1` 和 `han1me-16kb` AVD |
| Xcode | 复用本机 `26.6`、iOS `26.5` SDK 与模拟器运行时。Kotlin 兼容表列 `26.4`，本机已用 Kotlin/Native `2.4.21` 生成最小 arm64 模拟器 framework |
| 版本管理 | Gradle Kotlin DSL、Wrapper 与 `gradle/libs.versions.toml` 管理版本。本机 JDK 和 SDK 路径用本地配置，仓库保存可复现版本 |

本机为 Apple Silicon，运行 macOS `26.6.2`。Android 37.0 在 AAR 元数据检查时被拒绝。共享 Android 库和应用壳统一按 `compileSdk { version = release(37) { minorApiLevel = 1 } }` 使用 37.1，AGP `9.2.1` 会提示 37.1 超出其官方测试范围。初始化工程已在 Android 16 模拟器、iOS 26.5 模拟器和打包后的 macOS arm64 应用中启动。 [运行说明](../README.md#本地运行) · [KMP 兼容表](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html) · [AGP 9.2 系列兼容表](https://developer.android.com/build/releases/agp-9-2-0-release-notes) · [Android 次级 API 配置](https://developer.android.com/build) · [JetBrains 官方 CMP 组件表](https://kotlinlang.org/docs/multiplatform/whats-new-compose-113.html#dependencies)

## 生产依赖

| 职责 | 选定方案 | 选型依据 |
| --- | --- | --- |
| 界面与资源 | Compose Multiplatform `1.13.0-beta02`、官方 Material 3 `1.13.0-beta02`、Compose Resources | 在共享层实现主题、导航布局、书架、浏览和阅读器。字体、图标和字符串由项目管理。 [官方组件表](https://kotlinlang.org/docs/multiplatform/whats-new-compose-113.html#dependencies) · [Compose Resources](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html) |
| 导航与页面状态 | Navigation 3 `1.2.0`、JetBrains Lifecycle / ViewModel `2.11.0`、`StateFlow` | 自管五栏独立返回栈，使用序列化路由恢复页面。桌面加入 `kotlinx-coroutines-swing` 提供 Main dispatcher。Voyager 和 Decompose 均可实现导航，本项目由 Navigation 3 和现有 Lifecycle 生态覆盖这两项职责。 [Navigation 3](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html) · [Navigation 3 发布物](https://repo.maven.apache.org/maven2/org/jetbrains/androidx/navigation3/navigation3-ui/1.2.0/) · [跨平台 ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html) · [Voyager](https://voyager.adriel.cafe/) · [Decompose](https://github.com/arkivanov/Decompose) |
| 依赖注入 | Koin 4.2，Kotlin DSL + 构造器注入 | 在应用装配层注册实现与生命周期。仓储、用例和脚本宿主保持显式依赖。Metro 的编译期校验可作为后续选项，首期以普通 Kotlin DSL 降低编译插件耦合。 [Koin Compose](https://insert-koin.io/docs/reference/koin-compose/compose/) · [Metro](https://github.com/ZacSweers/metro) |
| 并发和数据契约 | Coroutines `1.11.0`、Serialization JSON `1.12.0`、DateTime `0.8.0` | 复用缓存版本，用 `suspend` 和 `Flow` 表达任务与状态订阅。绝对时间用 `kotlin.time.Instant`，本地时间和时区转换用 datetime。 [Coroutines](https://github.com/Kotlin/kotlinx.coroutines) · [Serialization](https://github.com/Kotlin/kotlinx.serialization) · [DateTime](https://github.com/Kotlin/kotlinx-datetime) |
| 主数据库 | SQLDelight `2.4.1`、SQLite、`coroutines-extensions` | 复用已缓存的 SQLDelight 插件和驱动版本。用 `.sq` 明确按分类、来源和历史联查的查询与索引，用 `.sqm` 跟踪迁移。普通库按平台使用官方 Android、Native、JVM SQLite 驱动。若引入 FTS5，先核对三类驱动的行为。 [SQLDelight 2.4.1](https://github.com/sqldelight/sqldelight/releases/tag/2.4.1) · [驱动指南](https://sqldelight.github.io/sqldelight/2.3.2/multiplatform_sqlite/) |
| 偏好与密钥 | Multiplatform Settings `1.3.0` + `SecretStore` 平台实现 | Settings 存主题和普通设置，观察接口由应用封装成 `Flow`。密钥和追踪服务凭据走平台密钥存储。任务状态、源配置的版本记录和书架状态归数据库。 [Multiplatform Settings](https://github.com/russhwolf/multiplatform-settings) · [Android Keystore](https://developer.android.com/privacy-and-security/keystore) · [Keychain](https://developer.apple.com/documentation/security/keychain-services) |
| HTTP 与 Cookie | Ktor Client `3.6.1`、Android / 桌面 JVM 用 OkHttp、iOS 用 Darwin | 扩展、资源、追踪和同步共用传输基础设施。源 Cookie 的访问、保存与网页登录交接按 Venera-Next 契约实现。 [Ktor 请求引擎](https://ktor.io/docs/client-engines.html) · [Venera-Next Cookie API](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/api/js.zh.md#cookie) |
| 图片 | Coil `3.6.3` 与 `MediaLoader` | 用 Coil 解码和呈现封面与图片，源资源通过自定义取件器走同一个会话边界。阅读器负责长图分块、预读窗口、缩放与离线读取策略。 [Coil 图片管线](https://coil-kt.github.io/coil/network/) |
| 文件与压缩 | Okio `3.18.2`、FileKit `0.12.0`、libarchive | 复用已缓存的 Okio 和 FileKit，分别负责流式 I/O、ZIP 读取与平台文件对话框。libarchive 为其他归档格式适配方案。目录权限由平台管理。 [Okio 文件系统](https://lysine.dev/okio/file_system/) · [FileKit 平台能力](https://filekit.mintlify.app/introduction) · [libarchive](https://github.com/libarchive/libarchive) · [Komikku 本地格式](https://github.com/komikku-app/komikku/blob/383ffaa81667f45fc6c2975edb1760a7606531d3/source-local/src/commonMain/kotlin/tachiyomi/source/local/io/Archive.kt) |
| 网页解析与小说 | Fleeksoft Ksoup `0.2.6`、Compose 文本排版、EPUB 3 解析 | Ksoup 在宿主侧提供 Venera 的 CSS 选择器与 HTML 对象接口。小说统一进入结构化正文模型，EPUB 从容器、OPF 与 spine 解析顺序。 [Fleeksoft Ksoup](https://github.com/fleeksoft/ksoup) · [EPUB 3.3](https://www.w3.org/TR/epub-33/) |
| JS 执行 | QuickJS + `io.github.dokar3:quickjs-kt:1.0.18` | 复用已有构件，实现 Venera 的普通脚本入口、同步和异步宿主桥接、Promise 和二进制数据转换。先以原版脚本和回调实测语义，再核对原生绑定的资源限制与释放行为。 [quickjs-kt](https://github.com/dokar3/quickjs-kt) · [Venera-Next JS API](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/api/js.zh.md) |
| 日志与测试 | Kermit `2.0.3`、`kotlin.test`、`kotlinx-coroutines-test`、Turbine、Ktor MockEngine、Compose UI Test | 覆盖状态序列、源请求契约、UI 操作和失败恢复。截图基线用固定字体、主题、窗口和测试数据。 [Kermit](https://kermit.touchlab.co/docs/) · [Turbine](https://github.com/cashapp/turbine) · [Ktor MockEngine](https://ktor.io/docs/client-testing.html) · [Compose UI Test](https://kotlinlang.org/docs/multiplatform/compose-test.html) |

## 原生能力与发布验证

| 事项 | 实现决策和验收 |
| --- | --- |
| QuickJS 打包和执行限制 | 已核对 `1.0.18` 的 Maven 变体、JVM JAR 以及公共源码。Android、iOS 真机与模拟器均有对应发布变体，JVM JAR 含 Windows x64、macOS arm64 与 Linux x64 所需原生产物。已确认绑定暴露内存、栈、超时和手动中断接口。每个目标通过安装包启动并实际跑 Promise、取消、超时和二进制传输。 [发布元数据](https://repo.maven.apache.org/maven2/io/github/dokar3/quickjs-kt/1.0.18/quickjs-kt-1.0.18.module) · [JVM 构件](https://repo.maven.apache.org/maven2/io/github/dokar3/quickjs-kt-jvm/1.0.18/) · [公共源码发布物](https://repo.maven.apache.org/maven2/io/github/dokar3/quickjs-kt/1.0.18/) |
| 数据库、备份加密和格式 | 普通 SQLite 先走官方稳定驱动，`SqlDriverFactory` 独立于 schema。数据库加密采用 SQLCipher 路线，备份加密使用独立的版本化加密容器。SQLCipher 的移动端和桌面 JVM 驱动集成、启停加密的数据迁移、密钥生命周期及本地归档的格式/编码兼容需实测后才能宣称功能对齐。 [SQLCipher](https://www.zetetic.net/sqlcipher/) |
| 后台任务与文件授权 | Android 用 WorkManager 和 SAF。iOS 用 BackgroundTasks 及文档访问授权，后台触发时间由系统调度，启动或回前台时补跑到期任务。桌面先用进程内调度和持久任务恢复，各系统的应用退出后调度按能力交付。单次文件选择与重启后的授权访问分别验证。 [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent) · [SAF](https://developer.android.com/training/data-storage/shared/documents-files) · [Apple BackgroundTasks](https://developer.apple.com/documentation/backgroundtasks) |
| 网页登录 | 沿用 Venera-Next 的平台 WebView 路线。Android 用系统 WebView，iOS / macOS 用 WKWebView，Windows 用 WebView2，Linux 用系统 WebKitGTK 并可用独立原生窗口。登录完成后把当前站点 Cookie 和 `localStorage` 交回 Venera 源回调。Windows 缺 Runtime 时给出安装提示，Linux 包声明系统库依赖。 [Venera 登录实现](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/features/comic_source/comic_source_page.dart#L1314) · [Venera 桌面后端](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/packages/desktop_webview_window/README.md) · [WKWebView](https://developer.apple.com/documentation/webkit/wkwebview) · [WebView2 Runtime](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/distribution) · [WebKitGTK](https://webkitgtk.org/reference/webkit2gtk/stable/) |
| 社区发布 | 应用以社区 Release 安装包分发。扩展作者发布 Venera 格式的原始 `.js` 和 JSON 源仓库索引，用户在应用中添加仓库或导入脚本，更新沿用同一仓库和更新机制。 [Venera 源发布格式](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/api/comic_source.zh.md#7-发布脚本与源仓库) |

每个发布平台分别验证书架读写、JS 扩展安装与回滚、在线漫画、离线重启续读、漫画本地文件导入与实际安装包启动。平台差异和结果记入 [Komikku 对齐计划](komikku-alignment.md) 的验收记录。
