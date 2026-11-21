# 阶段 1 · 书架与分类

[阶段索引](README.md) · [目标架构](../application-architecture-mvvm-clean-udf.md) · [验收基线](../verification.md)

## 起点与结果

书架已有 `LibraryViewModel`，仍通过 `LibrarySession` 读写展示侧 `LibraryRepository`。分类管理从「更多」经现有路由进入。生产适配器组合领域书架契约、历史和下载数据，并转换成书架展示快照。本阶段先贯通分类读写与刷新，再将书架的主要命令和状态迁到 ViewModel。书架表现、分类入口、作品身份和数据库兼容性沿用现状。 [生产适配器](../../shared/app/src/commonMain/kotlin/library/ProductionLibraryRepository.kt) · [分类页面](../../shared/features/src/commonMain/kotlin/categories/CategoriesScreen.kt)

主要技能为 [架构重构](../../.agents/skills/kmp-architecture-refactor/SKILL.md)。改契约和事务时补 [数据边界](../../.agents/skills/kmp-data-boundaries/SKILL.md)，迁 UI 时补 [Compose 边界](../../.agents/skills/compose-ui-boundaries/SKILL.md)。

## 实施顺序

1. **核对分类的数据链路。** 从「更多 → 分类管理」和书架分类分配追踪到 `LibraryStore`。列出创建、改名、排序、隐藏、删除和分配现在的提交与刷新点，确认书架筛选如何处理已隐藏或已删除分类。以领域层 `LibraryRepository` 和现有查询为起点，契约只补实际缺口。
2. **迁分类读写。** 分类管理移到 `features/library/categories`，路由仍在 `app/navigation`。为分类页绑定 ViewModel，书架和分类页以同一个会话仓储读取提交结果。重用现有 SQLDelight 事务与排序规则。变更成功时从已提交数据刷新两个调用方，失败时维持各页既有提示及可见状态。核对一页被压在导航栈下方时能够在返回后看到结果。
3. **收拢收藏与批量命令。** 按收藏与撤销、批量归类、已读标记、继续阅读查询分逻辑组迁移。可共享的校验进入领域侧，事务与查询留在数据侧，布局、未读文案、筛选和选择映射属于展示状态。保持现有批量写入的原子范围、失败与撤销行为。保留 `(sourceKey, kind, workId)` 及章节定位语义。
4. **退役书架 Session 展示状态。** 将 `LibrarySession` 中迁移完成的读写逐项交给 `LibraryViewModel`。核对 `LibraryTab`、详情、分类、设置或测试中的剩余调用，生产入口改完再删旧状态与展示侧仓储方法。封面和继续阅读保留既有来源与失败回退。需要合并加载或错误布局时复用设计系统组件。

## 回归与交付

| 路径 | 测试与观察 |
| --- | --- |
| 分类读写、排序、隐藏、删除 | `LibraryStoreTest`、`CategoriesUiTest`。确认操作提交及返回书架后的可见分类和筛选。 |
| 收藏、撤销、多选、筛选 | `LibraryViewModelTest`、`LibraryScreenTest`、`VeneraLibraryRegressionTest`。核对失败提示和原交互。 |
| 旧收藏、读档与重启 | `VeneraLibraryProductionRestartTest`、`VeneraLibraryHistoryPersistenceTest`。保持旧库可读与续读可用。 |

按改动运行 `:shared:domain:jvmTest`、`:shared:data:jvmTest`、`:shared:features:jvmTest` 和 `:shared:app:jvmTest`，交付前按验收基线运行全量门。预留独立提交给分类链路、各组书架命令和旧容器清理。阶段结束时，分类页与书架页面状态各自独立，业务数据来源相同。
