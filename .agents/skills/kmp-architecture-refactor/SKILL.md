---
name: kmp-architecture-refactor
description: 按现有 Kotlin Multiplatform 项目迁移功能目录、分层、导航入口、Koin 装配与 MVVM + UDF 或 MVI 状态容器时使用。适合规划和实施保留现有行为的业务链路重构。
---

# KMP 架构重构

1. 读[架构目标](../../../docs/application-architecture-mvvm-clean-udf.md)和当前任务对应的[阶段规划](../../../docs/architecture-refactor/README.md)。用实际生产入口和 Gradle 依赖区分已经落地的代码与计划中的代码。
2. 沿页面事件、状态容器、领域契约、生产实现和持久状态追踪一条完整链路。明确业务规则、展示数据、会话与跨进程任务各由谁持有。先迁这条链路，再迁它的同类调用方。
3. 直接复用已有契约和测试。业务计算放在领域侧，数据实现返回业务类型，页面进行展示映射，应用外壳只负责装配和导航。跨功能调用经业务契约或导航回调。调用方切换后再删除临时适配和旧状态入口。
4. 普通页面用 ViewModel 输出只读 StateFlow，复杂状态转移沿用 MVIKotlin Store。页面渲染以一份状态为准。释放页面时取消页面工作，持久写入和后台任务交由其业务作用域。若改变 Holder 作用域，先追踪未完成的读写和返回时的状态交接。
5. 保留当前 Navigation 3 栈、路由数据和交互。跨模块引用以窄入口和显式注入为先。拆阅读器遵循阶段 2 的顺序。技术选型以[技术栈](../../../docs/technology-stack.md)为准。
