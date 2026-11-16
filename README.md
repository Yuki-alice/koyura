# Koyura

使用 Kotlin Multiplatform 与 Compose Multiplatform 构建跨平台的 Komikku 漫画阅读体验。当前优先对齐 Komikku 的界面、交互和漫画阅读器，JS 源以 Venera-Next 兼容为目标。 [当前对齐计划](docs/komikku-alignment.md) · [技术栈选型](docs/technology-stack.md) · [项目架构与目录](docs/architecture.md)

## 本地运行

使用 JDK 21，通过仓库内的 Gradle Wrapper 构建。Android 需要 Android SDK Platform `37.1`。iOS 需要 macOS 和带 iOS 模拟器运行时的 Xcode。

```bash
# 桌面
./gradlew :apps:desktop:run
./gradlew :apps:desktop:createDistributable

# Android 调试包与安装
./gradlew :apps:android:assembleDebug
./gradlew :apps:android:installDebug

# iOS 模拟器调试构建
xcodebuild -project apps/ios/Reader.xcodeproj -scheme Reader \
  -destination 'generic/platform=iOS Simulator' \
  -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

Android 构建从 `ANDROID_HOME` 或 `local.properties` 解析 SDK 路径。iOS 也可以在 Xcode 中打开 `apps/ios/Reader.xcodeproj`，选中模拟器后运行 `Reader` scheme。若 Xcode 的脚本环境需要指定 JDK 路径，可把 `KMP_JAVA_HOME` 设置为本机 JDK 21 根目录。

## 当前工程

| 模块 | 职责 | 状态 |
| --- | --- | --- |
| `:apps:android` | Android 应用入口 | 已接入 |
| `apps/ios` | Xcode 应用入口，构建时链接 `ReaderShared` | 已接入 |
| `:apps:desktop` | Compose Desktop JVM 入口和安装包配置 | 已接入 |
| `:shared:app` | 共享 Compose 入口、导航、装配和应用资源 | 已接入 |
| `:shared:design-system` | 共享 Material 3 主题 | 已接入 |
| `:shared:domain` | 作品、章节、用例和仓储/平台接口 | 已接入 |
| `:shared:data` | Venera 仓库、扩展持久化和三端会话 | 已接入 |
| `:shared:network` | Ktor 传输、HTTP 策略和按源会话 | 已接入 |
| `:shared:source-runtime` | QuickJS 宿主、Venera 源注册和回调桥接 | 已接入 |
| `:shared:features` | 漫画阅读器页面与共享测试替身（其余页面规划中） | 部分接入 |
| `:shared:media` | 本地小说解析与阅读位置 | 独立构建，暂未接入 |

Android、桌面和 iOS 从同一共享入口打开扩展会话并恢复已安装源。在「浏览 → 扩展仓库」添加 Venera 仓库索引，预览并安装条目，再从「来源」进入搜索、作品详情和章节阅读。应用重启后可从「历史」续读。`reference/` 放本地参考仓库，开发任务与验收按[当前对齐计划](docs/komikku-alignment.md)执行。

## Venera 脚本验证

运行以下命令，用本机 Venera-Next 参考仓库提供原始 `doc/examples/minimal_source.js`。默认读取 `reference/Venera-Next`，在独立工作树中可用 `-PveneraReferenceDir` 指定路径。测试核对脚本 SHA-256，通过注入的 HTTP 响应运行搜索、详情、章节、正文图片、缩略图和标签回调。

```bash
./gradlew :shared:source-runtime:jvmTest \
  -PveneraReferenceDir="/path/to/Venera-Next"

./gradlew :shared:source-runtime:iosSimulatorArm64Test \
  :shared:source-runtime:compileKotlinIosArm64 \
  :shared:source-runtime:assembleAndroidMain
```
