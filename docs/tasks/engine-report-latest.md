# 引擎轨交付报告

## 本轮改动

| 文件 | 改动 |
| --- | --- |
| `gradle/libs.versions.toml`、`shared/source-runtime/build.gradle.kts` | 使用已选型的 Ksoup 0.2.6 和 Okio，给社区原始脚本回归提供本地夹具目录参数。 |
| `shared/source-runtime/src/commonMain/kotlin/HtmlHost.kt`、`HtmlBootstrap.kt` | 同步实现文档与元素的 CSS 查询、节点遍历、属性和生命周期。 |
| `shared/source-runtime/src/commonMain/kotlin/SourceSessions.kt`、`SourceSessionHttp.kt`、`SessionHost.kt`、各平台 `SessionLock.kt` | 按源保存 Cookie、脚本数据与设置，文本、二进制和图片请求共用会话，处理重定向与注销失效。 |
| `shared/data/src/*Main/kotlin/session/*SourceSecretStore.kt`、各平台 `ExtensionPlatform.*.kt` | 将源会话接到 Android Keystore、iOS Keychain 和桌面系统凭据存储。 |
| `shared/network/src/ios*Main/kotlin/DefaultEngine.kt` | 关闭 Darwin 客户端的系统 Cookie 存储与自动 Cookie 注入，由源会话发送 Cookie。 |
| `shared/data/src/commonMain/kotlin/extension/SourceAccountService.kt`、`ExtensionService.kt` | 提供密码和 Cookie 登录、WebView 验证与采集交接、持久化后回调、注销和未完成登录失效。 |
| `shared/source-runtime/src/commonMain/kotlin/ScriptImageProcessor.kt`、各平台 `ScriptImageCodec.kt`、`VeneraImageConfig.kt` | 解析 `modifyImage`，以独立 QuickJS 实例执行裁切、旋转、填充和 PNG 输出。 |
| `shared/source-runtime/src/commonMain/kotlin/VeneraSource.kt`、`VeneraBootstrap.kt`、`VeneraSourceRuntime.kt`、`VeneraKtorTransport.kt` | 扩展浏览与互动回调、二进制 HTTP、脚本数据、会话和定时器桥接。 |
| `shared/source-runtime/src/commonMain/kotlin/ComputeHost.kt`、`ConvertHost.kt`、`UtilityBootstrap.kt`、各平台 `ConvertPlatform.kt` | 提供无主引擎闭包的独立计算、文本与字节转换、哈希和脚本兼容加解密。 |
| `shared/data/src/commonMain/kotlin/extension/SourceBrowseService.kt` | 提供探索页、分类项、异步筛选、分类漫画和排名的服务层调用。 |
| `shared/domain/src/commonMain/kotlin/downloads/ChapterDownloads.kt`、`shared/data/src/commonMain/sqldelight/app/koyura/data/extension/{7.sqm,DownloadJobs.sq}` | 定义单章下载队列、按钮观察流和持久化迁移。 |
| `shared/data/src/commonMain/kotlin/downloads/{ChapterDownloadStore,ChapterContentPackages}.kt` | 串行下载，按页验证 SHA-256，同目录原子提交，启动时核对临时文件、完整包与数据库状态。 |
| `shared/data/src/commonMain/kotlin/{session/ExtensionSession,reading/ComicReadingService}.kt`、`shared/domain/src/commonMain/kotlin/extension/ReadingRecords.kt` | 装配队列，优先打开本地内容并恢复页位，缺页时在可联网时回源。 |

## 验收

从 `core-engine` 工作树运行以下命令。两个社区脚本从同一固定提交下载，回归执行原始字节。

```bash
export JAVA_HOME="$HOME/devtools/jdk-21"
venera_ref='/Users/kurisu/project x/reference/Venera-Next'
community_dir=build/community-scripts
mkdir -p "$community_dir"
for script in baozi mxs; do
  curl -fsSL "https://raw.githubusercontent.com/venera-app/venera-configs/a04e05591f7832f7166d6660546613c114d6738a/$script.js" \
    -o "$community_dir/$script.js"
done
```

HTML 层执行 `:shared:source-runtime:jvmTest`。会话、图片与浏览层分别执行 `:shared:source-runtime:jvmTest :shared:data:jvmTest`。各次均传入下方两个 Gradle 属性。各层的原版示例回归均通过。下载层受影响模块验证执行 `:shared:data:jvmTest :shared:domain:jvmTest`。

```bash
./gradlew --continue \
  :shared:domain:jvmTest :shared:features:jvmTest :shared:data:jvmTest \
  :shared:source-runtime:jvmTest :shared:network:jvmTest :shared:app:jvmTest \
  :apps:android:assembleDebug :apps:desktop:classes \
  :shared:app:linkDebugFrameworkIosSimulatorArm64 \
  :shared:source-runtime:iosSimulatorArm64Test \
  -PveneraReferenceDir="$venera_ref" -PveneraCommunityFixtureDir="$community_dir"
```

