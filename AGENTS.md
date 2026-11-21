# Koyura 工作区指引

Kotlin Multiplatform + Compose Multiplatform 漫画阅读器。当前优先理清业务、视图与数据的职责和依赖边界，Komikku `290267d` 作为功能与交互参考。JS 插件引擎（QuickJS）协议兼容 Venera-Next。构建用 JDK 21（本机 `~/devtools/jdk-21`）与仓库 Gradle Wrapper。

## 构建与验收基线

- 验收按 `docs/verification.md` 执行。受影响模块跑快速回归，交付前跑全量 JVM 门。界面改动按项目既有行为与本次确认的交互要求人工核对。不设阶段门禁和证据归档。
- 交付前测试不绿不算完成。
- 改动 `:shared:source-runtime`、`:shared:network` 会话桥或宿主回调后，必跑 Venera 原版脚本回归（SHA-256 + 全回调链）。
- 代码标识符与文件名按功能命名，不含产品名（Komikku/Koyura）；`app.koyura` 基包与安装标识除外，注释引用来源不受限。

## 提交规范

- `type(scope): 中文主题`，conventional commits；scope 用模块或功能域（library、reader、shell、browse、source-runtime、design-system 等）。
- 主题写真实改动内容与影响面，不看 diff 也能明白这个提交做了什么；禁止 `V0`/`P1`/`M2` 等阶段或轮次编号，禁止「保存当前进度」这类无信息主题。
- 分逻辑组提交：一个提交对应一个内聚改动（一处行为修复及其测试、一组配套重构、一类文档调整）；跨域工作拆成多个提交，各自可独立描述与回滚；不把无关改动顺手捎进同一提交。

## 选型状态（以 docs/technology-stack.md 为准，勿凭通用 skill 想当然）

- **重构即用（方案 B 已拍板）**：Koin、MVIKotlin、androidx ViewModel（`lifecycle-viewmodel-navigation3`）。mvikotlin `keepers` 仍禁用。
- **已定排期、时机未到**：Multiplatform Settings、Kermit、Turbine（随架构收尾窗口接入）。
- **阅读器轮次即用**：ZoomImage（缩放/长图）、Coil 3（封面与列表图片）。
- **未选型勿引入**：Voyager、Decompose、Room、Retrofit、Paging 3、Firebase/Sentry 系；mvikotlin `keepers` 模块已废弃，禁用。
- 当前 DI 正由手动构造器注入 + `CompositionLocal`（装配在 `ExtensionApp`/`AppScaffold`）向 Koin 迁移。页面状态正由 session/state holder + 不可变 `UiState` 向 ViewModel / MVIKotlin Store 迁移。

## 架构重构任务

- 先读[目标架构](docs/application-architecture-mvvm-clean-udf.md)和[阶段索引](docs/architecture-refactor/README.md)，选择本次任务对应的阶段文档。对照生产入口、Gradle 配置与现有测试确认进度。按一条行为链路推进，已完成的实现和测试直接复用。
- `app` 负责导航和装配，`domain` 定义业务规则与契约，`data` 负责查询、持久化与事务，展示层按目标文档使用 MVVM + UDF 或 MVI。跨页面操作通过导航回调或领域契约，UI 保留现有视觉、入口和操作语义。
- 阶段 2 依次收口阅读器接口、解除封面解码依赖、验证退出保存，再拆出 `:shared:reader`。通用 UI 优先复用现有设计系统。 [阶段 2 细则](docs/architecture-refactor/reader-downloads-updates.md)

## 工作区技能路由

项目技能存放在 `.agents/skills/<name>/SKILL.md`。支持该目录发现的 agent 使用对应技能名称。其他 agent 直接读取下表链接。先用与当前改动最贴近的一个技能，涉及独立的组件、数据、构建或源边界时再补对应技能。任务只改一条链路时，只加载与之有关的入口及引用。架构、依赖版本和验收以项目文档为准。

| 当前改动 | 首选技能 |
| --- | --- |
| 分层、导航与 Koin 装配、Session 迁移、MVVM 或 MVI 状态所有权 | [kmp-architecture-refactor](.agents/skills/kmp-architecture-refactor/SKILL.md) |
| Compose 页面、通用组件、主题、宽窄布局、手势与 UI 副作用 | [compose-ui-boundaries](.agents/skills/compose-ui-boundaries/SKILL.md) |
| 领域契约、SQLDelight 仓储与事务、持久偏好、下载或更新任务 | [kmp-data-boundaries](.agents/skills/kmp-data-boundaries/SKILL.md) |
| Gradle、AGP 9、拆模块、资源和 source set、三端构建 | [kmp-gradle-modules](.agents/skills/kmp-gradle-modules/SKILL.md) |
| Venera 源协议、JS 宿主、WebView、Cookie、会话与图片路径 | [source-compatibility](.agents/skills/source-compatibility/SKILL.md) |
| 选择和移动回归测试、定位测试失败、交付前核对覆盖 | [kmp-refactor-verification](.agents/skills/kmp-refactor-verification/SKILL.md) |
| 审阅方案或 diff，核对需求、工程规范与兼容风险 | [kmp-architecture-review](.agents/skills/kmp-architecture-review/SKILL.md) |

## 权威文档

- `README.md` — 本地运行与模块表
- `docs/architecture.md` — 目录、包名与依赖方向
- `docs/technology-stack.md` — 版本基线、选型与升级排期
- `docs/verification.md` — 验收基线：测试门、三端构建与视觉核对口径
- `docs/application-architecture-mvvm-clean-udf.md` — 重构目标、目录树、依赖与状态模型
- `docs/architecture-refactor/README.md` — 四阶段实施规划与工作区技能来源
