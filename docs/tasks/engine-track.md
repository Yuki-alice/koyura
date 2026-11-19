# 引擎轨任务书：第三轮（GPT 6.1 / Codex）

你负责 Koyura 漫画阅读器的核心引擎。前两轮已交付并合入 main：HTML/会话/登录/图片/计算宿主、探索分类回调、单章下载与本地优先阅读、作品更新扫描引擎、Android/iOS 网页登录适配、Cookie 防抖与 Windows 加固、脚本对话框与剪贴板宿主。前端视觉由视觉轨并行负责。你的产出由主 agent 审阅后合并，main 只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/ui-parity`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/core-engine` 工作，分支 `feat/core-engine`（已含第二轮合并与主 agent 桥接）。所有提交落此分支。
- 开场先读：工作区 `AGENTS.md`、`docs/verification.md`、`docs/technology-stack.md`。
- JDK 21 在 `~/devtools/jdk-21`；测试必带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`；社区脚本回归另带 `-PveneraCommunityFixtureDir`（夹具下载步骤见 `docs/verification.md`）。

## 地盘（上轮对 `apps/` 与 `shared/app` 平台粘合的准入本轮收回）

可以改：`shared/source-runtime`、`shared/network`、`shared/data`、`shared/domain`、`gradle/libs.versions.toml`（仅加依赖）。
禁止改：`apps/android`、`apps/ios`、`shared/app` 任何目录（含平台 source set——若确实需要改动，在交付说明登记路径与理由，由主 agent 裁决后处理）、`shared/design-system`、`shared/features`、`reference/`、`docs/`（交付说明除外）。

## 任务一：账号配置暴露（先做，视觉轨账号区 UI 依赖它）

现状：`SourceAccountService` 已有 `login`（固定用户名/密码）、`loginWithCookies`、`captureWebLogin`、`logout`；`supportsWebLogin` 可查。但 Venera 的登录表单是**脚本 `account.login` 声明字段后动态渲染**的（`reference/Venera-Next/lib/features/comic_source/comic_source_page.dart:1072-1210`），已登录态显示脚本 `account.infoItems` 的动态信息行（同文件 `:905-980`）。你要把这三块配置暴露出来：

- **域模型放 `shared/domain`**：账号字段声明（label、类型：用户名/密码/cookies 文本）、`registerWebsite` 外链、`loginWebsite` 是否声明、`infoItems` 声明（title + 动态取值回调）。
- 从 `ExtensionService` 暴露查询：源的账号配置（无 `account` 声明返回 null）、当前登录态（未登录 / 表单已登录 / 网页已登录——Venera 以 `data["account"]` 是否为 `'ok'`/List 区分，Re-login 行只对表单登录显示）、`infoItems` 的当前取值。
- `login` 保持兼容固定双字段的既有调用，同时提供按声明字段提交表单的通道（字段值按声明顺序传入）。
- 对照物：`comic_source_page.dart` 的 `_LoginPage`/`_buildAccount` 渲染语义与 `doc/api/comic_source.zh.md` 的 account 段；`checkLoginStatus`/`onLoginWithWebviewSuccess` 语义上一轮已实现，不要动。
- 验收：假脚本声明各形态账号配置（纯表单、纯网页、混合、含 infoItems、含 registerWebsite），断言暴露的配置与登录态判定；登录过期重登（Re-login）路径可重放。

**与视觉轨的对接契约（两边一致，形状以此为准）**：视觉轨按下方形状先行实现 UI，你保证域模型字段能无损映射到它，映射关系写进交付说明，app 层生产桥接由主 agent 合并时完成——

```kotlin
// 视觉轨 features 层契约（供对齐，不需要你实现）
data class AccountFieldConfig(val label: String, val type: AccountFieldType) // TEXT / PASSWORD / COOKIES
data class AccountInfoItem(val title: String, val value: String?)
sealed interface AccountState {
    data object NotConfigured : AccountState
    data class LoggedOut(val fields: List<AccountFieldConfig>, val registerUrl: String?, val webLoginSupported: Boolean) : AccountState
    data class LoggedIn(val formLogin: Boolean, val infoItems: List<AccountInfoItem>) : AccountState
}
```

## 任务二：短期请求缓存与防并发（上轮盘点标注的剩余兼容项）

Venera 请求配置里的 `cache-time`（短期响应缓存）与 `prevent-parallel`（同请求防并发）仍未实现。先确定 Ktor 拦截器与按源缓存的边界（缓存键、body 大小上限、缓存与按源会话的关系、并发去重的等待语义），再实现并配测试。`http_client` 与 Dio 专用 `extra` 确认真实源不需要后登记不做。

## 任务三：智能更新的标题跳过规则

对照 `reference/komikku` 更新扫描的跳过策略（`app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt` 一带的规则：已读跳过、标题匹配过滤等），在 `LibraryUpdateScanner` 落同语义的过滤开关（偏好可关，默认对齐 Komikku），配夹具测试。后台调度仍不在本轮。

## 任务四：宿主 API 收尾盘点

延续上轮的盘点表：确认 `Convert` 全表、定时器边界、`UI` 契约的剩余项在真实社区源（含账号类 Komga）中的实际使用面；有缺口的补齐，确认不做的写明理由。清单更新进交付说明。

## 硬性验收（每次交付列出你跑过的命令与结果）

- 五模块 JVM 测试全绿，**交付前连续两遍**（第一轮的教训已制度化）。
- 触碰宿主回调/会话桥/网络：必跑 Venera 原版脚本回归；改 HTML/会话/图片/账号路径另跑社区脚本回归（含 Komga 账号源）。
- 三端构建 `:apps:android:assembleDebug`、`:apps:desktop:classes`、`:shared:app:linkDebugFrameworkIosSimulatorArm64` 全部 EXIT 0。
- 真实源在线回归延续前两轮做法：漫画柜/Komga 至少各跑一遍全链路，波动如实记录。

## 提交规范

`type(scope): 中文主题`（scope 举例：source-runtime、network、data、updates、account）。主题如实描述改动与影响面；禁止无信息主题与阶段编号；一提交一个内聚逻辑。

## 交付说明（每轮末更新，供主 agent 审）

覆盖写 `docs/tasks/engine-report-latest.md`：本轮改动清单、测试证据（命令 + 结果 + 新增测试类名与覆盖场景、社区源结果与 SHA）、任务一契约映射表（域字段 → `AccountState` 形状）、需主 agent 裁决/桥接的路径、待实机验收项与下一轮计划。