最终整组命令 EXIT 0，JVM 合计 510 例。

| 命令 | 结果 |
| --- | --- |
| `:shared:domain:jvmTest` | EXIT 0，11 例通过。 |
| `:shared:features:jvmTest` | EXIT 0，69 例通过。 |
| `:shared:data:jvmTest` | EXIT 0，109 例通过。 |
| `:shared:source-runtime:jvmTest` | EXIT 0，55 例通过。 |
| `:shared:app:jvmTest` | EXIT 0，266 例通过。 |
| `:shared:network:jvmTest` | EXIT 0，`NO-SOURCE`，会话网络链路由 source-runtime 的测试覆盖。 |
| `:apps:android:assembleDebug` | EXIT 0。 |
| `:apps:desktop:classes` | EXIT 0。 |
| `:shared:app:linkDebugFrameworkIosSimulatorArm64` | EXIT 0。 |
| `:shared:source-runtime:iosSimulatorArm64Test` | EXIT 0，49 例通过。 |

| 测试类 | 覆盖 |
| --- | --- |
| `CommunityHtmlCompatibilityTest` | 未修改社区脚本的搜索、详情、目录、章节图片，断言 SHA-256。 |
| `SourceSessionTest` | 两源 Cookie 隔离、跨站重定向、脚本数据和图片会话重启恢复、候选版本回滚。 |
| `SourceAccountServiceTest` | WebView 检查 URL 和标题，先保存 Cookie 和 localStorage 后等待成功回调，注销使旧登录采集失效。 |
| `ScriptImageProcessorTest`、`VeneraImageCallbacksTest`、`ComicReadingServiceTest` | 像素级拼接、裁切、旋转，独立引擎，错误的图片脚本类型与损坏输入，沿用字节变换与失败重取回归。 |
| `SourceBrowseServiceTest` | 编号与游标优先级、混合探索、旧式分类、同步动态分类、筛选参数、排名游标、收藏和评论。 |
| `SourceUtilitiesTest` | 二进制 POST 字节偏移、`fetch` 响应、`compute` 跨引擎二进制传输、SHA-256、GBK、AES 与定时器取消。 |
| `ChapterDownloadStoreTest` | 单章按钮观察流、暂停与取消、失败重试、稳定任务 ID、重启补齐已提交内容包、断网续读、缺页回源、损坏包收敛。 |

- Venera-Next `minimal_source.js`，SHA-256 `41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d`。在 MockEngine 上断言安装元数据、搜索、详情、章节、正文图与缩略图配置、标签跳转。data 层还验证图片字节、Referer、页序和重启续读。 [原版示例](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/examples/minimal_source.js)
- 包子漫画 `baozi.js`，SHA-256 `6ccb52e7734e54c6dfcfcb9729adb46409f1e5c9d83bbda54313b066013e4255`。以 HTML 夹具断言选择器、目录 Map、默认设置、最终图片 URL。实站搜索端点探测经重定向返回 403。 [社区脚本](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/baozi.js)
- 漫小肆 `mxs.js`，SHA-256 `da2c222eda825b77eb3b67ac09ec33b70bc2512cd5fc867fbe9b958df55037f8`。以 HTML 夹具断言文本实体、属性选择器、目录、懒加载图片域名替换与文档释放。实站搜索端点探测返回 200，回归断言使用固定夹具。 [社区脚本](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/mxs.js)

## 装配接线与下一轮

- 扩展账号入口向 `ExtensionSession.service.accounts.captureWebLogin` 提供目标平台的 `WebLoginBrowser`。适配器负责打开和关闭独立 WebView、从通过 `checkStatus(url, title)` 的同一主框架采集 Cookie 与 localStorage。持久化失败时，用原 `PendingWebLogin.complete()` 重试。
- 浏览入口从 `ExtensionSession.service.browse` 读取探索定义、分类和排名。在线收藏、评论和其他互动通过 `ExtensionService.call` 及相应 `VeneraCallback` 接入。
- 章节单按钮收集 `ExtensionSession.downloads.observe(DownloadChapter(...))`，调用 `enqueue`、`pause`、`cancel`、`retry`。现有生产 `VeneraReaderService` 已调用 `ExtensionSession.comicReading` 的本地优先路径。批量下载面板后置。
- 平台真实网页登录、桌面系统凭据存储和真实源在线阅读需要在平台适配器接线后实测。Linux 凭据适配依赖系统 Secret Service 和 `secret-tool`，Windows 使用 DPAPI。

宿主版本报告与最低版本比较沿用既有语义。QuickJS 会等待当前求值中的一次性延时，后台间隔定时器由运行时调度并按源实例回收。
