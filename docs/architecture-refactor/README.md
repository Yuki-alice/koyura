# 应用架构重构实施规划

以[目标架构](../application-architecture-mvvm-clean-udf.md)、[技术栈](../technology-stack.md)和[验收基线](../verification.md)为准。各文档描述可独立交付的改动顺序。执行时先从生产入口核对当前分支进度，复用已经落地的实现和测试。

| 阶段 | 实施文档 | 应交付的行为 |
| --- | --- | --- |
| 1 | [书架与分类](library.md) | 书架与分类管理共享业务契约和已提交数据，书架 ViewModel 收拢展示状态。 |
| 2 | [阅读器、下载与更新](reader-downloads-updates.md) | 阅读器接口、封面解码和退出保存先收敛，再拆出 `:shared:reader`。下载与更新保留各自的任务生命周期。 |
| 3 | [Browse 与详情](browse-details.md) | 图源、搜索、Feed、扩展、换源与详情迁入对应功能包，保留路由和源协议。 |
| 4 | [历史、设置与外壳](history-settings-shell.md) | 页面状态、设置及全局装配按归属收敛，完成目标目录树和三端接线核对。 |

## 技能库审阅与取舍

审阅了本机 `~/skills` 的集合索引及相关入口。目录中可见的 10 个集合共 167 个 `SKILL.md`，含集合路由。逐项阅读与本轮重构直接相关的架构、Compose、数据、工具链、测试、安全与评审技能。仓库中采用独立的中文工作流，按项目已定技术栈适配。

| 工作区技能 | 参考入口与采用内容 |
| --- | --- |
| [kmp-architecture-refactor](../../.agents/skills/kmp-architecture-refactor/SKILL.md) | `kmp-clean-architecture`、`kotlin-kmp-refactor-safety`、`kotlin-project-state-management`。采用职责与编译依赖审查、行为保持和状态所有权。模块采用本项目目标树。 |
| [compose-ui-boundaries](../../.agents/skills/compose-ui-boundaries/SKILL.md) | `compose-expert`、`compose-component-design`、`compose-state-and-effects`。采用状态提升、插槽和生命周期指导。性能集合的成员按具体卡顿或重组问题才有用。 |
| [kmp-data-boundaries](../../.agents/skills/kmp-data-boundaries/SKILL.md) | `kotlin-data-kmp-data-layer`。采用数据源和事务归属、主线程安全、错误与数据适配原则。 |
| [kmp-gradle-modules](../../.agents/skills/kmp-gradle-modules/SKILL.md) | `kotlin-build-kmp-gradle-governance`、`kotlin-tooling-agp9-migration`。采用 source set、依赖和三端验证检查，版本以本项目目录为准。 |
| [source-compatibility](../../.agents/skills/source-compatibility/SKILL.md) | `android-review/security-audit`。采用会话隔离、敏感信息和网页登录边界检查，回归命令与夹具来源于本项目。 |
| [kmp-refactor-verification](../../.agents/skills/kmp-refactor-verification/SKILL.md) | `kotlin-testing-kmp`、`compose-ui-testing-patterns`。采用按行为安排测试层次、验证源集覆盖和稳定复现。 |
| [kmp-architecture-review](../../.agents/skills/kmp-architecture-review/SKILL.md) | `kmp-audit`、`mattpocock-skills/code-review`。采用需求和工程规范两个视角，使用实际 diff、代码、测试和任务说明。 |

单独的 Material 3 / MD3E 风格技能适用于视觉设计任务，当前重构沿用既有视觉。发布、外部工具、跨项目迁移及全量性能脚本留在个人技能库。工作区条目采用通用 YAML + Markdown，引用仓库内文件。支持标准目录发现的 agent 可以自动找到，其他 agent 通过 [AGENTS.md](../../AGENTS.md) 读取指定入口。 [Agent Skills 格式](https://agentskills.io/specification)
