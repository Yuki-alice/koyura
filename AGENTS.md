# Koyura 工作区指引

Kotlin Multiplatform + Compose Multiplatform 漫画阅读器：视觉与功能全面对齐 Komikku `290267d`（与设计白皮书同版），实现采用现代 KMP 技术栈；JS 插件引擎（QuickJS）协议兼容 Venera-Next。构建用 JDK 21（本机 `~/devtools/jdk-21`）与仓库 Gradle Wrapper。

## 构建与验收基线

- 验收按 `docs/verification.md` 执行：受影响模块快速回归、交付前全量 JVM 门、界面改动按需与白皮书实机截图（`docs/design-whitepaper`，第二版含勘误表与 `tools/` 复算脚本）人工核对、行为存疑以 `reference/komikku` 源码为准、阅读行为沿用夹具测试；不设阶段门禁，不建证据归档。
- 交付前测试不绿不算完成。
- 改动 `:shared:source-runtime`、`:shared:network` 会话桥或宿主回调后，必跑 Venera 原版脚本回归（SHA-256 + 全回调链）。
- 代码标识符与文件名按功能命名，不含产品名（Komikku/Koyura）；`app.koyura` 基包与安装标识除外，注释引用来源不受限。

## 提交规范

- `type(scope): 中文主题`，conventional commits；scope 用模块或功能域（library、reader、shell、browse、source-runtime、design-system 等）。
- 主题写真实改动内容与影响面，不看 diff 也能明白这个提交做了什么；禁止 `V0`/`P1`/`M2` 等阶段或轮次编号，禁止「保存当前进度」这类无信息主题。
- 分逻辑组提交：一个提交对应一个内聚改动（一处行为修复及其测试、一组配套重构、一类文档调整）；跨域工作拆成多个提交，各自可独立描述与回滚；不把无关改动顺手捎进同一提交。

## 选型状态（以 docs/technology-stack.md 为准，勿凭通用 skill 想当然）

- **已定排期、时机未到**（第一轮阅读器对齐验收后统一迁移，勿提前引入）：Koin、MVIKotlin、androidx ViewModel（`lifecycle-viewmodel-navigation3`）、Multiplatform Settings、Kermit、Turbine。
- **阅读器轮次即用**：ZoomImage（缩放/长图）、Coil 3（封面与列表图片）。
- **未选型勿引入**：Voyager、Decompose、Room、Retrofit、Paging 3、Firebase/Sentry 系；mvikotlin `keepers` 模块已废弃，禁用。
- 当前 DI 为手动构造器注入 + `CompositionLocal`（装配在 `ExtensionApp`/`AppScaffold`）；页面状态为 session/state holder + 不可变 `UiState`。

## 工艺技能路由（入口在 ~/.zcode/skills/，嵌套经路由按需读）

| 任务 | 入口 |
| --- | --- |
| Compose UI / 状态 / 组件 / 性能 | `compose-expert`、`chrisbanes-skills`、`compose-performance-skills` |
| KMP 功能实现 / 测试 / 数据层 | `kotlin-kmp-claude-agent-skills`、`kmp-expert` |
| Gradle / AGP 9 工具链 | `kotlin-agent-skills` |
| WebView / Cookie / JS 桥安全 | `security-audit` |
| 发版前双轴评审 | `code-review` |

用户级 `dart-*` / `flutter-*` skills 与本 Kotlin 工作区无关，不要调用。

## 权威文档

- `README.md` — 本地运行与模块表
- `docs/architecture.md` — 目录、包名与依赖方向
- `docs/technology-stack.md` — 版本基线、选型与升级排期
- `docs/verification.md` — 验收基线：测试门、三端构建与视觉核对口径
