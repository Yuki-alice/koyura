# 阶段 2 · 阅读器、下载与更新

[阶段索引](README.md) · [目标架构](../application-architecture-mvvm-clean-udf.md) · [验收基线](../verification.md)

## 起点与结果

阅读器、下载与更新已使用 MVI。阅读器 Holder 由应用外壳创建，退出发出异步 `SaveCurrent` 后立即返回。Browse 封面解码依赖阅读器包。完成本阶段后，`app` 只负责用现有路由调用 `:shared:reader`，阅读器状态随导航条目，下载与更新任务继续由会话或后台调度拥有。 [阅读器界面](../../shared/features/src/commonMain/kotlin/reader/comic/ReaderScreen.kt) · [封面接线](../../shared/app/src/commonMain/kotlin/browse/CoverImageLoaders.kt)

主要技能为 [架构重构](../../.agents/skills/kmp-architecture-refactor/SKILL.md)。拆模块时补 [Gradle 模块](../../.agents/skills/kmp-gradle-modules/SKILL.md)，涉及图片路径或宿主会话时补 [源兼容回归](../../.agents/skills/source-compatibility/SKILL.md)。

## 阅读器实施顺序

1. **收口调用。** 在现有模块内建立单一阅读器页面入口。书架、详情与历史交给 `app` 作品、章节及原有续读参数。保留 `ComicReaderRoute` 的序列化形态、全屏层级与退出动作。入口接收 `ReaderService`、`ReaderPreferences` 及所需平台回调，Holder、ReaderIntent、Viewer 和 Chrome 的内部接线从外壳迁出。
2. **解除封面解码依赖。** 以现有 `CoverDecoder` 接口提供封面解码实现，移除 Browse 对 `features.reader.comic.decodeToImageBitmap` 的编译引用。逐端核对同一封面图片、缓存命中、读取失败占位和阅读器页面解码。以原版源图片回调夹具检查按源会话语义。
3. **验证退出保存与作用域。** 从当前翻到一页后立即返回的路径入手，查 `SaveCurrent` 发起、持久提交与 Holder `dispose()` 的顺序。出栈前完成保存，或将最后一次有效进度快照移交会话级保存路径。移交包含稳定作品、章节、页键与页索引，避免切换章节后写入了另一条记录。阻断旧章的晚到结果覆盖新章。快速返回、快速切章及重新打开另一部作品都要验证，退出再重启后从实际持久记录读取最后一页，并覆盖无痕分支。
4. **拆出 `:shared:reader`。** 前三步仍在现有模块内完成。新增模块后，随代码迁移 Android、iOS、JVM 专用图像实现、Compose 资源、预览和阅读器测试。模块直接依赖 `domain` 与 `design-system`，`app` 通过现有导航聚合，iOS 继续消费 `ReaderShared`。缩小 Store 等内部符号可见性，逐项确认不存在封面或其他页面对内部符号的依赖。更新验收命令覆盖 `:shared:reader:jvmTest`。

## 下载与更新

- 核对队列页与详情的 `ChapterDownloads` 调用。复用既有契约，只补查询与操作的实际缺口。下载任务状态由 `ChapterDownloadStore` 和提交记录驱动，展示状态由 `DownloadsStore` 映射。退出队列后仍可执行，重新进入能看到状态。
- 将更新查询和展示转换放回各自层，保留 `UpdatesStore`。后台调度、更新错误及新增章节自动入队沿现有语义验证。下载重启恢复与更新入队各自提交，避免与阅读器模块移动混在同一提交。

## 回归与交付

| 路径 | 测试与观察 |
| --- | --- |
| 阅读进度、手势与页面 | `ReaderStoreTest`、`ReaderFlowTest`、`VeneraReaderRestartTest`、`VeneraReaderIncognitoTest`。补现有用例覆盖不到的出栈写入竞态，核对五种模式、切换保位、章节边界和离线续读。 |
| 调用边界与画面 | `AppShellUiTest`、`ReaderSettingsDialogTest`、现有阅读器视觉夹具。用同一主题和宽窄窗口验证原返回顺序、工具栏与输入反馈。 |
| 下载、更新与恢复 | `ChapterDownloadStoreTest`、`DownloadsStoreTest`、`UpdatesStoreTest`、`BackgroundLibraryUpdatesTest`。覆盖失败重试、重启恢复和新增章节触发。 |

拆前运行受影响的现有 JVM 任务，拆后确认阅读器和应用导航测试均由正常任务执行。按验收基线运行全量 JVM 门和 Android、桌面、iOS framework 构建。图片通路变更跑社区源夹具，涉及运行时、会话桥或宿主回调时同时核对 Venera 原版 SHA-256 与全回调链。
