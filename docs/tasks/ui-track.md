# 视觉轨任务书：第三轮（Muse 1.3 / ZCode 新对话窗口）

你负责 Koyura 漫画阅读器的前端视觉表现，对照物是 Komikku 设计白皮书实机截图与 `reference/komikku` 源码。前两轮已交付并合入 main（五页视觉收敛、Updates/More/下载队列真实结构、网格自适应、阅读器背景 Automatic、图标终稿）。核心引擎由引擎轨并行负责。你的产出由主 agent 审阅后合并，main 只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/core-engine`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/ui-parity` 工作，分支 `feat/ui-parity`（已含第二轮合并与主 agent 桥接）。所有提交落此分支。
- 开场先读：工作区 `AGENTS.md`、`docs/verification.md`、`docs/technology-stack.md`。

## 地盘（本轮新增平台粘合准入）

可以改：`shared/design-system`、`shared/features` 的 UI 层（含新增 Fake 与会话）、`shared/app` 的屏幕与装配层视觉接线，以及**本轮新增**——`shared/app/src/androidMain`、`iosMain`、`jvmMain`（仅剪贴板与界面宿主的平台实现粘合，不得触碰引擎文件）。
禁止改：`shared/source-runtime`、`shared/network`、`shared/domain`/`shared/data` 任何文件、`shared/media`、`gradle/libs.versions.toml`、`reference/`、`docs/` 任何文件（交付说明除外，见文末）。

## 任务一：扩展详情页账号区（本轮核心，对 Fake 先行）

模型复刻 Venera（协议基线），不是 Komikku 的共享 CookieJar 模式。参照物：`reference/Venera-Next/lib/features/comic_source/comic_source_page.dart:897-980`（`_buildAccount`）与 `:1072-1210`（`_LoginPage`）；视觉规格对照白皮书 `07-页面规格.md` 扩展/源设置小节与 `06-组件规格库` 的列表/表单/对话框条目。

**契约（与引擎轨任务书一致，主 agent 合并时写生产桥接）**——在 `shared/features` 定义并按此实现 UI 与 Fake：

```kotlin
data class AccountFieldConfig(val label: String, val type: AccountFieldType) // TEXT / PASSWORD / COOKIES
data class AccountInfoItem(val title: String, val value: String?)
sealed interface AccountState {
    data object NotConfigured : AccountState
    data class LoggedOut(val fields: List<AccountFieldConfig>, val registerUrl: String?, val webLoginSupported: Boolean) : AccountState
    data class LoggedIn(val formLogin: Boolean, val infoItems: List<AccountInfoItem>) : AccountState
}
interface AccountRepository {
    suspend fun state(sourceKey: String): AccountState
    suspend fun login(sourceKey: String, values: List<String>): Boolean   // 按声明字段顺序
    suspend fun captureWebLogin(sourceKey: String): Boolean              // 平台浏览器不可用时由注入方返回 false
    suspend fun logout(sourceKey: String): Boolean
}
```

要求：

- 扩展详情页（ExtensionDetailsScreen）内按源渲染账号区；`NotConfigured` 时整个区块不出现（与 Venera 同）。
- 未登录：按 `fields` 动态渲染表单（TEXT/PASSWORD/COOKIES 三种输入形态）、`registerUrl` 非空时给「注册账号」外链、`webLoginSupported` 为真给「网页登录」按钮（调 `captureWebLogin`，进行中态与失败提示走任务二的反馈宿主）；表单提交按字段顺序传 `login`。
- 已登录：`infoItems` 信息行（`formLogin` 为真时附「重新登录」行，点回登录表单）+ 注销动作（确认后调 `logout`）。
- Fake 状态机覆盖：无配置/纯表单/纯网页/混合/含 infoItems 五形态，会话与 UI 测试照此写。

## 任务二：VeneraUiHost 的 Compose 宿主

引擎轨已暴露脚本 `UI.showMessage/showDialog/showInputDialog/showSelectDialog/showLoading/cancelLoading` 与剪贴板回调（`InteractionBootstrap.kt`、`VeneraUiHost.kt`，只读引用其接口，不修改）。实现 app 层宿主并注入：

- 对话框/输入框/选择列表（动作按钮、校验器、配图）走 Compose `Dialog`，视觉对照白皮书 `08-状态与反馈.md` 与 `06` 的对话框规格；`showMessage` 走 snackbar；`showLoading/cancelLoading` 走顶层加载态。
- 剪贴板：`shared/app` 各平台 source set 写 expect/actual（Android `ClipboardManager`、JVM `java.awt.datatransfer`、iOS `UIPasteboard`），commonMain 收口。
- 注入点：装配层把宿主传给 `openExtensionSession`（签名与引擎轨第二轮交付一致；如需装配改动超出你的地盘，在交付说明登记，主 agent 合并时接线）。
- UI 测试：对话框三形态、输入校验、snackbar、加载态各至少一例。

## 任务三：更新错误列表入口（契约已有，纯 UI）

`UpdatesRepository` 已有 `retryFailures`（主 agent 桥接后为真实重试）。Updates 页按白皮书 7.7 加「更新错误」入口（顶栏溢出或区块切换），显示失败作品与错误信息（对 Fake failures 数据），重试按钮调 `session.retryFailures()`。Fake 增加失败数据形态，测试覆盖展示与重试动作。

## 任务四：阅读器轮次遗留项（本轮可选末位，时间不足可留下一轮并在报告注明）

- 详情页 FAB 化：Start/Resume 内联钮改 Komikku 的可拖拽 SmallExtendedFAB（左右吸附、位置持久化），对照 `reference/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreen.kt:532` 一带。
- 阅读设置竖滑杆自适应重写：固定 280/64 改 weight + 自定义旋转布局（对照原版源码）。

## 硬性验收（每次交付列出你跑过的命令与结果）

- `:shared:design-system:jvmTest`、`:shared:features:jvmTest`、`:shared:app:jvmTest` 全绿，命令带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`，JDK 21 在 `~/devtools/jdk-21`。
- **交付前连续两遍 `--rerun-tasks` 全绿**；不新增零断言的截图生成类工具。
- 平台剪贴板改动后跑三端构建：`:apps:android:assembleDebug`、`:apps:desktop:classes`、`:shared:app:linkDebugFrameworkIosSimulatorArm64`，全部 EXIT 0。
- 视觉对照结论逐页写差异与处置（四态截图存 `build/` 比对后即弃）。

## 禁项（选型红线，AGENTS.md 亦载）

不引入 Koin / MVIKotlin / androidx ViewModel / Multiplatform Settings / Kermit / Turbine（已定排期）；不引 Voyager / Decompose / Room / Retrofit / Paging 3；不提前引 Coil 3 / ZoomImage。DI 维持手动构造器注入 + `CompositionLocal`。

## 提交规范

`type(scope): 中文主题`（scope 举例：account、shell、updates、ui-host、design-system、details）。主题如实描述改动；拒绝无信息主题与阶段编号；一提交一个内聚逻辑。

## 交付说明（每轮末更新，供主 agent 审）

覆盖写 `docs/tasks/ui-report-latest.md`：本轮改动清单、视觉对照结论与四态自查（逐页差异与处置表）、任务四的取舍说明、需主 agent 裁决/接线项与下一轮计划。
