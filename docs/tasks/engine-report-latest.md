# 引擎轨交付报告 · 第五轮

## 改动清单

| 文件 | 改动 |
| --- | --- |
| `SourceFeed.kt`、`SourceFeedService.kt` | 定义 Feed 卡片及稳定标识，在 `library_prefs` 保存卡片启停和顺序，按当前探索声明收敛。 |
| `SourceDiscovery.kt`、`SourceDiscoveryService.kt` | 在 `library_prefs` 保存来源固定、隐藏、用户分组、默认来源及全局和每源 NSFW 偏好，提供发现与搜索查询。 |
| `SourceBrowseService.kt`、`ExtensionService.kt`、`ExtensionSession.kt` | 共用探索声明解析，在扩展生命周期锁内读取注册源和声明。会话暴露 `feed` 与 `discovery`。 |
| `SourceFeedServiceTest.kt`、`SourceDiscoveryServiceTest.kt` | 覆盖源升级、偏好重开和多源隔离，以及固定、分组、默认选择、隐藏和 NSFW 过滤。 |
| `CommunitySourceJourneyTest.kt`、`VeneraKtorTransportTest.kt` | 公网复测记录 HTTP 状态与类型，夹具断言 302、默认与显式 UA、透传头和最终响应。 |

## Feed 和来源语义

