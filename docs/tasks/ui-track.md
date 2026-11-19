# 视觉轨任务书：Komikku 前端视觉复刻（Muse 1.3 / ZCode 新对话窗口）

你负责 Koyura 漫画阅读器的前端视觉表现，对照物是 Komikku 官方设计白皮书的真实实机截图。核心引擎由 GPT 6.1 的并行轨道负责，与你无关。你的产出由主 agent 审阅后合并，main 分支只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/core-engine`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/ui-parity` 工作，分支 `feat/ui-parity`，所有提交落此分支。
- 开场先读：工作区 `AGENTS.md`（技能路由与选型红线）、`docs/verification.md`（验收基线）、`docs/technology-stack.md`（技术栈约束）。

## 你的地盘（其他目录改动会在审阅时被整体拒绝）

可以改：`shared/design-system`（令牌/组件/图标/字符串）、`shared/features` 的 UI 层、`shared/app` 的屏幕与装配层（仅视觉接线所需，与引擎轨的装配冲突由主 agent 合并时裁决）。
禁止改：`shared/source-runtime`、`shared/network`、`shared/domain`/`shared/data` 任何文件、`shared/media`、`gradle/libs.versions.toml`、`reference/` 与 `docs/` 任何文件（交付说明除外，见下）。

## 对照体系（验收口径，写进 `docs/verification.md`，务必熟悉）

- 视觉基线：`/Users/kurisu/project x/reference/komikku-design-whitepaper-md` —— `07-页面规格.md` 按页给骨架与尺寸，`assets/phone/`（360dp）37 张、`assets/tablet/`（1280×800dp）37 张实机截图按页编号命名；令牌/组件/反馈见 `02-设计令牌.md`、`06-组件规格库.md`、`08-状态与反馈.md`。白皮书区分 Code/Screen/Inference 三类证据：写实现前引 Code 类（`文件:行`），比对用 Screen 类截图。
- 行为存疑：查 `/Users/kurisu/project x/reference/komikku`（commit `290267d`，与白皮书同版）的源码，不猜。
- 自查截图：按 360dp / 1280×800dp 画布 × 浅/深主题截窄宽四态，存 `build/` 临时目录人工比对后即弃，不建归档。

## 任务一：既有五页视觉收敛（先做，小步逐页提交）

已有生产页面五张：书架、浏览（源/扩展仓库/搜索/详情入口）、作品详情、历史、阅读器 Chrome。这些页面功能已接生产服务（书架真实 SQLite、阅读器五种模式），你的工作是**视觉**向白皮书对齐，不动数据行为。方法：开 `07-页面规格.md` 对应小节 → 对照 phone/tablet 双端截图 → 列出每页差异清单（颜色、间距、圆角、字阶、组件规格、布局结构）→ 逐项收敛 → 四态自查 → 提交。令牌数值一律走 `DesignTokens`，禁止 UI 内魔法数；已收敛项不要为对齐而破坏现有测试。

## 任务二：Updates 与 More 两页真实页面（占位 → 真实结构，服务可为 Fake）

现状：两页仍是 `TabRootScreen` 占位（`AppScaffold.kt` 直接落默认分支，`ui/TabScreens.kt`）。要求：

- 按白皮书 `07-页面规格.md` 7.7（更新）、7.8（更多）小节 + `assets/phone/15_updates.jpg`、`16_more.jpg`、`16b_more_scrolled.jpg`、tablet 同编号，搭出真实页面骨架与视觉。
- 数据侧：只允许 Fake 服务 + 假数据驱动 UI（在 `shared/features` 内自建 session/state holder + 不可变 UiState，沿用 `LibrarySession` 模式）。禁止碰 domain/data 层新接口——真实引擎由引擎轨接，届时只需替换注入。
- 下载队列面板（7.12）本期不做，只在 More 留入口并禁用，交付说明里注明。

## 硬性验收（每次交付列出你跑过的命令与结果）

- `:shared:design-system:jvmTest`、`:shared:features:jvmTest`、`:shared:app:jvmTest` 全绿（命令带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`，JDK 21 在 `~/devtools/jdk-21`）。
- 视觉对照结论：逐页写「差异项 → 处置（已对齐/白皮书未覆盖需主 agent 裁决）」。

## 禁项（选型红线，AGENTS.md 亦载）

不引入 Koin / MVIKotlin / androidx ViewModel / Multiplatform Settings / Kermit / Turbine（已定排期、时机未到）；不引 Voyager / Decompose / Room / Retrofit / Paging 3；不提前引 Coil 3 / ZoomImage（阅读器轮次任务，那两项涉及引擎轨管线）。DI 维持手动构造器注入 + `CompositionLocal`。

## 提交规范（不合规范会被审阅退回）

- `type(scope): 中文主题`（scope 举例：design-system、shell、library、reader、browse、history）。
- 主题如实描述改动；拒绝「保存当前进度」或阶段编号式主题；一提交一个内聚逻辑（一页一组件族一次收敛）。

## 交付说明（每轮任务结束时输出，供主 agent 审）

在分支上留 `docs/tasks/ui-report-latest.md`（可覆盖），包含三节：

1. 本轮改动清单（文件 + 一句话）。
2. 视觉对照结论：逐页差异与处置表 + 四态自查结果。
3. 需主 agent 裁决项与下一轮计划。
