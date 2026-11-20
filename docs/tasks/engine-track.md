# 引擎轨任务书：第五轮（GPT 6.1 / Codex）

你负责 Koyura 漫画阅读器的核心引擎。前四轮已交付并合入 main：宿主能力全套、按源会话与三平台凭据、网页登录、下载队列（单章 + 作品级批量 + 自动触发）、更新扫描（智能跳过 + 错误列表 + 后台调度）、搜索选项与保存搜索。主 agent 已把搜索选项通道、自动下载钩子与调度器接进装配。**备份与恢复经裁决延后**，本轮转做浏览发现域的数据补全。你的产出由主 agent 审阅后合并，main 只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/ui-parity`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/core-engine` 工作，分支 `feat/core-engine`（已含第四轮合并与主 agent 桥接）。
- 开场先读：`AGENTS.md`、`docs/verification.md`、`docs/technology-stack.md`。
- JDK 21 在 `~/devtools/jdk-21`；测试必带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`；社区脚本回归另带 `-PveneraCommunityFixtureDir`（夹具清单六脚本与下载步骤见 verification.md）。

## 地盘

可以改：`shared/source-runtime`、`shared/network`、`shared/data`、`shared/domain`、`gradle/libs.versions.toml`（仅加依赖）。
禁止改：`shared/app` 任何目录、`apps/`、`shared/design-system`、`shared/features`、`reference/`、`docs/`（交付说明除外）。

## 任务一：Feed 数据服务（先做，本轮头牌——浏览 Feed 页的数据层）

浏览一级页的 Feed 卡片（对照 `reference/komikku` 的 BrowseTab Feed 实现：`app/src/main/java/eu/kanade/tachiyomi/ui/browse/feed/` 一带与 `presentation/browse/`）目前缺数据层；探索定义已由 `SourceBrowseService` 提供。要求：

- Feed 条目的持久化与排序偏好（`library_prefs` 通道）：按源的 explore 子项启停与顺序，重启可读。
- `feedItems()` 一次性查询返回 Feed 页所需的完整数据形状（启用的源子项、标题、类型、编号/游标形态），供主 agent 后续接浏览 UI。
- 声明变更收敛：源升级后 explore 项增减时，用户已有的启停与顺序按剩余项保留、新项默认启用追加（语义对照 Komikku 的 feed 偏好处理；有出入的对照原版并登记）。
- 验收：启停/排序持久化、声明增减收敛、重启读取、多源隔离。

## 任务二：源固定、自定义分类与 NSFW 过滤的数据层

「内容发现与搜索」域的引擎侧残余（对照 13 域差距清单）：

- **源固定（pin）与自定义分类**：对照 Komikku 的源列表语义（`presentation/browse/source/` 一带：固定源、源分类/筛选、隐藏禁用源），落数据层——源固定状态、用户自定义源分组、默认源设定；对照 Venera 源声明（`reference/Venera-Next/doc/api/`）确认有无源级分类可映射，无则纯用户自定义。持久化走既有通道，重启可读。
- **NSFW 过滤**：全局开关 + 每源 NSFW 标记。先对照 Venera 协议确认源声明是否携带 NSFW 字段（对照原版 Dart 解析器与文档）；有则读声明，无则提供用户手动标记。过滤语义对照 Komikku（全局关/开/仅生物钟之类开关简化为开关 + 每源覆盖），作用于来源列表与全局搜索的源集合。
- 验收：固定/分组/NSFW 的持久化与过滤查询、重启读取、与全局搜索源集合（主 agent 桥接的 `GlobalSearchService` 扇出前过滤）的衔接接口在交付说明写清，供主 agent 合并时接线。

## 任务三：传输层公网兼容复测

第四轮两处公网阻断（ComicWalker 公网搜索返回 `<` 开头内容、MangaDex 公网 302 挑战 + HTML）。先诊断再动：

- 对照 `reference/Venera-Next/lib/network/` 的网络栈（Dio 配置、User-Agent、重定向与挑战处理）列出差异，判断是 UA/头/重定向语义的兼容缺口还是站点风控。
- 传输层可修的（UA、跳转跟随、头透传）在 `shared/network`/`VeneraKtorTransport` 修并公网复测走通；站点风控不可修的如实登记、固定夹具保持覆盖。
- 验收：修复附最小断言测试（模拟 302/UA 差异夹具），两源公网复测结果与状态码如实记录。

## 弹性任务四：全局搜索真通道适配核对（时间不足可跳过）

主 agent 桥接后 `searchWorks` 应用选项默认值：核对 `GlobalSearchService` 扇出路径的行为（应沿用同一通道、无需逐源选项），如语义有偏差给出最小修正；无偏差则登记核对结论即可。若任务二的 NSFW 过滤已落，可顺带给出扇出前过滤的接入点说明。

## 硬性验收

- 五模块 JVM 测试全绿，**交付前连续两遍**。
- 触碰宿主/会话/网络：Venera 原版脚本回归；改网络层另跑社区脚本回归（六源全量）。
- 三端构建全部 EXIT 0；漫画柜/Komga 至少各跑一遍全链路。

## 提交规范

`type(scope): 中文主题`（scope 举例：data、feed、browse、network、search）。主题如实描述；禁止无信息主题与阶段编号；一提交一个内聚逻辑。

## 交付说明

覆盖写 `docs/tasks/engine-report-latest.md`：改动清单、测试证据（命令 + 结果 + 新增测试类与覆盖场景、公网复测状态码）、需主 agent 桥接/装配的路径（Feed 页 UI 接线、NSFW 扇出过滤接线、pin/分组 UI 接线）、下一轮计划。

下一轮预告（主 agent 排期，不在本轮）：备份与恢复数据层（已裁决延后）、追踪服务第一批（AniList）、更新调度的平台接线（WorkManager/前台事件）。
