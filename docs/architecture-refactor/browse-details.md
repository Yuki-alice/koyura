# 阶段 3 · Browse 与详情

[阶段索引](README.md) · [目标架构](../application-architecture-mvvm-clean-udf.md) · [验收基线](../verification.md)

## 起点与结果

Browse、全局搜索、源内搜索和详情仍有大量代码在 `app/browse`。`BrowseSession` 同时持有 pane、查询和扩展操作状态。本阶段把展示迁到 `features/browse` 与 `features/details`，由 `app` 保留目的地接线，源运行时、下载和书架分别拥有自己的业务状态。保持 Browse 四个 pane、共享详情路由及入口体验。 [BrowseSession](../../shared/app/src/commonMain/kotlin/browse/BrowseSession.kt) · [源浏览和详情页面](../../shared/app/src/commonMain/kotlin/browse/SourceScreens.kt)

主要技能为 [架构重构](../../.agents/skills/kmp-architecture-refactor/SKILL.md)。迁页面时补 [Compose 边界](../../.agents/skills/compose-ui-boundaries/SKILL.md)，调整扩展、图片与会话时补 [源兼容回归](../../.agents/skills/source-compatibility/SKILL.md)。

## 实施顺序

1. **迁图源列表与 Feed。** 将 Browse 外层、图源和 Feed 的内容 Composable、资源、预览和测试随功能迁入。外层保留 pane 的顺序、可用条件、选择及切换恢复。列表和 Feed 使用各自的 ViewModel，从现有来源契约读数据。样式按阶段 1 的复用方式处理。
2. **迁源内搜索与全局搜索。** 保留各自的路由、过滤条件和状态作用域。查询或选项变化时取消旧请求、重置分页，防止过期页覆盖新查询。分页继续遵循来源语义，区分页号、结束与不透明 next。全局搜索分源反馈和可重试路径保持现状。领域契约接收业务参数，JS 值转换留在适配层。
3. **迁扩展与仓库。** 将安装、更新、启停、卸载和仓库预览的展示状态迁至 MVI。操作由现有扩展服务执行，状态容器反映进行中、失败或已提交结果。保留来源并发隔离、旧实例恢复和进程重启后的记录。扩展列表、详情、仓库编辑共享业务结果，各自管理本页状态。
4. **分离详情与换源。** 将 `SourceDetailScreen` 与章节界面移到 `features/details`，详情 ViewModel 串接作品、书架和下载契约。复用阶段 1 的收藏与分类能力，以及阶段 2 的打开阅读器回调。换源候选、选择及进度归 `browse/migration` 的 Store。迁移和合并继续由书架规则及数据事务执行，保持收藏、分类、已读和进度语义。
5. **收窄剩余适配。** 每完成一个消费者就清理对应旧 Session 方法或展示侧接口。生产路由保持一致，检查回到父页后的选中 pane、查询、过滤状态和重新打开详情。

## 回归与交付

| 路径 | 测试与观察 |
| --- | --- |
| 搜索取消、分页与 UI | `BrowseSearchPagingTest`、`SourceSearchPaginationTest`、`GlobalSearchSessionTest`、`GlobalSearchUiTest`。核对快速改查询、旧请求晚到和同页重试。 |
| 扩展一致性 | `BrowseConvergenceTest`、`ReviewBrowseConcurrencyTest`、`ReviewExtensionConsistencyTest`、`ReviewExtensionCommitBoundaryTest`。覆盖并发操作与失败后的旧实例。 |
| 详情、换源与下载 | `AppShellDetailUiTest`、`DetailDownloadStateTest`、`LibraryMigrationTest`。走书架、历史、搜索到详情及阅读器的入口。 |

随页面提交迁资源和 UI 测试，业务边界变更独立提交。涉及 JS 运行时、会话桥和宿主回调时，按验收基线跑 Venera 原版脚本与适用社区源夹具，同时核对 SHA-256 和回调链。交付前跑全量 JVM 门。
