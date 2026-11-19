# 视觉轨任务书：第二轮（Muse 1.3 / ZCode 新对话窗口）

你负责 Koyura 漫画阅读器的前端视觉表现，对照物是 Komikku 官方设计白皮书的真实实机截图。第一轮已交付并由主 agent 审阅合入 main（五页视觉收敛、Updates/More 真实结构、外壳徽标联通）。核心引擎由引擎轨并行负责，与你无关。你的产出由主 agent 审阅后合并，main 只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/core-engine`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/ui-parity` 工作，分支 `feat/ui-parity`（已含第一轮合并）。所有提交落此分支。
- 开场先读：工作区 `AGENTS.md`（技能路由与选型红线）、`docs/verification.md`（验收基线）、`docs/technology-stack.md`（技术栈约束）。

## 你的地盘（其他目录改动会在审阅时被整体拒绝）

可以改：`shared/design-system`（令牌/组件/图标/字符串）、`shared/features` 的 UI 层（含本轮新增 downloads 页与 Fake）、`shared/app` 的屏幕与装配层视觉接线。
禁止改：`shared/source-runtime`、`shared/network`、`shared/domain`/`shared/data` 任何文件、`shared/media`、`gradle/libs.versions.toml`、`reference/`、`docs/` 任何文件（交付说明除外，见文末）。

## 对照体系（验收口径）

- 视觉基线：`/Users/kurisu/project x/reference/komikku-design-whitepaper-md` —— `07-页面规格.md` 按页给骨架与尺寸，`assets/phone/`（360dp）与 `assets/tablet/`（1280×800dp）实机截图按页编号命名；令牌/组件/反馈见 `02`、`06`、`08` 章。白皮书区分 Code / Screen / Inference 三类证据：写实现前引 Code 类（`文件:行`），比对用 Screen 类截图。
- 行为存疑：查 `/Users/kurisu/project x/reference/komikku`（commit `290267d`，与白皮书同版）源码，不猜。
- 自查截图：按 360dp / 1280×800dp 画布 × 浅/深截窄宽四态，存 `build/` 临时目录人工比对后即弃，不建归档。

## 任务一：书架网格（主 agent 已裁决，照此执行）

移除列数自定义，固定自适应：

- `LibraryScreen` 网格改 `GridCells.Adaptive(128.dp)`，padding 8dp、间距 4dp（白皮书 07:56 Code 值）；分组头 span 改 `GridItemSpan(maxLineSpan)`。
- 删除 `LibraryPrefs.columns` 与 `LibrarySheets` 的列数滑杆；`ProductionLibraryRepository` 的偏好序列化从十二键减为十一键，`parsePrefs` 保持缺键容错（老数据无害）。
- 列数滑杆既有测试退役改写，新增自适应断言（360dp 画布 2 列量级、1280dp 9 列量级，按实测画布写死）。
- 浏览源、相关推荐若有同型固定列网格，一并对齐 `Adaptive(128.dp)`。

## 任务二：阅读器背景默认 Automatic（主 agent 已裁决）

- `ReaderScreen` 根背景从 `colorScheme.background` 改为固定映射：暗主题 `#FF202125`、亮主题 `#FFFFFFFF`（Komikku `ReaderActivity.kt:1347/1432` 的 Automatic 语义），不用主题 token。
- chrome 不动：Automatic 下 chrome 跟随主题，现状即正确。
- 在根背景处留注释：四档背景偏好（Black/Gray/Automatic/White）后置实现时，Black/Gray 档须用 `ReaderTheme(darkTheme = true)` 包 chrome——原版 `ContextExtensions.kt:111-118` 的耦合语义，勿丢。
- 测试：浅/深两主题下根背景色值断言。

## 任务三：图标终稿

`BrandPlaceholder` 与第一轮两个原创几何字形（隐身眼镜、星爆徽标）按 Material Symbols 官方字形路径重画替换；其余 8 个手绘图标逐个与官方路径核对修正。对照 `reference/komikku` 的 drawable 资源与白皮书 02 章图标规格。

## 任务四：下载队列页（白皮书 07 §7.12，对 Fake）

More 页的下载队列入口落到真实页面：队列行（封面/标题/进度/状态）、暂停/恢复/取消/重试动作，全部由 Fake 仓储驱动——Fake 的状态机语义对照 `shared/data` 已交付的 `ChapterDownloadStore`（排队/下载/暂停/失败/完成）。入口不再禁用。会话与仓储沿用 `UpdatesSession` 模式，真实引擎由主 agent 在引擎轨合入后桥接。

## 任务五：白皮书未覆盖细项收敛

第一轮报告的待裁决细项（简介收起行数、ReadingButtons 样式、竖轨 280/64、页码指示器 12/6）：白皮书未覆盖的一律以 `reference/komikku` 源码为准对齐，提交注明 Code 出处；仍无出处的按现状保留并在交付说明登记。

## 硬性验收（每次交付列出你跑过的命令与结果）

- `:shared:design-system:jvmTest`、`:shared:features:jvmTest`、`:shared:app:jvmTest` 全绿，命令带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`，JDK 21 在 `~/devtools/jdk-21`。
- **交付前在你自己的 worktree 连续两遍 `:shared:app:jvmTest --rerun-tasks`，两遍全绿才算过**（第一轮的教训：单次绿不代表全量稳定；零断言的截图生成类工具不要新增）。
- 视觉对照结论逐页写差异与处置。

## 禁项（选型红线，AGENTS.md 亦载）

不引入 Koin / MVIKotlin / androidx ViewModel / Multiplatform Settings / Kermit / Turbine（已定排期、时机未到）；不引 Voyager / Decompose / Room / Retrofit / Paging 3；不提前引 Coil 3 / ZoomImage。DI 维持手动构造器注入 + `CompositionLocal`。

## 提交规范

- `type(scope): 中文主题`（scope 举例：design-system、shell、library、reader、browse、history、updates、downloads）。
- 主题如实描述改动；拒绝无信息主题与阶段编号；一提交一个内聚逻辑（一页一组件族一次收敛）。

## 交付说明（每轮末更新，供主 agent 审）

覆盖写 `docs/tasks/ui-report-latest.md`：本轮改动清单（文件 + 一句话）、视觉对照结论与四态自查（逐页差异与处置表）、需主 agent 裁决项与下一轮计划。
