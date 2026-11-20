# 视觉轨任务书：第四轮——阅读器轮次（Muse 1.3 / ZCode 新对话窗口）

你负责 Koyura 漫画阅读器的前端视觉表现。前三轮已交付并合入 main（五页收敛、Updates/More/下载队列、网格自适应、阅读背景四档、图标终稿、账号区、脚本 UI 宿主、更新错误入口）；夜间轨又合入分类/统计/历史多选/全局搜索/智能更新设置。本轮收「阅读器轮次」的既定积压。你的产出由主 agent 审阅后合并，main 只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/core-engine`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/ui-parity` 工作，分支 `feat/ui-parity`（已含夜间轨合并与主 agent 桥接）。
- 开场先读：`AGENTS.md`、`docs/verification.md`、`docs/technology-stack.md`。
- 白皮书已入库 `docs/design-whitepaper/`；原版源码在主工作区 `/Users/kurisu/project x/reference/komikku`（`290267d`）——行为存疑以源码为准。
- 夜间轨新页（统计/分类/历史多选/全局搜索）已在 main：本轮对照原版修正它们，**不要重构其结构**。

## 地盘

可以改：`shared/design-system`、`shared/features` 的 UI 层（含夜间轨新页的修正）、`shared/app` 屏幕与装配视觉接线。
禁止改：`shared/domain`、`shared/data`、`shared/source-runtime`、`shared/network`、`shared/media`、`gradle/`、`reference/`、`docs/`（交付说明除外）。

## 任务一：统计页字段校准

夜间轨按白皮书截图推断的字段需要对照原版定稿：`reference/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/stats/StatsScreen.kt` 与 `eu/kanade/presentation/more/stats/StatsScreenContent.kt`（注意原版类名叫 StatsScreen，不叫 StatisticsScreen）。按原版四张卡的真实字段与口径改 `StatisticsModels.kt` 与 `FakeStatisticsRepository`（Fake 值语义照原版计算式），字段映射表写进交付说明；数值格式串如与原版不同一并对齐。

## 任务二：点击区偏好接线

持久化契约已就位：`shared/domain` 的 `ReaderSettings.tapZone`（DEFAULT/LEFT_RIGHT/KINDLE）与 `invertTapZone`，含旧配置兼容测试；当前 chrome 会话仍持会话级字段（`ReaderChromeState` 注释已更新指路）。把点击区切到真实偏好链：

- 设置对话框的点击区段改走 `writeGlobal/writeOverride` 双路径（背景四档已示范该模式），覆盖段含「跟随全局」清除项。
- chrome 状态的 `tapZone/invertTapZone` 改从 `effective`（全局+覆盖合并）解析映射（域枚举 ↔ 查看器 `ComicTapZoneMode` 一一对应）。
- 既有会话级 rememberSaveable 移除，行为断言更新：改设置 → 重进阅读器保持；覆盖清除回全局。

## 任务三：详情页 FAB 化

对照 `reference/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt:532` 一带与白皮书 7.4：Start/Resume 内联双钮改为可拖拽 SmallExtendedFAB——左右吸边、垂直位置持久化、点击开阅读、长按拖动。位置持久化用既有偏好通道（对照原版存哪就存哪；若需 app 层键，沿用书架偏好的序列化模式）。UI 测试：显隐态、吸边、点击动作、位置存取；拖动手势按既有测试基建可达性评估，不可测的部分报告登记待真机。

## 任务四：阅读设置竖滑杆自适应重写

现状固定 280dp 宽 + 64dp 轨；原版为 weight + 自定义旋转布局（对照 `reference/komikku` 阅读设置滑杆实现）。重写为自适应并保持既有测试断言语义（值域与步进不变），报告附四态自查。

## 任务五：分类拖拽排序

夜间轨分类页现为行尾上/下移按钮；改为长按拖拽排序（对照白皮书 7.11 的拖动手柄）。手势实现评估项目内既有测试基建：若 Compose 拖拽在 JVM 测试不可稳定驱动，实现拖拽 + 保留按钮作为无障碍替代，测试覆盖按钮路径、拖拽路径登记真机验收。

## 硬性验收

- `:shared:design-system:jvmTest`、`:shared:features:jvmTest`、`:shared:app:jvmTest` 全绿，命令带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`，JDK 21 在 `~/devtools/jdk-21`。
- **交付前连续两遍 `--rerun-tasks` 全绿**；不新增零断言的截图生成类工具。
- 视觉对照逐页写差异与处置；夜间轨修正项单独提交便于审阅定位。

## 禁项

不引入 Koin / MVIKotlin / androidx ViewModel / Multiplatform Settings / Kermit / Turbine（已定排期）；不引 Voyager / Decompose / Room / Retrofit / Paging 3；不提前引 Coil 3 / ZoomImage。DI 维持手动构造器注入 + `CompositionLocal`。

## 提交规范

`type(scope): 中文主题`（scope 举例：statistics、reader、details、settings、categories）。主题如实描述；禁止无信息主题与阶段编号；一提交一个内聚逻辑（夜间轨每处修正独立提交）。

## 交付说明

覆盖写 `docs/tasks/ui-report-latest.md`：改动清单、视觉对照结论与四态自查、需主 agent 裁决/接线项（统计字段映射表、FAB 位置持久化通道、拖拽真机验收项）与下一轮计划。
