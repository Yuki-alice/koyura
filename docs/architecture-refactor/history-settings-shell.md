# 阶段 4 · 历史、设置与外壳

[阶段索引](README.md) · [目标架构](../application-architecture-mvvm-clean-udf.md) · [验收基线](../verification.md)

## 起点与结果

历史页和会话仍位于 `app/history`，设置、统计、账号等已有各自展示入口，外壳仍持有多种页面 Session 和业务装配。本阶段收敛到目标目录，核对根导航、共享子页、独立阅读器以及持久设置。 [历史会话](../../shared/app/src/commonMain/kotlin/history/HistorySession.kt) · [应用外壳](../../shared/app/src/commonMain/kotlin/AppScaffold.kt) · [Koin 装配](../../shared/app/src/commonMain/kotlin/di/AppKoin.kt)

主要技能为 [架构重构](../../.agents/skills/kmp-architecture-refactor/SKILL.md)。改用例和持久偏好时补 [数据边界](../../.agents/skills/kmp-data-boundaries/SKILL.md)，最终 diff 和契约核对用 [架构评审](../../.agents/skills/kmp-architecture-review/SKILL.md)。

## 实施顺序

1. **历史迁入展示层。** 将 `HistoryScreen`、ViewModel、展示模型及资源迁到 `features/history`。搜索、日期分组、多选和失败状态由 ViewModel 管理，继续阅读、删除与收藏从现有业务契约取得结果。核对隐藏历史和已存续读位置各自的语义。打开详情或阅读器使用既有路由回调。
2. **设置、统计与账号按归属收敛。** 设置 ViewModel 使用既有偏好契约，保留即时主题、本地化、全局和单作品阅读偏好。统计读业务数据并映射展示，账号按原有会话边界接线。更多和首启沿用原入口及完成标记。Multiplatform Settings、Kermit 和 Turbine 依技术栈在收尾窗口单独接入，先验证旧数据和默认值再改变生产实现。
3. **缩小外壳与装配。** 整理 `app/shell`、`app/navigation`、`app/di`。各目的地接入其页面入口，外壳保有 Tab、返回、全局叠层和生产装配。收拢 Koin 的会话绑定，让状态容器按导航条目或明确的宿主生命周期释放。逐个核对 `Local*` 与 Session 的剩余调用，保留主题和平台宿主所需的局部能力。
4. **核对平台例外与目录。** 按目标树检查各模块、资源和测试任务归属。Android 网页登录适配如纳入本轮，独立接入 `shared/app` 的平台代码，先验证登录捕获及按源 Cookie 会话交接，再移除 Android 入口对 `shared/data` 的依赖。核对 Android、桌面、iOS 与 `ReaderShared` 打包，并把已落地的结构同步到[现行架构](../architecture.md)。

## 回归与交付

| 路径 | 测试与观察 |
| --- | --- |
| 历史、续读与旧记录 | `HistorySessionTest` 对照旧语义，生产切到新状态容器后验证 `HistoryActionsUiTest`、`HistoryAtomicityTest`、`VeneraHistoryFlowTest` 和重启。 |
| 偏好与主题 | `ProductionSettingsRepositoryTest`、`ReaderPreferencesStoreTest`。验证旧值、默认值、主题和阅读器覆盖的结果。 |
| 外壳导航与恢复 | `AppNavigationStateTest`、`RouteRegistryTest`、`AppShellStateRetentionTest`。验证五个 Tab 重复点击、返回优先级、首启与设置宽窄屏、打开阅读器再返回原入口。 |
| 平台入口 | 运行验收文档的三端构建。在实际触及网页登录或会话交接时加载源兼容技能并执行相关原版脚本回归。 |

按历史、设置和外壳各自提交。全量 JVM 命令须包含阶段 2 新增的阅读器任务。最终确认目标树、技术栈与验收文档和实际 Gradle 配置一致，保留测试覆盖和用户操作语义。
