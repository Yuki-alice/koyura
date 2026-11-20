# 引擎轨任务书：第五轮（GPT 6.1 / Codex）

你负责 Koyura 漫画阅读器的核心引擎。前四轮已交付并合入 main：宿主能力全套、按源会话与三平台凭据、网页登录、下载队列（单章 + 作品级批量 + 自动触发）、更新扫描（智能跳过 + 错误列表 + 后台调度）、搜索选项与保存搜索。主 agent 已把搜索选项通道、自动下载钩子与调度器接进装配。你的产出由主 agent 审阅后合并，main 只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/ui-parity`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/core-engine` 工作，分支 `feat/core-engine`（已含第四轮合并与主 agent 桥接 `967f629`）。
- 开场先读：`AGENTS.md`、`docs/verification.md`、`docs/technology-stack.md`。
- JDK 21 在 `~/devtools/jdk-21`；测试必带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`；社区脚本回归另带 `-PveneraCommunityFixtureDir`（夹具下载步骤见 verification.md，现有六脚本清单在第四轮报告）。

## 地盘

可以改：`shared/source-runtime`、`shared/network`、`shared/data`、`shared/domain`、`gradle/libs.versions.toml`（仅加依赖）。
禁止改：`shared/app` 任何目录、`apps/`、`shared/design-system`、`shared/features`、`reference/`、`docs/`（交付说明除外）。

## 任务一：备份与恢复的数据层（先做，本轮头牌）

对照 `docs/architecture.md` 的设计：「备份用自有版本化清单和数据容器，书架、分类、阅读进度、Feed、保存搜索和设置按选项导出，凭据继续由 `SecretStore` 管理」。要求：

- **版本化容器格式**：自有格式（清单 JSON + 数据容器，版本字段 + 导出时间 + 生成者标识），不兼容任何上游格式；为未来 WebDAV 同步留条件协商位。加密容器（Cryptography-Kotlin）**不在本轮**，登记后续。
- **导出**：按选项（书架条目与分类、阅读进度、更新检查元数据、下载队列状态、保存搜索、各偏好通道）选择导出；凭据与源会话**不进**备份（设计红线）。文件写出走 Okio 原子提交模式（临时文件 + 原子移动）。
- **恢复**：读入校验（版本兼容、清单完整性）后按选项恢复；合并语义明确——同三元键作品「保留较新收藏」、进度取较新写入、分类按名字并合并；冲突不静默丢弃，恢复报告逐项列出（新增/合并/跳过）。
- 服务层：`shared/domain` 模型 + `shared/data` 实现（驱动与文件系统的既有注入模式），app 装配与 UI 入口由主 agent 后续接线。
- 验收：夹具测试覆盖全量与部分导出、跨库恢复合并、坏容器拒绝、版本不匹配拒绝、恢复幂等（同备份恢复两遍结果一致）、进度取较新。真 SQLite 两库对拷回归至少一例。

## 任务二：Feed 数据服务

浏览页的 Feed（Komikku BrowseTab 的源 Feed 卡片）缺数据层：探索定义已有（`SourceBrowseService` 的 explore），补：

- Feed 条目的持久化与排序偏好（`library_prefs` 通道）：按源的 explore 子项启停与顺序。
- `feedItems()` 一次性查询返回浏览 Feed 页需要的全部数据形状（供 UI 后续消费），并为任务一的备份范围接入 Feed 偏好。
- 验收：启停/排序持久化、声明变更（源升级后 explore 项增减）的收敛语义、重启读取。

## 任务三：传输层公网兼容复测

第四轮两处公网阻断（ComicWalker 公网搜索返回 `<` 开头内容、MangaDex 公网 302 挑战 + HTML）。先诊断再动：

- 对照 `reference/Venera-Next` 的网络栈（`lib/network/` 的 Dio 配置、User-Agent、重定向与挑战处理）列出差异，判断是 UA/头/重定向语义的兼容缺口还是站点风控。
- 若为传输层可修（UA、跳转跟随、头透传），在 `shared/network`/`VeneraKtorTransport` 修并公网复测走通；站点风控不可修的如实登记、固定夹具保持覆盖。
- 验收：修复附最小断言测试（模拟 302/UA 差异的夹具），两源公网复测结果与状态码如实记录。

## 弹性任务四：全局搜索的真通道适配核对（时间不足可跳过）

主 agent 桥接后 `searchWorks` 应用选项默认值：核对 `GlobalSearchService` 扇出路径的行为（应沿用同一通道、无需逐源选项），如语义有偏差给出最小修正；无偏差则登记核对结论即可。

## 硬性验收

- 五模块 JVM 测试全绿，**交付前连续两遍**。
- 触碰宿主/会话/网络：Venera 原版脚本回归；改网络层另跑社区脚本回归（六源全量）。
- 三端构建全部 EXIT 0；漫画柜/Komga 至少各跑一遍全链路。

## 提交规范

`type(scope): 中文主题`（scope 举例：data、backup、feed、network、search）。主题如实描述；禁止无信息主题与阶段编号；一提交一个内聚逻辑。

## 交付说明

覆盖写 `docs/tasks/engine-report-latest.md`：改动清单、测试证据（命令 + 结果 + 新增测试类与覆盖场景、公网复测状态码）、备份格式规范草案（字段与版本策略，供主 agent 评审后固化进 docs）、需主 agent 桥接/装配的路径与下一轮计划。
