# 引擎轨交付报告 · 第三轮

## 本轮改动

| 文件 | 改动 |
| --- | --- |
| `shared/domain/.../extension/SourceAccount.kt`、`ExtensionService.kt`、`SourceAccountService.kt` | 暴露账号声明、登录态和动态信息，保留固定双字段登录，新增按声明提交与重登。 |
| `VeneraBootstrap.kt`、`VeneraSourceRuntime.kt`、`SourceSessions.kt` | 投影账号声明与回调结果，在既有凭据存储中按顺序保存成功表单的值。 |
| `SourceRequestPolicy.kt`、`VeneraKtorTransport.kt`、`SourceSessions.kt`、`ExtensionService.kt`、`ComicReadingService.kt` | 按源与会话缓存脚本 GET，串行化显式标记的同请求，图片继续走阅读和离线链路。 |
| `LibraryUpdates.kt`、`LibraryUpdateScanner.kt`、`LibraryUpdates.sq`、`9.sqm`、`ExtensionSession.kt`、`LibraryStore.kt` | 增加智能更新偏好、跳过原因和作品元数据。旧库幂等补表，取消收藏时清理元数据。 |
| `gradle/libs.versions.toml`、`shared/data/build.gradle.kts` | 复用仓库既有的 kotlinx-datetime 0.7.1，按设备时区计算发布窗口。 |
| `VeneraUiHost.kt`、`UtilityBootstrap.kt`、`VeneraBootstrap.kt`、`VeneraSourceRuntime.kt` | 脚本等待请求或定时时，在同一求值上下文交付一次加载取消回调。 |
| `SourceAccountServiceTest.kt`、`CommunitySourceJourneyTest.kt`、`SourceRequestPolicyTest.kt`、`LibraryUpdateSmartPolicyTest.kt`、`LibraryUpdateScannerTest.kt`、`CommunityHtmlCompatibilityTest.kt`、`ConvertCompatibilityTest.kt` | 覆盖账号契约、请求缓存隔离、智能更新、原始脚本和转换向量。 |

## 账号映射

| 引擎查询和字段 | 视觉轨契约 |
| --- | --- |
| `ExtensionService.sourceAccountConfig(key) == null` | `AccountState.NotConfigured`。 |
| `SourceLoginState.LOGGED_OUT` 且存在账号声明 | `AccountState.LoggedOut`。 |
| `SourceAccountConfig.fields` 中的 `label`、`type` | 按原顺序映射为 `AccountFieldConfig`，三种类型直接对应 `TEXT`、`PASSWORD`、`COOKIES`。 |
| `SourceAccountConfig.registerWebsite` | `LoggedOut.registerUrl`，仅允许无内嵌凭据的 HTTP(S) URL。 |
| `SourceAccountConfig.declaresWebLogin && service.accounts.supportsWebLogin` | `LoggedOut.webLoginSupported`。 |
| `SourceLoginState.FORM` / `WEB` | `AccountState.LoggedIn` 的 `formLogin` 分别为 `true` / `false`。重登行仅在 `FORM` 时显示。 |
| `SourceAccountConfig.infoItems` 的 `title / hasValue`，以及 `sourceAccountInfo(key)` 的 `title / value` | 声明给出顺序及有无动态值。已登录时以当次查询结果映射为 `AccountInfoItem`，无值时保留标题与 `null`。 |

