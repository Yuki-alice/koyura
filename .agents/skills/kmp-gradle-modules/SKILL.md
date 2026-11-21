---
name: kmp-gradle-modules
description: 修改项目的 Gradle 模块、AGP 9 Kotlin Multiplatform 插件、source set、Compose 资源或 iOS framework 接线时使用。重点用于拆分漫画阅读器模块及三端构建验证。
---

# KMP 模块与构建

- 使用仓库 Gradle Wrapper 和现行 JDK 21。先对照 `settings.gradle.kts`、`gradle/libs.versions.toml` 和相邻共享模块的构建文件。沿用项目支持的 Android、JVM 与 iOS 目标和现行插件配置。
- 模块拆分前列出实际调用方、公开类型、跨模块引用与 source set。优先收窄可见性和消除反向依赖，先让生产调用方经公开入口接通。
- 拆出 `:shared:reader` 时一起迁移平台实现、资源、单测和预览。由 `:shared:app` 聚合，iOS 仍链接其 `ReaderShared`。移动测试时同时更新验收命令里的任务路径和覆盖范围。
- 先确认受影响的模块能编译和运行 JVM 回归，然后按[验收基线](../../../docs/verification.md)执行全量门。涉及模块、入口或平台接线时，核对 Android、桌面和 iOS framework。记录真实执行、缓存复用及平台缺失造成的未验证项。
