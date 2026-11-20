# 引擎轨任务书：第六轮（GPT 6.1 / Codex）

你负责 Koyura 漫画阅读器的核心引擎。前五轮已交付并合入 main：宿主能力全套、按源会话与三平台凭据、网页登录、下载（单章 + 作品级批量 + 自动触发）、更新扫描（智能跳过 + 后台调度）、搜索选项与保存搜索、Feed 数据服务、来源偏好（固定/隐藏/分组/默认/NSFW）。主 agent 已把保存搜索复用通道、新章自动下载钩子、调度器、全局搜索过滤接进装配。你的产出由主 agent 审阅后合并，main 只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/ui-parity`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/core-engine` 工作，分支 `feat/core-engine`（已含第五轮合并与主 agent 桥接 `88c6dfe`）。
- 开场先读：`AGENTS.md`、`docs/verification.md`、`docs/technology-stack.md`。
- JDK 21 在 `~/devtools/jdk-21`；测试必带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`；社区脚本回归另带 `-PveneraCommunityFixtureDir`（清单与下载步骤见 verification.md，六脚本）。

## 地盘

可以改：`shared/source-runtime`、`shared/network`、`shared/data`、`shared/domain`、`gradle/libs.versions.toml`（仅加依赖）。
禁止改：`shared/app` 任何目录、`apps/`、`shared/design-system`、`shared/features`、`reference/`、`docs/`（交付说明除外）。

## 任务一：备份与恢复数据层（本轮头牌，从延后队列取出）

对照 `docs/architecture.md` 的设计：

- **版本化自有容器**：清单 JSON + 数据容器，版本字段 + 导出时间 + 生成者标识；为未来 WebDAV 同步留条件协商位。**加密容器（Cryptography-Kotlin）不在本轮**，登记后续。
- **导出内容**：书架条目与分类、阅读进度、更新检查元数据、下载队列状态、保存搜索、各偏好通道（更新策略、下载触发、Feed、来源偏好、阅读设置全局值）。**凭据与源会话不进备份**（设计红线），作用域记录到恢复报告。
- **恢复**：版本校验 + 清单完整性校验后按选项恢复；合并语义——同三元键作品「保留较新收藏」、进度取较新写入、分类按名字合并；**冲突不静默丢弃**，逐项列入恢复报告（新增/合并/跳过）。
- 模型进 `shared/domain`，实现进 `shared/data`（复用既有驱动与文件注入模式，走 Okio 原子文件提交）；app 装配与 UI 入口由主 agent 后续接线。
- 验收：夹具覆盖全量与部分导出、跨库恢复合并、坏容器拒绝、版本不匹配拒绝、恢复幂等（同备份恢复两遍结果一致）。真 SQLite 两库对拷回归至少一例。

## 任务二：追踪服务第一批：AniList（架构集成）

对照 Komikku 的追踪适配结构与 AniList GraphQL 接口：

- OAuth 授权流：授权码交换 → token 持久化（经既有 `SecretStore` 路线，不进备份）；refresh 逻辑；登录态查询与注销。
- 作品绑定：本地作品 ↔ 远端条目的 binding 持久化（新表，新建的备份范围需包含它——与任务一衔接）。
- 双向进度：本地读章 ↔ 远端进度的上报（阅读器 `savePage` 触发点留给主 agent 接线，此轮做到服务层方法）与远端拉取合并（本地较新优先，冲突报告）。
- OAuth 回调与真实网络的在线验收用官方沙盒/个人令牌在报告里写清（凭据不入库）；离线夹具用 MockEngine 覆盖授权/刷新/上报/合并全链。
- 本轮只做 AniList；MAL/Kitsu 等登记后续。UI（搜索绑定对话框、设置入口）不做，主 agent 后续派发视觉轨。

## 弹性任务三：传输层的长期回归固化（时间不足可跳过）

把第四轮对 Venera 网络栈的诊断结论（UA/302/头/跳转语义）固化为 `VeneraKtorTransportTest` 的永久夹具：UA 默认与显式、302 跟踪链、头透传大小写、挑战页内容类型识别；若此前结论有变化，如实修正报告。

## 硬性验收

- 五模块 JVM 测试全绿，**交付前连续两遍**。
- 触碰宿主/会话/网络：Venera 原版脚本回归 + 六源社区脚本回归；三端构建全部 EXIT 0。
- 漫画柜/Komga 至少各跑一遍全链路（公网状态码如实记录）。

## 提交规范

`type(scope): 中文主题`（scope 举例：data、backup、tracking、network）。主题如实描述；禁止无信息主题与阶段编号；一提交一个内聚逻辑。

## 交付说明

覆盖写 `docs/tasks/engine-report-latest.md`：改动清单、测试证据（命令 + 结果 + 新增测试类与覆盖场景、公网复测状态码）、**备份格式规范草案**（字段与版本策略，供主 agent 固化进 docs）、需主 agent 桥接/装配的路径（备份 UI 入口、追踪设置入口与阅读器上报接线）与下一轮计划。