固定版 Venera 解析器把 `account.login(user, password)` 渲染为用户名、密码，并从 `loginWithCookies.fields` 读取有序字段。本轮在保留该回退的基础上，接受 `login.fields` 或 `account.loginFields` 中的 `{label, type}` 声明。优先使用 `account.login`，Cookie 形态经 `validate(values)` 提交整个数组。动态信息按 `account.infoItems[].data` 取值，支持 getter、同步函数和 Promise，表单成功按照上游标记规则保存字符串数组。网页、Cookie 和旧版引擎留下的 `"ok"` 都映射为不可重放的登录态。 [Venera 账号解析](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/features/comic_source/source_account_parser.dart) · [Venera 账号界面](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/features/comic_source/comic_source_page.dart#L905)

桥接提交调用 `service.accounts.loginForm(key, values)`，固定双字段调用可继续用 `login(key, user, password)`。重登调用 `reLogin(key)`，凭据只在平台凭据库和源会话内部使用。登录操作完成后重新读取状态与信息行。网页登录继续调用 `captureWebLogin(key)` 与 `PendingWebLogin.complete()`。

## 请求缓存与防并发

- 逻辑请求入口位于 `VeneraKtorTransport.exchange`，Ktor 发送和接收拦截器继续负责重定向及 Cookie。缓存只接收有源归属、无请求体和 Range 的成功 GET，区分文本与字节。键用 SHA-256 概括源键、方法、完整 URL、请求头、有效 Cookie 和会话版本。源数据、设置或 Cookie 变动后使用新版本，登录、注销和替换也会隔离旧响应。等待中的请求沿用排队前租约。
- 默认有效期 5 秒，`cache-time: long` 最长 6 小时，服务端 `max-age` 可缩短有效期。`cache-time: no` 跳过并移除同键旧响应。拒绝缓存带 `Set-Cookie`、`Vary: *`、禁缓存指令、重定向后交换或错误状态的结果。单条正文上限 1 MiB，文本按最坏字节数计费，缓存全局最多 10 MiB 和 256 条。到期直接走 GET。此轮采用短期内存缓存，Venera 的到期 HEAD 比对属于未移植范围。 [Venera 缓存拦截器](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/network/cache.dart#L114)
- `prevent-parallel: true` 按源、方法和完整 URL 排队。等待者取得执行权后重新查缓存，失效或不可缓存时各自发请求，错误和取消释放队列。未设置的请求独立运行。控制头由宿主消费。 [Venera 请求排队](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/network/app_dio.dart#L178)

## 智能更新

`session.updates.preferences()/setPreferences()` 使用现有 `library_prefs` 持久化开关。默认跳过只取一次且已有目录、已完结、未读积压、已有目录但从未读完一章以及超出当天发布窗口的作品。读章数为零时可单独关闭未读积压过滤，保留未开始规则。空目录仍会扫描。跳过项随 `UpdateScan.skipped` 返回原因，保留原有检查时间和失败记录。显式 `retryFailures()` 强制重试失败项。 [Komikku 默认偏好](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt#L64) · [Komikku 扫描规则](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt#L306)

任务书另要求标题匹配。固定版 Komikku 的更新任务没有标题文本排除列表，`excludedTitleTerms` 因此默认空白，配置后按书架标题做忽略大小写的包含匹配。`enabled = false` 跳过全部过滤条件。扫描从明确 `status` 或 `Status / 状态 / 狀態` 分组记住完结状态，`setTitleMetadata` 可接入已知的完结、只取一次与下次发布时间。Venera 目录缺少可靠的逐章发布日期，发布窗口规则仅用已知的 `nextUpdateMillis`，没有后台推断或调度。 [Komikku 发布窗口](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/domain/src/main/java/tachiyomi/domain/manga/interactor/FetchInterval.kt#L36)

## 宿主 API 盘点

静态核对固定提交的 38 个 JavaScript 文件，其中 34 个为社区源，另有模板、API 辅助和仓库维护脚本。实际使用面按源脚本中的调用统计，公网验证对象为漫画柜和 Komga。下表实现位置省略 `shared/source-runtime/src/commonMain/kotlin` 前缀。 [Venera JavaScript API](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/api/js.zh.md) · [社区源固定提交](https://github.com/venera-app/venera-configs/tree/a04e05591f7832f7166d6660546613c114d6738a)

| API | 实现与使用面 |
| --- | --- |
| `Network.get/delete/post/put/patch/sendRequest/fetchBytes` | `VeneraBootstrap.kt:73`、`VeneraKtorTransport.kt:78`、`SourceRequestPolicy.kt:47`。长缓存和防并发在部分社区源中确实声明，本轮实现。 |
| `Cookie` 和 `Network.setCookies/getCookies/deleteCookies` | `VeneraBootstrap.kt:39,73`、`SourceSessionHttp.kt`、`SourceSessions.kt:91`。即时会话内可见，响应 Cookie 防抖保存。 |
| `fetch` 的 `ok/status/statusText/headers/text/json/arrayBuffer` | `VeneraBootstrap.kt:106`。沿用上游有限封装，`statusText` 为空。 |
| HTML 文档、元素、节点的查询、访问和释放 | `HtmlBootstrap.kt:15,23,40`、`HtmlHost.kt:25`。漫画柜、包子和漫小肆夹具覆盖。 |
| 源数据、设置、注册表、翻译和 APP 三字段 | `VeneraBootstrap.kt:4,417`、`SessionHost.kt:16`、`VeneraSourceRuntime.kt:636`。漫画柜用 `APP.version` 选择章节格式。 |
| `Convert.encodeUtf8/decodeUtf8/encodeGbk/decodeGbk/encodeBase64/decodeBase64/hexEncode` | `UtilityBootstrap.kt:64`、`ConvertHost.kt:22`。Komga 用 UTF-8、Base64 和返回值兼容分支构造认证头。其他源也使用 Base64 解码和十六进制编码。 |
| `Convert.md5/sha1/sha256/sha512/hmac/hmacString` | `UtilityBootstrap.kt:75`、`ConvertHost.kt:61,69`。社区源用 MD5、SHA-256、SHA-512 及 `hmacString`。四种摘要和 HMAC 向量均实测。 |
| AES ECB/CBC/CFB/OFB 双向转换与 RSA PKCS#1 解密 | `UtilityBootstrap.kt:81`、`ConvertHost.kt:42,77`、各平台 `ConvertPlatform.kt`。社区源有 ECB 和 CBC 解密。CFB 与无显式 IV 的 OFB 用固定向量，RSA 用测试专用 PKCS#8 密钥。 |
| `setTimeout`、`setInterval` / `timer.cancel()` | `UtilityBootstrap.kt:110`、`VeneraBootstrap.kt:274,280,345`、`VeneraSourceRuntime.kt:554`。四个源使用一次性定时器，漫小肆用它实现域名探测超时。一次性回调随当前求值收尾，无浏览器 ID 或 clearTimeout，请求的延迟值夹在 0 到 24 小时内，并受现有求值时限约束。间隔立即首触发，可取消，每个运行时最多 128 个，卸载或替换释放。 |
| `UI.showMessage/showDialog/launchUrl/showLoading/cancelLoading/showInputDialog/showSelectDialog` | `InteractionBootstrap.kt:73`、`VeneraUiHost.kt:92,138,167`。实源使用提示、对话框、外链、输入与加载框。原版漫小肆测试覆盖 HTTP 等待时取消、回调去重和晚到响应。列表选择的取消与索引边界由宿主负责。 |
| `setClipboard/getClipboard`、`log/console` | `InteractionBootstrap.kt:54,65,71`、`VeneraUiHost.kt:50,167`。剪贴板在仓库 API 辅助文件声明，未发现源脚本直接调用。对话框、剪贴板和日志的引擎转发由 `SourceUiTest` 验证，应用宿主仍待接线。 |
| 图片请求配置、重排、失败重取及 Image API | `VeneraSourceRuntime.kt:267,284`、`ScriptImageProcessor.kt:28,62`、`shared/data/.../reading/ComicReadingService.kt:262,297`。漫画柜和 Komga 固定夹具覆盖页序、字节与下载后恢复。 |
| `createUuid/randomInt/randomDouble/compute`、业务对象和分页跳转 | `UtilityBootstrap.kt:93,99`、`ComputeHost.kt:19`、`VeneraBootstrap.kt:27,31,35`、`shared/data/.../extension/SourceBrowseService.kt`。计算引擎隔离源状态与交互。 |

`http_client: dart:io` 与 Dio 专用 `extra` 继续登记为未移植。四个回归源均可使用 Ktor 默认引擎，固定仓库中仍有两份脚本显式声明 `http_client`，其中一处注释提到原客户端兼容性。它们的 Ktor 行为尚未在线核实。扫描未发现其他源传入 Dio 专用选项，保留 `extra` 参数供后续按真实用例映射。 [显式客户端标记](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/ehentai.js#L1337) · [另一处标记](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/picacg.js#L45)

## 验收

从 `core-engine` 工作树执行，原始脚本按字节核对 SHA-256。

```bash
export JAVA_HOME="$HOME/devtools/jdk-21"
venera_ref='/Users/kurisu/project x/reference/Venera-Next'
community_dir=build/community-scripts
mkdir -p "$community_dir"
for script in baozi mxs manhuagui komga; do
  curl -fsSL "https://raw.githubusercontent.com/venera-app/venera-configs/a04e05591f7832f7166d6660546613c114d6738a/$script.js" \
    -o "$community_dir/$script.js"
done
args=(-PveneraReferenceDir="$venera_ref" -PveneraCommunityFixtureDir="$community_dir")

./gradlew --offline :shared:data:jvmTest --tests '*SourceAccountServiceTest*' "${args[@]}"
./gradlew --offline :shared:source-runtime:jvmTest "${args[@]}"
./gradlew --offline :shared:data:jvmTest --tests '*LibraryUpdate*Test' "${args[@]}"
./gradlew --offline :shared:source-runtime:jvmTest \
  --tests '*CommunityHtmlCompatibilityTest.unmodifiedMxsCancelsDomainCheckWhileNetworkIsPending*' \
  --tests '*SourceUiTest*' --tests '*SourceUtilitiesTest*' "${args[@]}"
./gradlew --offline :shared:source-runtime:jvmTest --tests '*ConvertCompatibilityTest*' "${args[@]}"

./gradlew --offline :shared:data:jvmTest --tests '*CommunitySourceJourneyTest' \
  -PveneraCommunityLive=true "${args[@]}"
./gradlew --offline --continue :apps:android:assembleDebug :apps:desktop:classes \
  :shared:app:linkDebugFrameworkIosSimulatorArm64 "${args[@]}"
./gradlew --offline :shared:source-runtime:iosSimulatorArm64Test "${args[@]}"

for pass in 1 2; do
  ./gradlew --offline \
    :shared:domain:jvmTest --rerun :shared:data:jvmTest --rerun \
    :shared:source-runtime:jvmTest --rerun :shared:features:jvmTest --rerun \
    :shared:app:jvmTest --rerun "${args[@]}" || exit $?
done
```

账号、请求策略、智能更新、漫小肆加载取消和 Convert 的定向回归均 EXIT 0。交付前五模块 JVM 连续两遍强制重跑，均 EXIT 0。

| JVM 模块 | 第一遍通过 | 第二遍通过 |
| --- | ---: | ---: |
| `shared:domain` | 11 | 11 |
| `shared:data` | 117 | 117 |
| `shared:source-runtime` | 64 | 64 |
| `shared:features` | 80 | 80 |
| `shared:app` | 276 | 276 |
| 合计 | 548 | 548 |

每遍仅跳过两个需显式启用的公网用例。Venera 原版回调链与四个社区源固定夹具均实际执行。Android APK、桌面 classes 与 iOS 模拟器 framework 均 EXIT 0。额外执行的 iOS 模拟器宿主测试通过 57 例，包含 Convert 全表向量。

| 测试类 | 本轮覆盖 |
| --- | --- |
| `SourceAccountServiceTest` | 无账号、固定双字段、声明多字段、纯网页登录、混合配置、注册 URL、动态信息、Cookie 顺序、重登与注销。 |
| `SourceRequestPolicyTest` | 源键、会话、请求头、查询参数、文本与字节隔离，默认与长 TTL，容量，禁缓存模式，串行等待、取消和注销竞态。 |
| `LibraryUpdateSmartPolicyTest` | 待读与未开始、已读可拉取、空目录、完结、只取一次、发布窗口、标题匹配、偏好持久化、全关与显式重试。 |
| `CommunityHtmlCompatibilityTest` | 原始漫小肆域名探测在 HTTP 返回前取消，重复点击只回调一次，迟到响应无成功提示。 |
| `ConvertCompatibilityTest` | 在真实 QuickJS 内核对 UTF-8、GBK、Base64、hex、四种摘要和 HMAC、四种 AES 模式与 PKCS#8 RSA 解密。 |
| `CommunitySourceJourneyTest` | 漫画柜与 Komga 固定夹具及可选公网回归，Komga 走新表单 API，断网重启检查登录态、页字节和进度。 |

| 脚本 | SHA-256 | 结果 |
| --- | --- | --- |
| [Venera-Next `minimal_source.js`](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/examples/minimal_source.js) | `41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d` | 搜索、详情、章节、正文与缩略图回调及标签跳转通过。data 层验证 Referer、页序、字节、重启续读。 |
| [包子漫画](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/baozi.js) | `6ccb52e7734e54c6dfcfcb9729adb46409f1e5c9d83bbda54313b066013e4255` | 搜索、详情和章节 HTML 夹具通过。 |
| [漫小肆](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/mxs.js) | `da2c222eda825b77eb3b67ac09ec33b70bc2512cd5fc867fbe9b958df55037f8` | 搜索、详情和章节 HTML 夹具，以及加载取消夹具通过。 |
| [漫画柜](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/manhuagui.js) | `a5bf6c633e0ce728361dded097424b829134d618128f72b571e42525e8950128` | 固定 HTML 夹具通过。公网走完搜索、详情、目录、WebP 取图、23 页下载和断网重启续读。 |
| [Komga](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/komga.js) | `2aa5b4e7d4b32bf282f86168b22af091d7436f4e3ad603defb7b6df738a36908` | 固定 API 夹具通过。公网官方演示账号走完登录、探索、搜索、详情、目录、取图、35 页下载和断网重启续读。 |

两次公网组合运行均为 4 例通过，EXIT 0。第二次 Komga 使用新表单入口，并在离线重开时断言 `FORM` 登录态。两次均完成漫画柜 23 页与 Komga 35 页下载并恢复原阅读进度。本轮两次通过的组合运行未遇下载超时。 [Komga 官方演示](https://komga.org/docs/introduction/#demo)

## 主 agent 桥接与实机

- 在 `shared/app/src/commonMain/kotlin/ExtensionApp.kt` 的生产装配中将账号查询、提交、重登与注销桥接给视觉轨浏览源详情页。装配按上表计算 `AccountState`，通过 `SourceAccountService` 复用已注册的 Android 和 iOS 登录适配器。桌面端网页登录继续搁置。
- `shared/app/src/commonMain/kotlin/updates/ProductionUpdatesRepository.kt` 已桥接真实扫描。目前刷新会用本次扫描结果替换列表。桥接时应在检查或重试后重读 `session.updates.feed()`，让被跳过标题的历史新章继续显示，再将 `UpdateScan.skipped` 映射到刷新反馈，并在应用偏好入口接入 `session.updates.setPreferences()`。已知发布窗口和只取一次数据通过 `setTitleMetadata()` 接入。
- `PlatformExtensionApp.android.kt`、`PlatformExtensionApp.ios.kt` 和 `PlatformExtensionApp.jvm.kt` 可通过现有 `openExtensionSession(sourceUi = …)` 注入前台对话框、加载提示和系统剪贴板。交付范围为引擎宿主与回调生命周期。
- Android / iOS 真实网页登录仍待实机。分别用表单、Cookie 和网页源验证字段顺序、可用性、注册链接、取消、重定向后的 Cookie/localStorage 交接和注销清理。表单先登录，再模拟过期，确认重登使用原顺序的值并刷新动态信息。用漫小肆触发域名检测后取消，确认提示只出现一次。原生 WebView 的真机结果与模拟器构建分开记录。
- 下一轮优先实机完成上述路径。若出现明确的站点差异，再对照请求样本评估 Ktor 的可移植客户端或拦截器选项。