Feed 以 `(sourceKey, explore.title)` 标识卡片。Venera 要求同一源下的标题唯一，`index` 来自当前注册版本。读取时保持已有卡片的顺序和开关，移除成功读取的声明中消失的卡片，按源键及声明顺序追加新卡片并默认启用。改名按移除旧卡片和新增新卡片处理。源暂不可用或某源声明读取出错时保留其偏好，下次可用时再收敛。Komikku 持久化主动添加的全局 Feed 和 `feed_order`。本轮 Feed 的初始内容来自 Venera 探索声明，追加默认启用按任务书执行。 [Venera 探索契约](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/api/comic_source.zh.md#探索页) · [Komikku Feed 查询](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/data/src/main/sqldelight/tachiyomi/data/feed_saved_search.sq) · [Komikku Feed 装配](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/app/src/main/java/eu/kanade/tachiyomi/ui/browse/feed/FeedScreenModel.kt)

Komikku 的来源偏好保存固定、禁用集合和 NSFW 显示开关，源列表还支持用户分类。固定版 Venera 文档和 Dart 解析器的 `category` 是源内作品分类，没有可映射到来源列表的分组或 NSFW 字段。本轮分组和 NSFW 标记属于用户偏好。新来源未标记，全局开关默认开启，关闭时隐藏已标记来源。隐藏来源和被全局开关隐藏的来源也从正常 Feed 卡片和全局搜索候选剔除，管理查询仍可读取它们。用户可选择默认来源，暂时不可见时保留选择并返回空的生效默认值。固定项排在非固定项前，同组按名称及源键稳定排序。 [Komikku 源偏好](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt) · [Venera Dart 源解析器](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/features/comic_source/parser.dart#L294)

## 网络诊断与公网复测

| 项目 | 固定版 Venera 与本仓库 | 结论 |
| --- | --- | --- |
| 请求 UA 与头 | 脚本 `_performHttp` 默认使用 `webUA`，RHttp 的应用 UA 仅在缺少 UA 时生效。Ktor 使用完全相同的 `webUA`，并保留任意大小写的显式 UA 和普通请求头。 | 漫画柜和 Komga 公网通过，302/UA/普通头的组合动作有固定夹具。 |
| 重定向和 Cookie | Venera 由 Dio/RHttp 根据 `followRedirects` 和重定向上限跟随。Ktor 默认跟随，Cookie 拦截器按源、目标和原请求同源关系重新计算 Cookie。 | MangaDex 实际跟随到了验证页。既有夹具验证同源 Cookie、跨源隔离与重启。 |
| 站点挑战 | Venera 的 Cloudflare 拦截器仅在 403 带标准挑战标记时识别专用错误，并能读取应用已有的验证会话 UA。Ktor 把最终 HTTP 状态与响应体交给脚本。 | 本次两源响应均未报告标准挑战头。MangaDex 由跳转目的页确认验证，ComicWalker 是匿名令牌入口拒绝。复测只发送原版脚本的正常请求，未发现可由 UA、普通头或重定向修正的兼容缺口。 |

[Venera 脚本 HTTP 调用](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/foundation/js_engine.dart#L803) · [Venera Dio/RHttp](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/network/app_dio.dart#L145) · [Venera 挑战拦截器](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/network/cloudflare.dart#L55)

| 原版脚本 | 公网结果 |
| --- | --- |
| ComicWalker | 安装成功，初始化的 `POST /v1/users` 收到 **403 `text/html`**。搜索 `GET /v1/search/comics` 收到 **400 `application/json`**。原脚本尝试刷新匿名令牌，仍收到 **403 `text/html`**，随后的 JSON 解析中断。固定夹具通过图像计算、下载及离线恢复。 |
| MangaDex | 安装成功，`GET /manga` 收到 **302 `text/plain`**，随后 `mangadex.org/edge/challenge` 返回 **200 `text/html`**。原脚本在 JSON 解析处中断。固定夹具通过安全分级选项、论坛 Cookie、评论、读图重试和离线恢复。 |
| 漫画柜 | 原版脚本完成搜索、详情、章节、WebP 取图、23 页整章下载和断网重启续读。 |
| Komga | 官方演示登录、探索、搜索、详情、取图、35 页整章下载和断网重启续读均通过。 |

另核对了 ComicWalker 的版本发现。其原脚本在 iTunes 查询成功后从响应顶层取 `version`，独立请求实测 HTTP 200，版本字段在 `results` 内。按原脚本的字符串插值，这种响应形状会让显式 UA 带上 `undefined`。这一点属于源脚本的版本解析，现有证据无法把本次 403 归因于该字段。 [ComicWalker 固定脚本](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/comic_walker.js#L78)

公网组合命令执行 8 例，6 例通过。ComicWalker 和 MangaDex 遇到上述站点阻断，命令 EXIT 1。公网追踪只记 HTTP 状态、内容类型、跳转与路径，保留脚本原样。 [Komga 官方演示](https://komga.org/docs/introduction/#demo)

## 验收

在 `core-engine` 工作树执行。以下命令使用 JDK 21 和本地 Gradle 依赖。

```bash
export JAVA_HOME="$HOME/devtools/jdk-21"
reference='/Users/kurisu/project x/reference/Venera-Next'
fixtures=build/community-scripts
mkdir -p "$fixtures"
for script in baozi mxs manhuagui komga comic_walker manga_dex; do
  curl -fsSL "https://raw.githubusercontent.com/venera-app/venera-configs/a04e05591f7832f7166d6660546613c114d6738a/$script.js" \
    -o "$fixtures/$script.js"
done
args=(-PveneraReferenceDir="$reference" -PveneraCommunityFixtureDir="$fixtures")

./gradlew --offline :shared:data:jvmTest --tests '*SourceFeedServiceTest*' "${args[@]}"
./gradlew --offline :shared:data:jvmTest --tests '*SourceFeedServiceTest*' \
  --tests '*SourceDiscoveryServiceTest*' "${args[@]}"
./gradlew --offline :shared:source-runtime:jvmTest --tests '*VeneraKtorTransportTest*' \
  --tests '*SourceSessionTest*' "${args[@]}"
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

Feed、源发现和传输层定向回归均 EXIT 0。Android Debug、桌面 classes 和 iOS 模拟器 Framework 构建均 EXIT 0。iOS 模拟器运行时测试 EXIT 0，58 例通过。

五模块 JVM 全量回归连续两遍强制重跑，均 EXIT 0。两遍均无失败或错误。data 模块跳过的 4 例为单独执行的公网用例。

| JVM 模块 | 第一遍 | 第二遍 |
| --- | --- | --- |
| `domain` | 13 通过，0 跳过 | 13 通过，0 跳过 |
| `data` | 127 通过，4 跳过 | 127 通过，4 跳过 |
| `source-runtime` | 65 通过，0 跳过 | 65 通过，0 跳过 |
| `features` | 101 通过，0 跳过 | 101 通过，0 跳过 |
| `app` | 326 通过，0 跳过 | 326 通过，0 跳过 |
| 合计 | 632 通过，4 跳过 | 632 通过，4 跳过 |

| 夹具或测试 | 断言 |
| --- | --- |
| `SourceFeedServiceTest` | 初始顺序、只重排显示项、禁用留存、同名卡片跨源隔离、真实安装升级后的增减与分页变化、重开读取。 |
| `SourceDiscoveryServiceTest` | 固定排序、两个组与组编辑、来源默认状态、隐藏、每源手动 NSFW 和全局开关、Feed 及搜索候选过滤、重开恢复。 |
| `VeneraKtorTransportTest`、`SourceSessionTest` | 302 跨主机跟随、默认和混合大小写显式 UA 各自保持，普通头与最终响应保持。Cookie 同源交接、跨源隔离及重启继续通过。 |
| `CommunitySourceJourneyTest`、`CommunityHtmlCompatibilityTest` | 六份原始社区脚本实跑。四条端到端固定夹具覆盖安装、搜索、读图、下载和重启，两条 HTML 夹具覆盖搜索、目录、详情和设置动作。 |
| Venera 最小示例 | 原版 SHA 及搜索、详情、章节、正文/缩略图、标签回调链通过。data 层继续断言页序、Referer、字节和重启续读。 |

| 固定脚本 | SHA-256 |
| --- | --- |
| `minimal_source.js` | `41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d` |
| `baozi.js` | `6ccb52e7734e54c6dfcfcb9729adb46409f1e5c9d83bbda54313b066013e4255` |
| `mxs.js` | `da2c222eda825b77eb3b67ac09ec33b70bc2512cd5fc867fbe9b958df55037f8` |
| `manhuagui.js` | `a5bf6c633e0ce728361dded097424b829134d618128f72b571e42525e8950128` |
| `komga.js` | `2aa5b4e7d4b32bf282f86168b22af091d7436f4e3ad603defb7b6df738a36908` |
| `comic_walker.js` | `d039289f0aacda36990f090f93779bf54116e4d0e20c626c7ac29c73ec1e3513` |
| `manga_dex.js` | `c8beb84262da8fa3922ff0392fc2f79f03ee36ae08933d10571172ef6c8ce21d` |

## 主 agent 桥接

- Feed 页从 `ExtensionApp.kt` 注入 `session.feed`。加载卡片调 `feedItems()`，获取 `sourceKey`、`sourceName`、`title`、`type`、`index`、`numbered`、`cursor` 和稳定 `id`。内容加载继续用 `session.service.browse.explore`，以当前快照的索引构造 `SourceExplorePage`。进入配置使用 `feedSettings()`，勾选和拖动调 `setEnabled(id, enabled)`、`reorder(order)`。拖动可传部分卡片，未传项保持位置。
- 来源列表调用 `session.discovery.sources(groupId = ...)`，用 `pinned`、`groupIds` 和 `defaultSourceKey` 绘制固定与分类。管理页调用带 `includeFiltered = true` 的查询，接 `setPinned`、`setHidden`、`setSourceGroups`、组增改删、`setDefaultSource`、`setNsfw` 和 `setShowNsfw`。
- 已核对弹性任务。现有 `GlobalSearchSession` 通过 `GlobalSearchService.searchOne` 调用 `BrowseService.searchWorks`，生产注入的 `VeneraBrowseService` 已走 `session.service.browse.search`。该通道用源默认选项或用户已保存的当前选择，调用者无需为各源构建选项。接线时在 `GlobalSearchService.sources()` 把 `loadSnapshot().sources` 与 `session.discovery.searchSourceKeys()` 取交集，在单源重试前同样复核当前资格。

下一轮可沿任务书排期继续备份与恢复、AniList 追踪，以及更新调度的平台挂载。
