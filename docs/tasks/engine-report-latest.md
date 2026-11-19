# 引擎轨交付报告 · 第二轮

## 本轮改动

| 文件 | 改动 |
| --- | --- |
| `shared/domain/.../updates/LibraryUpdates.kt`、`shared/data/.../updates/LibraryUpdateScanner.kt` | 定义和实现手动批量扫描、作品级 feed、上次检查时间及失败重试。 |
| `shared/data/.../library/LibraryStore.kt`、`LibraryUpdates.sq`、`8.sqm`、`ExtensionSession.kt` | 事务内按章节 ID 合并，保留已读态和已消失章节，新增默认未读，保存检测记录并迁移旧库。 |
| `ChapterDownloads.kt`、`ChapterDownloadStore.kt`、`DownloadJobs.sq` | 按 `(sourceKey, workId)` 查询已完成章节数量，启动时的损坏包收敛会同步影响计数。 |
| `SourceSessions.kt`、`ExtensionService.kt` | 按源将 HTTP 响应 Cookie 合并到内存，250 ms 静默后写密钥库，正常结束补写。网页登录和注销立即持久化。 |
| `DesktopSourceSecretStore.kt` | Windows 凭据文件名和临时文件名基于源键 SHA-256，有效的旧文件按需迁移。 |
| `AndroidWebLoginBrowser.kt`、`AndroidWebLoginRegistration.kt`、`PlatformExtensionApp.android.kt`、`MainActivity.kt` | 系统 WebView 独立 profile，按当前主文档采集并销毁，Android 入口通过工厂注入。 |
| `apps/android/build.gradle.kts`、`gradle/libs.versions.toml` | 引入 AndroidX WebKit 的独立 profile 和 Cookie 读取接口。 |
| `IosWebLoginBrowser.kt`、`PlatformExtensionApp.ios.kt` | 通过 Kotlin/Native 打开 WKWebView，使用不持久化网站数据存储，随 iOS 会话注入。 |
| `SourceAccountService.kt`、`WebLoginMonitor.kt`、`ExtensionPlatform.*.kt` | 统一检测 URL 和标题、拒绝过期文档采集并在关闭时终止轮询，保留可重试的网页登录交接。 |
| `VeneraUiHost.kt`、`InteractionBootstrap.kt`、`VeneraSourceRuntime.kt`、`VeneraBootstrap.kt` | 暴露对话框、提示、加载状态、剪贴板、日志和设置动作，JS 回调绑定源实例并在撤销时释放。 |
| `VeneraBootstrap.kt`、`shared/data/build.gradle.kts`、`CommunitySourceJourneyTest.kt` | 忽略社区脚本的空请求头，加入两条未经修改的社区脚本及在线可选回归。 |

