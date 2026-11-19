# 引擎轨任务书：第二轮（GPT 6.1 / Codex）

你负责 Koyura 漫画阅读器的核心引擎。第一轮已交付并由主 agent 审阅合入 main：HTML 解析、按源会话与三平台凭据存储、网页登录服务、二进制/图片/计算宿主、探索分类回调、单章下载队列与本地优先阅读。前端视觉由视觉轨并行负责，与你无关。你的产出由主 agent 审阅后合并，main 只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/ui-parity`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/core-engine` 工作，分支 `feat/core-engine`（已含第一轮合并）。所有提交落此分支。
- 开场先读：工作区 `AGENTS.md`（选型红线与验收口径）、`docs/verification.md`（验收基线）、`docs/technology-stack.md`（技术栈约束）。
- JDK 21 在 `~/devtools/jdk-21`；测试命令必带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`；社区脚本回归另带 `-PveneraCommunityFixtureDir`，夹具下载步骤见 `docs/verification.md`「源运行时回归」。

## 地盘（本轮新增两项准入）

可以改：`shared/source-runtime`、`shared/network`、`shared/data`、`shared/domain`、`gradle/libs.versions.toml`（仅加依赖），以及本轮新增——`apps/android`（仅 WebView 登录页/适配）、`apps/ios`（仅 WKWebView 登录适配）、`shared/app/src/androidMain` 与 `shared/app/src/iosMain`（仅 `WebLoginBrowser` 实现与注册粘合）。
禁止改：`shared/app/commonMain` 及任何屏幕文件、`shared/design-system`、`shared/features`、`reference/`、`docs/`（交付说明除外，见文末）。

## 任务一：作品更新检测引擎（先做，这是 Updates 页的数据源）

现状：Updates 页 UI 已由视觉轨交付（对 Fake 仓储）；单作品目录同步已有原语 `ProductionLibraryRepository.syncWorkDetail`（打开详情时同步）。你的工作是把「检查更新」做成数据层批处理引擎，语义对照 `reference/komikku` 的更新扫描与 updates 数据模型（`app/src/main/java/eu/kanade/presentation/updates/` 及其依赖的扫描服务），只做数据层，不复刻 UI：

- 域模型放 `shared/domain`（更新结果：作品、新章列表、检查时间、错误）；扫描服务放 `shared/data`。
- 批量扫描书架作品：逐一 `comic.loadEp` 拉目录，与 `library_chapter` 差异比对；新章落库默认未读，同 ID 已读态保留，源端已消失的章保留本地记录。
- 结果可查询：作品级 feed（新章数分组）、每作「上次检查时间」；失败作品进错误列表可重试。
- 触发为服务方法（手动「立即检查」调用）；后台调度、智能更新的标题跳过规则不在本轮。
- 验收：夹具测试覆盖新增章检测、已读保留、章消失、失败重试、重启恢复。
- 依赖方向注意：`data` 不依赖 `features`。你保证域/数据接口的字段与 `features/updates/UpdatesModels.kt` 的 `UpdateWork`/`UpdateChapter` 对得上并在交付说明写明映射；app 层的生产桥接由主 agent 合并时完成。

## 任务二：下载与账号的平台适配 + 第一轮遗留项

- `WebLoginBrowser` 平台实现：Android 用系统 WebView（独立 Activity 或对话框），iOS 用 WKWebView；实现注册进 `shared/app` 的 androidMain/iosMain 粘合层，注册方式（expect/actual 或注入）在交付说明写清。桌面端网页登录本轮登记不做。捕获/交接语义已有假 browser 单测；平台真实打开登录页标「待实机验收」，交付说明列出实机验证步骤。
- 下载计数派生：`ChapterDownloadStore` 增加按作品统计已下载章数的查询 + 测试；接入书架 `ProductionLibraryRepository` 由主 agent 合并时做。
- 会话持久化性能（必做）：`SourceSessions.update` 当前每次收到 Set-Cookie 都在持锁下同步写平台凭据库，桌面端每次起子进程，爬页型源被串行拖慢。改为批量/防抖落盘（静默若干毫秒或会话收尾统一写一次）；`saveWebLogin` 与注销仍即时落盘；代际/租约并发语义不得回退。验收：用计数型 SecretStore 假实现断言连续 N 次 cookie 更新只落盘一次量级。
- Windows 凭据文件名防御性编码：`DesktopSourceSecretStore` 的 Windows 分支对 `sourceKey` 做哈希或 URL 编码后再拼路径（上游白名单已拦截，此为纵深防御）。

## 任务三：真实源在线端到端

从 venera-configs 社区仓库再挑 2–3 个源（HTML 解析类与账号类各至少一个），跑安装 → 探索/搜索 → 详情 → 章节 → 阅读 → 下载离线 → 重启续读全链路。每个源的结果（通过/卡点）与脚本 SHA-256 写进交付说明；能夹具化的一律自动化为回归测试（沿用第一轮 `CommunityHtmlCompatibilityTest` 的做法）。

## 任务四：宿主 API 余量盘点

对照 `reference/Venera-Next/doc/api/js.zh.md` 逐条清点宿主 API：已实现的列出处（文件与行号），未实现但真实源会用的补齐并配测试（优先 UI 对话框、剪贴板、`APP` 其余字段）；确认不做的写明理由。清单写进交付说明。

## 硬性验收（每次交付列出你跑过的命令与结果）

- `:shared:domain:jvmTest`、`:shared:data:jvmTest`、`:shared:source-runtime:jvmTest`、`:shared:features:jvmTest`、`:shared:app:jvmTest` 全绿。
- 触碰宿主回调/会话桥/网络：必跑 Venera 原版脚本回归（SHA-256 + 全回调链）；改 HTML/会话/图片路径另跑社区脚本回归（含本轮新增源）。
- 交付前跑一次三端：`:apps:android:assembleDebug`、`:apps:desktop:classes`、`:shared:app:linkDebugFrameworkIosSimulatorArm64`，全部 EXIT 0 才能宣称完成。
- **交付前在你自己的 worktree 连续两遍全量五模块测试，两遍全绿才算过**（第一轮的教训：单次绿不代表稳定）。

## 提交规范

- `type(scope): 中文主题`（scope 举例：source-runtime、network、data、updates、downloads、offline）。
- 主题如实描述改动与影响面；禁止「保存当前进度」与阶段编号式主题；一提交一个内聚逻辑。

## 交付说明（每轮末更新，供主 agent 审）

覆盖写 `docs/tasks/engine-report-latest.md`：本轮改动清单（文件 + 一句话）、测试证据（命令 + 结果 + 新增测试类名与覆盖场景、社区源清单与 SHA）、需主 agent 装配/桥接的路径、待实机验收项与下一轮计划。
