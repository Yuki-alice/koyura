# 引擎轨任务书：第四轮（GPT 6.1 / Codex）

你负责 Koyura 漫画阅读器的核心引擎。前三轮已交付并合入 main：宿主能力全套、按源会话与三平台凭据、网页登录、下载队列与本地优先阅读、更新扫描引擎（智能跳过 + 偏好持久化 + 错误列表）、账号配置暴露、请求缓存与防并发。夜间轨已补齐前端（全局搜索、历史多选、智能更新设置 UI、阅读背景四档）。你的产出由主 agent 审阅后合并，main 只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/ui-parity`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/core-engine` 工作，分支 `feat/core-engine`（已含夜间轨合并与主 agent 桥接）。
- 开场先读：`AGENTS.md`、`docs/verification.md`、`docs/technology-stack.md`。
- JDK 21 在 `~/devtools/jdk-21`；测试必带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`；社区脚本回归另带 `-PveneraCommunityFixtureDir`（夹具下载步骤见 verification.md）。

## 地盘

可以改：`shared/source-runtime`、`shared/network`、`shared/data`、`shared/domain`、`gradle/libs.versions.toml`（仅加依赖）。
禁止改：`shared/app` 任何目录（含平台 source set——需要装配改动时在交付说明登记，主 agent 合并时接线）、`apps/`、`shared/design-system`、`shared/features`、`reference/`、`docs/`（交付说明除外）。

## 任务一：保存搜索与搜索选项的数据层（先做，浏览功能的下一块基石）

对照原版语义：`reference/komikku` 的保存搜索（`tachiyomi/data/library` 一带的 saved search 持久化与复用入口）与源筛选。Venera 侧对照 `reference/Venera-Next` 的 `doc/api/comic_source.zh.md` search 段与 `lib/features/comic_source/`：

- 确认 Venera 搜索选项契约（`search.options` / 筛选字段的形态与加载语义），把「选项集与当前取值」暴露为可查询、可持久化的域模型。
- 保存搜索：`(sourceKey, query, 选项快照)` 的增删改查落 `shared/domain` 模型 + `shared/data` 持久化（新表或既有偏好通道，按既有 schema 迁移模式加 `.sqm`）。
- `SourceBrowseService` 补「按保存搜索执行搜索」的服务路径。
- 验收：夹具测试覆盖选项形态（无选项源、多类型选项源）、保存搜索 CRUD 与按快照复放；语义有出入的按 Venera 文档为准并在报告登记。

## 任务二：作品级批量下载服务面

现状：`ExtensionSession.downloads` 已有单章 `enqueue/observe/pause/cancel/retry` 与 `countCompleted`。补齐作品级服务面，语义对照 Komikku 的下载触发偏好（`reference/komikku` 的 `DownloadPreferences` 与 `LibraryDownloadJob` 一带）：

- `enqueueAll(sourceKey, workId, 章节列表)` 与「只下未读/排除已读」的入队过滤；重复入队幂等。
- 详情页逐章状态的批量查询（一次查询返回作品全部章的下载态映射，避免 UI 逐章订阅）。
- 下载触发偏好（收藏作品更新时自动下载新章 / 下载接下来 N 章）的数据层：偏好读写 + 更新扫描完成后的钩子点（扫描器产新章处留出触发接口，平台/装配接线由主 agent 合并时做）。
- 验收：夹具测试覆盖批量入队幂等、过滤、状态映射、坏包收敛对计数的影响。

## 任务三：后台更新调度

Komikku 的间隔更新（`LibraryPreferences.autoUpdateInterval` + `LibraryUpdateJob`）：做纯 common 调度器，平台接线标注不做 WorkManager：

- 间隔偏好（复用 `library_prefs` 通道，值域对照 Komikku：每 6/12/24 小时与仅 Wi-Fi 占位——网络约束先登记不做）。
- 调度器：进程内时间驱动触发 `checkNow`、启动/回前台补跑到期检查、`nextUpdateMillis` 与智能更新的发布窗口协同（已有元数据）。
- 事件流：最近一次检查时间与结果供 UI 观察。
- 验收：虚拟时钟夹具测试覆盖到期触发、间隔变更重排、补跑一次不重复。

## 任务四：真实源回归扩展与实机验收准备

- 从 venera-configs 固定提交再挑 2 个复杂源（图片处理类、账号/评论类各至少一个），入 `CommunitySourceJourneyTest` 固定夹具（沿用既有模式，SHA 记录），在线实测一次并把波动如实记录。
- 移动端网页登录的实机验收：把 Android / iOS 的验收步骤脚本化程度最大化（安装、进入、取消路径、登录路径、会话隔离、注销清理），写成可执行的检查清单入报告；标注哪些步骤必须真机。

## 硬性验收

- 五模块 JVM 测试全绿，**交付前连续两遍**。
- 触碰宿主/会话/网络：Venera 原版脚本回归；改 HTML/会话/图片路径另跑社区脚本回归（含本轮新增源）。
- 三端构建全部 EXIT 0；漫画柜/Komga 至少各跑一遍全链路。

## 提交规范

`type(scope): 中文主题`（scope 举例：source-runtime、network、data、downloads、updates、search）。主题如实描述；禁止无信息主题与阶段编号；一提交一个内聚逻辑。

## 交付说明

覆盖写 `docs/tasks/engine-report-latest.md`：改动清单、测试证据（命令 + 结果 + 新增测试类与覆盖场景、社区源清单与 SHA）、需主 agent 桥接/装配的路径（保存搜索服务、批量下载注入、调度器挂载）、待实机验收项与下一轮计划。
