# 引擎轨任务书：源运行时宿主能力与下载离线（GPT 6.1 / Codex）

你负责 Koyura 漫画阅读器的核心引擎：Venera-Next JS 源宿主能力与下载、离线阅读。前端视觉由另一条并行轨道负责，与你无关。你的产出由主 agent 审阅后合并，main 分支只读，禁止 push、禁止 merge、禁止碰 `.zcode/worktrees/ui-parity`。

## 工作区

- 只在 `/Users/kurisu/project x/.zcode/worktrees/core-engine` 工作，分支 `feat/core-engine`，所有提交落此分支。
- 先读工作区内 `AGENTS.md`、`docs/verification.md`、`docs/technology-stack.md`；技术栈约束以这三份为准，不要自行引入他们未批准的库。
- JDK 21 在 `~/devtools/jdk-21`；测试命令必带 `-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`。

## 你的地盘（其他目录改动会在审阅时被整体拒绝）

可以改：`shared/source-runtime`、`shared/network`、`shared/data`、`shared/domain`、下载/离线新代码所在模块、`gradle/libs.versions.toml`（仅加依赖）。
禁止改：`shared/design-system`、`shared/features` 既有 UI、`shared/app` 的屏幕与装配（若你的功能需要装配接线，在交付说明里写出接线路径，主 agent 合并时统一接）、两个 reference 仓库、`docs/` 任何文件。

## 任务一：Venera 源宿主能力（先做这个）

目标：让真实世界的 Venera 源装上能跑。当前缺口（已核实）：

- `VeneraSource.kt` 仅 8 条回调路径（search/searchNext/loadInfo/loadEp/loadThumbnails/onImageLoad/onThumbnailLoad/onClickTag）；缺 `explore`、`category`/`categoryComics`、`account`、在线收藏、评论。
- `VeneraBootstrap.kt:87` 宿主消息只接受非 bytes 的 HTTP 请求，其余抛 Unsupported；`:272` 明确拒绝 `modifyImage`。
- 缺 HtmlDocument / CSS 选择器（项目已定 Ksoup）、`Convert`、`compute`、`APP` 源数据读写、源 `settings`、Cookie 持久会话（当前 sourceKey 随请求携带但无按源 Cookie 仓储）、`fetch`、定时器。
- 版本比较、同 key 安装替换、事务回滚已有，不要动这三块的行为。

推进顺序（每完成一层，跑 Venera 原版脚本回归后单独提交）：

1. HTML 解析（Ksoup）：先跑通 2–3 个真实社区的纯 HTML 解析源，再提交。
2. Cookie 持久会话 + 网页登录平台接口（WebView 打开、`checkStatus(url,title)` 判定、采集 Cookie/localStorage 写入源会话、`onLoginSuccess` 回调）。Cookie/JS 桥属安全敏感面：禁明文落盘凭据，注意按源隔离与注销清理。
3. `modifyImage`、`onResponse`、图片失败重取。
4. `explore` / `category` / `categoryComics` 回调与 UI 无关的数据链路（UI 由视觉轨做，你出到 service 层即可）。

兼容基线：`reference/Venera-Next` 的 `c0c4597`——`doc/api/*.md`、`assets/init.js`、`lib/features/comic_source/`、`test/` 里的行为为准，逐条对着写。宿主报告的 `APP.version` 语义、`minAppVersion` 比较逻辑保持原规则，不改。

## 任务二：下载与离线阅读（任务一 1、2 层完成后开始）

从零到真实数据库表设计：
- 队列模型：`download_jobs` 带持久任务 ID；暂停、取消、重试、重启恢复复用同一记录。
- 写入协议：下载写临时内容包，校验通过后同目录原子提交，再更新数据库状态；启动时核对中断文件与记录并收敛状态。参考 `docs/architecture.md`「漫画、小说与离线数据」节的完整设计。
- 离线读取：`openChapter`/`loadPageBytes` 优先读本地内容包，缺失且联网时回源加载（不得只做纯在线架构的皮）。
- 控件范围：你只做 `domain` 接口 + `data` 实现 + 按钮状态流；UI 布局归视觉轨。下载先做单章按钮级验证，批量选择面板后置。
- 优先单源串行队列，进度通知做 UI 状态流即可，不引 WorkManager/Ketch，若 Ktor 会话接 Ketch 失败则参考其队列模型自写。

## 硬性验收（每次交付列出你跑过的命令与结果）

- 单元/集成：`:shared:source-runtime:jvmTest`、`:shared:data:jvmTest`、`:shared:domain:jvmTest`、`:shared:network:jvmTest`。
- 触碰宿主回调/会话桥/网络：按 `AGENTS.md` 必跑 Venera 原版脚本回归（SHA-256 + 全回调链），并在交付说明里列出验证过的脚本清单（含名称与来源链接）。
- 三端可达性：`:apps:android:assembleDebug`、`:apps:desktop:classes`、`:shared:app:linkDebugFrameworkIosSimulatorArm64`，全部 EXIT 0 才能宣称完成。

## 提交规范（main agent 审阅时按此重写，不合规范的仓库级改动会被退回）

- `type(scope): 中文主题`（scope 举例：source-runtime、network、data、downloads、offline）。
- 主题如实描述改动；拒绝「保存当前进度」「V1」这类无信息主题；一提交一个内聚逻辑。

## 交付说明（每轮任务结束时输出，供主 agent 审）

在分支上留 `docs/tasks/engine-report-latest.md`（可覆盖），包含三节：

1. 本轮改动清单（文件 + 一句话）。
2. 测试证据：命令 + 结果 + 新增测试类名与覆盖场景；真实脚本回归的脚本名与断言。
3. 需要主 agent 装配的接线路径（如有）与下一轮计划。