目录读取使用 `comic.loadInfo(workId).chapters`，版本基线中的 `comic.loadEp(workId, chapterId)` 用于章节图片。扫描参照 Komikku 的按作品扫描、按章 ID 比对和检测时间分组，本轮按任务书保留源端消失的本地章节。 [Venera 目录和图片协议](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/api/comic_source.zh.md#2-数据与章节规则) · [Komikku 更新扫描](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/app/src/main/java/eu/kanade/tachiyomi/data/library/LibraryUpdateJob.kt#L532)

## 测试证据

从 `core-engine` 工作树执行。社区脚本和 Venera-Next 原版示例均按原始字节核对 SHA-256。

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

# 连续两遍执行，--rerun 强制五项测试任务各自重新运行。
for pass in 1 2; do
  ./gradlew --offline \
    :shared:domain:jvmTest --rerun :shared:data:jvmTest --rerun \
    :shared:source-runtime:jvmTest --rerun :shared:features:jvmTest --rerun \
    :shared:app:jvmTest --rerun "${args[@]}" || break
done

./gradlew --offline --continue :apps:android:assembleDebug :apps:desktop:classes \
  :shared:app:linkDebugFrameworkIosSimulatorArm64 "${args[@]}"
./gradlew --offline :shared:source-runtime:iosSimulatorArm64Test "${args[@]}"

./gradlew --offline :shared:data:jvmTest --tests '*CommunitySourceJourneyTest' \
  -PveneraCommunityLive=true "${args[@]}"
./gradlew --offline :shared:data:jvmTest \
  --tests '*CommunitySourceJourneyTest.manhuaguiPublicWebsite*' \
  -PveneraCommunityLive=true "${args[@]}"
./gradlew --offline :shared:data:jvmTest \
  --tests '*CommunitySourceJourneyTest.komga*' \
  -PveneraCommunityLive=true "${args[@]}"
```

开发中先跑 `:shared:domain:jvmTest :shared:data:jvmTest` 和 `:shared:source-runtime:jvmTest :shared:data:jvmTest`，均 EXIT 0。交付前五模块连续实跑两遍，均 EXIT 0。表格计数来自两次各自的 JUnit XML。

| JVM 模块 | 第一遍通过 | 第二遍通过 |
| --- | ---: | ---: |
| `shared:domain` | 11 | 11 |
| `shared:data` | 115 | 115 |
| `shared:source-runtime` | 57 | 57 |
| `shared:features` | 73 | 73 |
| `shared:app` | 268 | 268 |
| 合计 | 524 | 524 |

每遍只有两个需显式启用的公网用例跳过，原版示例与四个社区脚本的固定夹具均实跑。Android APK、桌面 classes 和 iOS 模拟器 framework 三个构建任务均 EXIT 0。额外执行 iOS 模拟器的 `source-runtime` 回归，51 例通过，EXIT 0。

公网与夹具四例组合复跑为 3 通过、1 失败，两个固定夹具及 Komga 公网通过，漫画柜公网整章下载一度返回 `FAILED`。单独复跑漫画柜公网后 EXIT 0，23 页下载、断网重启和进度断言均通过。Komga 在同次组合回归中完成了 35 页下载和离线恢复，随后单独复跑通过固定夹具及公网首图，但公网下载未在用例的 120 秒等待窗口内完成，命令 EXIT 1。

| 测试类 | 覆盖 |
| --- | --- |
| `LibraryUpdateScannerTest` | 分组目录、同 ID 已读态、消失章保留、重复扫描去重、错误列表只重试失败作品、数据库重开及取消收藏。 |
| `ChapterDownloadStoreTest` | 按源和作品隔离统计，暂停、完成、坏包收敛对计数的影响。 |
| `SourceSessionTest` | 30 次响应更新一次写库，登录、注销即时写入，过期租约丢弃，关闭补写。 |
| `WindowsSourceSecretStoreTest` | 路径分隔符、保留名及大小写差异经过确定性文件名编码。 |
| `WebLoginMonitorTest`、`SourceAccountServiceTest` | 主文档变化时重新验证、关闭取消探测、先写入 Cookie 和 localStorage 再等成功回调，注销使陈旧交接失效。 |
| `SourceUiTest` | 设置动作调用 UI、按钮选择与去重、输入验证、二进制配图、列表选择、提示、加载状态、剪贴板和日志。 |
| `CommunitySourceJourneyTest` | 两条真实社区脚本的全链路固定夹具，另有可选在线用例。 |

| 脚本 | SHA-256 和断言 |
| --- | --- |
| [Venera-Next `minimal_source.js`](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/examples/minimal_source.js) | `41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d`。全回调链通过，data 层另断言页序、Referer、字节、重启续读。 |
| [包子漫画 `baozi.js`](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/baozi.js) | `6ccb52e7734e54c6dfcfcb9729adb46409f1e5c9d83bbda54313b066013e4255`。搜索、详情、目录与章节 HTML 夹具通过。 |
| [漫小肆 `mxs.js`](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/mxs.js) | `da2c222eda825b77eb3b67ac09ec33b70bc2512cd5fc867fbe9b958df55037f8`。搜索、详情、目录与章节 HTML 夹具通过。 |
| [漫画柜 `manhuagui.js`](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/manhuagui.js) | `a5bf6c633e0ce728361dded097424b829134d618128f72b571e42525e8950128`。在线安装、搜索、详情、章节和 WebP 取图通过，独立复跑完成 23 页原子下载和断网重启续读。HTML 固定夹具验证搜索、分组目录、压缩 JS 图片列表、Referer、下载与离线恢复。 |
| [Komga `komga.js`](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/komga.js) | `2aa5b4e7d4b32bf282f86168b22af091d7436f4e3ad603defb7b6df738a36908`。官方演示账号在线登录、探索、搜索、详情、章节、读图、35 页下载、断网重启续读通过。固定夹具还断言 API 与图片请求均携带认证头。 |

Komga 初次在线样本的图片请求超过 15 秒，复测改用演示库中的另一作品。漫画柜在线页为 WebP，检测文件签名并在重启后比对下载字节，JVM 固定夹具还解码 PNG。漫画柜下载失败时，现有 API 只返回队列状态，无法定位到具体页面。Komga 复跑使用官方演示账号。 [Komga 演示说明](https://komga.org/docs/introduction/#demo)

## 宿主 API 盘点

按 Venera-Next 的 JavaScript API 文档核对。以下文件名除特殊注明外均位于 `shared/source-runtime/src/commonMain/kotlin`。 [JavaScript API](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/api/js.zh.md)

| 上游 API | 实现位置与边界 |
| --- | --- |
| `Network.get/delete/post/put/patch/sendRequest/fetchBytes` | `VeneraBootstrap.kt:73`、`VeneraKtorTransport.kt:72`。传递文本、JSON 或字节，状态及响应头回传，空请求头由宿主过滤。 |
| `new Cookie`、`Network.setCookies/getCookies/deleteCookies` | `VeneraBootstrap.kt:39,73`、`SessionHost.kt:16`、`SourceSessions.kt:59,101`。Cookie 查写同步可见，响应 Cookie 防抖保存。 |
| `fetch` 和 `ok/status/statusText/headers/arrayBuffer/text/json` | `VeneraBootstrap.kt:106`。沿用有限包装，`statusText` 为空字符串。 |
| `HtmlDocument.querySelector/querySelectorAll/getElementById/dispose` | `HtmlBootstrap.kt:15`、`HtmlHost.kt:25`。 |
| `HtmlElement` 查询、text、innerHTML、attributes、classNames、id、localName、children、nodes、parent、前后同级节点 | `HtmlBootstrap.kt:23`、`HtmlHost.kt:25`。 |
| `HtmlNode.text/type/toElement` | `HtmlBootstrap.kt:40`、`HtmlHost.kt:25`。 |
| `loadData/saveData/deleteData/loadSetting/isLogged/translate`、`ComicSource.sources` | `VeneraBootstrap.kt:4`、`SessionHost.kt:16`。设置动作由 `VeneraSourceRuntime.kt:194` 调用。 |
| `APP.version/locale/platform` | `VeneraSourceRuntime.kt:645`。该版文档列出的 APP 字段共三项，沿用原兼容版本语义。 |
| `setClipboard/getClipboard`、`UI.showMessage/showDialog/launchUrl/showLoading/cancelLoading/showInputDialog/showSelectDialog` | `InteractionBootstrap.kt:71`、`VeneraUiHost.kt:25,82,111`。由注入的界面宿主负责呈现，支持对话框动作、校验器、配图和加载取消，外部链接限 HTTP(S)。剪贴板转发给平台宿主的 `setClipboard` / `getClipboard`。 |
| `Convert` 的 UTF-8、GBK、Base64、hex、MD5/SHA-1/SHA-256/SHA-512、HMAC、AES ECB/CBC/CFB/OFB、RSA | `UtilityBootstrap.kt:64`、`ConvertHost.kt:22`、各平台 `ConvertPlatform.kt`。 |
| 图片 `url/method/data/headers/onResponse/modifyImage/onLoadFailed` | `VeneraImageConfig.kt`、`VeneraSourceRuntime.kt:261,278`、`shared/data/src/commonMain/kotlin/reading/ComicReadingService.kt:262,297`。正文支持重排、失败重取，缩略图支持响应字节变换。 |
| `Image` 尺寸、`copyRange/copyAndRotate90/fillImageAt/fillImageRangeAt/empty` | `ScriptImageProcessor.kt:28,62`。 |
| `log/console.log/warn/error` | `InteractionBootstrap.kt:54,65`、`VeneraUiHost.kt:82`。有宿主实例时可交给应用日志，Headless 为空实现。 |
| `createUuid/randomInt/randomDouble/setTimeout/setInterval/compute` | `UtilityBootstrap.kt:93,99,110`、`VeneraBootstrap.kt:280`、`ComputeHost.kt:19`。一次性延时跟随 QuickJS 求值，间隔任务可取消，计算引擎与已安装源实例隔离。 |
| `Comic/ComicDetails/Comment`、分页及跳转对象 | `VeneraBootstrap.kt:27,31,35`、`VeneraSource.kt`、`shared/data/src/commonMain/kotlin/extension/SourceBrowseService.kt`。 |

短期请求缓存（`cache-time`）、`prevent-parallel`、`http_client` 和 Dio 专用 `extra` 暂缓。本轮原始脚本回归无需这些选项，后续需要先确定 Ktor 拦截器和按源缓存边界。上游的 fetch 契约只包含表中列出的能力，UI 契约要求有活动界面。漫小肆的源设置动作使用 `UI.showLoading`、`UI.cancelLoading` 和 `UI.showMessage`，这些路径由 `SourceUiTest` 覆盖。漫画柜读取 `APP.version`，在线和固定夹具均走原始脚本。

## 装配和实机

- Updates 页在 `ExtensionApp` 创建生产 `UpdatesRepository`，从 `session.updates.feed()` 读数据，手动刷新调用 `checkNow()` 并重新读 feed，失败重试调用 `retryFailures()`。将 `WorkUpdate.work.{sourceKey,workId,title,coverUrl}` 映射给 `UpdateWork`，`newChapters` 映射给 `chapters`，`DetectedChapter.id/title` 原样传递，`detectedAtMillis` 在 app 格式化为 `dateText`。`isRead` 可用于已有未读过滤，`newChapterCount` 为该作品检测出的章数，`checkedAtMillis` 与 `failures()` 可接到刷新反馈和错误列表。无新章的作品也会更新 `LibraryManga.lastUpdateCheckMillis`，可由书架查询取得。
- 在 `ProductionLibraryRepository` 装配时从 `session.downloads.countCompleted(sourceKey, workId)` 填下载数，连到现有徽标与筛选。应用交互宿主通过各平台 `openExtensionSession(sourceUi = ...)` 注入，`session.service.runSourceSetting(key, setting)` 调用源设置按钮。
- Android 的 `MainActivity` 注册工厂，`PlatformExtensionApp.android.kt` 使用当前 Activity 建立适配器。iOS 在 `PlatformExtensionApp.ios.kt` 注入 `IosWebLoginBrowser`。账号页检查 `session.service.accounts.supportsWebLogin`，调用 `captureWebLogin(sourceKey)` 及 `PendingWebLogin.complete()`，保存失败时可复用采集结果。桌面网页登录留后续。
- Android / iOS 真实打开网页登录页待实机。安装可网页登录的测试源，打开原生窗口，先走取消流程确认无会话落盘，再完成站点登录并使该站点 URL 和标题通过 `checkStatus`。确认成功回调可读本次 Cookie/localStorage，切换另一源验证会话隔离，随后退出账号并重启验证凭据已清理。在验证中覆盖登录页重定向或标题变化以及窗口关闭。Android 需支持 MULTI_PROFILE 与 GET_COOKIE_INFO 的系统 WebView。iOS 使用临时 WKWebsiteDataStore。 [Android WebKit](https://developer.android.com/reference/androidx/webkit/ProfileStore) · [Apple WKWebsiteDataStore](https://developer.apple.com/documentation/webkit/wkwebviewconfiguration/websitedatastore)
- 下一轮在主 agent 完成 Updates、下载徽标、账号及交互宿主接线后，实机走完上述路径，再结合更多真实源决定请求缓存和代理兼容优先级。
