# 引擎轨交付报告 · 第四轮

## 改动清单

| 模块和文件 | 交付 |
| --- | --- |
| `SourceSearch.kt`、`SourceBrowseService.kt`、`SourceSearchOptionsParser.kt`、`SavedSearchStore.kt`、`SourceSearch.sq`、`10.sqm` | 暴露源搜索选项和当前值，按源持久化选择，保存搜索支持增删改查及按原快照复放。 |
| `ChapterDownloads.kt`、`ChapterDownloadStore.kt`、`DownloadJobs.sq`、`DownloadTriggers.kt` | 作品级批量入队、可选排除已读、一次查询整作状态，保存自动下载和向后下载偏好。 |
| `LibraryUpdates.kt`、`LibraryUpdateScanner.kt` | 新章事务提交后调用可选下载钩子。钩子失败单独记录，章节检测结果保留。 |
| `LibraryUpdateSchedule.kt`、`LibraryUpdateScheduler.kt`、`LibraryUpdateScanner.kt`、`ExtensionSession.kt` | common 调度、间隔及最后结果落库，启动和回前台补跑到期检查，发布窗口变更重算下次时间。关闭会话时停止调度。 |
| `ComputeHost.kt`、`HtmlBootstrap.kt`、`CommunitySourceJourneyTest.kt`、`shared/data/build.gradle.kts` | 兼容 ComicWalker 返回函数的计算脚本和 MangaDex 使用的 `innerHtml` 拼写，四条原始社区源走保存搜索、读图、下载、重开夹具及可选公网。 |

### 搜索协议和复放

固定版 Venera 将声明放在 `search.optionList`，每项用 `{label, type, options, default}`，选项从第一个连字符切分值和标签。无选项传空数组。普通项传裸字符串，下拉允许 `null`，多选传 JSON 数组字符串。单选缺省用首项，多选缺省为空数组。固定版 Dart 内部会编码显式默认值，JavaScript 边界以文档和原版脚本调用为准。页码从 1 起，游标初值为 `null`，两种回调并存时页码优先。 [Venera 搜索协议](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/api/comic_source.zh.md#3-搜索探索与分类) · [Venera 搜索解析器](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/lib/features/comic_source/source_search_parser.dart)

当前选项按源和声明一起存储，声明发生变化时回退到新默认值。保存搜索沿用 Komikku 的独立记录和名称，由 `sourceKey`、查询和有序选项快照组成。复放保存项使用原值，独立于后来更改的当前选项。脚本升级后如果改变旧参数含义，可编辑保存项重新保存。 [Komikku 保存搜索表](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/data/src/main/sqldelight/tachiyomi/data/saved_search.sq)

### 批量下载和调度

- `enqueueAll(sourceKey, workId, chapterIds, unreadOnly, limit)` 按输入顺序，在一个事务内过滤和入队。重复项复用原有 ID 和状态。需要重新下载失败项时调用原有 `retry`。空章号用于整作视为一章的源，统一映射到 `null`。`statusesForWork` 用一次查询返回已有作业的映射，缺少键表示该章没有作业。坏包在重开时收敛为 `FAILED`，随后不会计入已完成。
- 自动下载新章和向后下载默认关闭。第三方源缺少 Komikku 的通用章节编号，已读过滤以本地同源同作章节 ID 为准。向后下载使用调用者传入的阅读顺序，过滤已读后取 N 条。阅读达到 25% 和当前及下一章已缓存等前端触发条件留给阅读器桥接。 [Komikku 下载偏好](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/domain/src/main/java/tachiyomi/domain/download/service/DownloadPreferences.kt) · [Komikku 向后下载触发](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt#L699)
- 调度值域按任务书采用关闭、6、12、24 小时。固定版 Komikku UI 有 12、24 和更长档位，其中 6 小时为任务书指定档。间隔和最近结果存在 `library_prefs`，`wifiOnly` 默认开启并持久化，网络准入待平台接线。下次时间取间隔到期与最早符合其他智能条件作品的发布日开始中较早者。前台检查和计时共用一个协程循环，补跑一次后重排。源检查或下载钩子的局部失败会进入结果，调度继续。 [Komikku 更新间隔与设置入口](https://github.com/komikku-app/komikku/blob/290267dd4c99f5b46812240fb0e4bc6b3ee8c562/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsLibraryScreen.kt#L160)

## 验收

在 `core-engine` 工作树执行，Gradle 使用 JDK 21。

```bash
export JAVA_HOME="$HOME/devtools/jdk-21"
reference='/Users/kurisu/project x/reference/Venera-Next'
fixtures=build/community-scripts
mkdir -p "$fixtures"
for src in baozi mxs manhuagui komga comic_walker manga_dex; do
  curl -fsSL "https://raw.githubusercontent.com/venera-app/venera-configs/a04e05591f7832f7166d6660546613c114d6738a/$src.js" \
    -o "$fixtures/$src.js"
done
args=(-PveneraReferenceDir="$reference" -PveneraCommunityFixtureDir="$fixtures")

./gradlew --offline :shared:data:jvmTest --tests '*SavedSearchStoreTest*' "${args[@]}"
./gradlew --offline :shared:data:jvmTest --tests '*ChapterDownloadStoreTest*' --tests '*LibraryUpdateScannerTest*' "${args[@]}"
./gradlew --offline :shared:data:jvmTest --tests '*LibraryUpdateSchedulerTest*' --tests '*LibraryUpdateSmartPolicyTest*' "${args[@]}"
./gradlew --offline :shared:data:jvmTest --tests '*CommunitySourceJourneyTest' "${args[@]}"
./gradlew --offline :shared:data:jvmTest --tests '*CommunitySourceJourneyTest' -PveneraCommunityLive=true "${args[@]}"
./gradlew --offline :apps:android:assembleDebug "${args[@]}"
./gradlew --offline --continue :apps:android:assembleDebug :apps:desktop:classes \
  :shared:app:linkDebugFrameworkIosSimulatorArm64 "${args[@]}"
./gradlew --offline :shared:source-runtime:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test \
  --tests '*LibraryUpdateSchedulerTest*' "${args[@]}"
for pass in 1 2; do
  ./gradlew --offline \
    :shared:domain:jvmTest --rerun :shared:data:jvmTest --rerun \
    :shared:source-runtime:jvmTest --rerun :shared:features:jvmTest --rerun \
    :shared:app:jvmTest --rerun "${args[@]}" || exit $?
done
```

保存搜索、批量下载、智能更新和四条社区源固定夹具定向回归均 EXIT 0。交付前五模块 JVM 连续两遍强制重跑，均 EXIT 0。

| JVM 模块 | 第一遍通过 | 第二遍通过 |
| --- | ---: | ---: |
| `shared:domain` | 13 | 13 |
| `shared:data` | 125 | 125 |
| `shared:source-runtime` | 64 | 64 |
| `shared:features` | 101 | 101 |
| `shared:app` | 325 | 325 |
| 合计 | 628 | 628 |

每轮仅跳过 4 个需显式启用的公网站点用例。Venera 原始脚本全回调链及六个社区脚本固定夹具均实际运行。Android APK、桌面 classes、iOS 模拟器 framework 最终组合构建 EXIT 0。首次组合构建时 Android 使用了上一版 domain 中间产物，单独重建 Android 后完整三端命令通过。iOS 模拟器另外通过 57 例宿主测试和 3 例调度测试。

| 测试类 | 断言 |
| --- | --- |
| `SavedSearchStoreTest` | 无选项源、三种选项形态、含连字符标签、默认值、错误输入，重启后读取与按保存项复放，页码优先和游标初值，CRUD 与跨源隔离。 |
| `ChapterDownloadStoreTest` | 批量去重、已读过滤、章节缺省键、顺序与限量、整作状态读取、偏好重开、坏包状态和完成数。 |
| `LibraryUpdateScannerTest` | 新章事务提交后触发钩子，失败源、旧章和重试路径继续回归。 |
| `LibraryUpdateSchedulerTest` | 虚拟时钟触发、间隔变更、关闭、重启和多次前台唤醒只补跑一次、局部取消不结束调度、发布时间变化。 |
| `CommunitySourceJourneyTest` | 每条脚本核对完整 SHA，走保存搜索和重启。ComicWalker 比对计算后的字节，MangaDex 模拟网页登录并核对论坛 Cookie、正文、页码及失败重取。 |

| 原始脚本 | SHA-256 | 结果 |
| --- | --- | --- |
| [Venera 最小示例](https://github.com/CyrilPeng/Venera-Next/blob/c0c45979d453c2a8740c45f08e95997c90717629/doc/examples/minimal_source.js) | `41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d` | 搜索、详情、章节、正文/缩略图及标签回调。data 回归继续校验 Referer、页序、字节及重开。 |
| [包子漫画](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/baozi.js) | `6ccb52e7734e54c6dfcfcb9729adb46409f1e5c9d83bbda54313b066013e4255` | 搜索、详情、章节固定 HTML 夹具。 |
| [漫小肆](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/mxs.js) | `da2c222eda825b77eb3b67ac09ec33b70bc2512cd5fc867fbe9b958df55037f8` | 搜索、详情、章节及设置加载取消夹具。 |
| [漫画柜](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/manhuagui.js) | `a5bf6c633e0ce728361dded097424b829134d618128f72b571e42525e8950128` | 固定夹具通过。公网通过安装、保存搜索、详情、WebP、23 页下载和断网重启续读。 |
| [Komga](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/komga.js) | `2aa5b4e7d4b32bf282f86168b22af091d7436f4e3ad603defb7b6df738a36908` | 固定夹具通过。官方演示公网通过原版表单、探索、保存搜索、取图、35 页下载和断网重启续读。 |
| [ComicWalker](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/comic_walker.js) | `d039289f0aacda36990f090f93779bf54116e4d0e20c626c7ac29c73ec1e3513` | 固定夹具通过令牌转发、计算回调、字节比对、2 页落盘和离线恢复。公网安装完成，搜索解析遇到以 `<` 开头的响应，未到章节。 |
| [MangaDex](https://github.com/venera-app/venera-configs/blob/a04e05591f7832f7166d6660546613c114d6738a/manga_dex.js) | `c8beb84262da8fa3922ff0392fc2f79f03ee36ae08933d10571172ef6c8ce21d` | 固定夹具通过安全评级选项、网页登录交接、评论正文、图片失败重取、2 页下载和离线恢复。公网安装完成，搜索返回 HTTP 200 `text/html`，JSON 解析中断，未到论坛和章节。 |

公网组合运行共 8 例，6 例通过、2 例遇到上述公网阻断，命令 EXIT 1。带现有宿主 User-Agent 的独立请求复核了 MangaDex 的 302 挑战重定向和最终 HTML。ComicWalker 原版回调未保留 HTTP 状态，记录为响应内容不符。两条阻断源的固定夹具完整通过。 [Komga 官方演示](https://komga.org/docs/introduction/#demo)

## 主 agent 桥接

- `shared/app/src/commonMain/kotlin/ExtensionApp.kt` 注入 `session.service.browse`。在 `browse/venera/VeneraBrowseService.kt` 用 `searchOptions`、`setSearchOptions`、`savedSearches`、`saveSearch`、`search` 和 `searchSaved` 接源详情搜索和次页。当前生产桥仍直接向脚本传空选项。
- 作品详情和阅读器从 `session.downloads` 调 `enqueueAll` 和 `statusesForWork`。更新装配调用 `session.updates.setNewChaptersDownloadHook(session.downloadTriggers)`，向后下载由 `session.downloadTriggers.enqueueNext` 处理。用 `UpdateScan.downloadTriggerFailures` 显示下载入队异常。
- `ExtensionApp.kt` 获取应用会话生命周期作用域后调用 `session.updateScheduler.start(scope)`，在平台前台恢复时调用 `onForeground()`，UI 观察 `state`。已有智能更新偏好继续走扫描器。间隔和仅 Wi-Fi 字段通过 `updateScheduler.setPreferences` 写入。WorkManager、iOS 系统后台唤醒和实际 Wi-Fi 约束由平台规划接入。

## Android / iOS 网页登录验收清单

本机查询时没有连接 Android 或 iOS 真机。双源生成命令已运行，索引字段和三个 JavaScript 片段已通过语法核验。以下构建与夹具检查可在模拟器走一遍，标为 **真机** 的项需在两个实体平台各完成一次。使用测试站点和授权测试账号。

### 安装并启动

```bash
# Android，实体设备已开启 USB 调试并授权这台电脑。
adb devices -l
adb -s "$ANDROID_SERIAL" shell dumpsys webviewupdate
adb -s "$ANDROID_SERIAL" install -r apps/android/build/outputs/apk/debug/android-debug.apk
adb -s "$ANDROID_SERIAL" shell am start -n app.koyura/app.koyura.android.MainActivity

# iOS 模拟器，先启动目标模拟器。
KMP_JAVA_HOME="$JAVA_HOME" xcodebuild -project apps/ios/Reader.xcodeproj -scheme Reader \
  -destination 'generic/platform=iOS Simulator' -configuration Debug \
  -derivedDataPath build/web-login-ios-sim CODE_SIGNING_ALLOWED=NO build
xcrun simctl install booted build/web-login-ios-sim/Build/Products/Debug-iphonesimulator/Reader.app
xcrun simctl launch booted app.koyura

# iOS 真机，先在 Xcode 选择已授权签名，并构建 Debug Reader.app。
xcrun devicectl list devices
xcrun devicectl device install app --device "$IOS_DEVICE" "$IOS_SIGNED_APP"
xcrun devicectl device process launch --device "$IOS_DEVICE" app.koyura
```

Android 被测系统 WebView 需支持 `MULTI_PROFILE` 与 `GET_COOKIE_INFO`。记录 `dumpsys webviewupdate` 的当前提供者版本。iOS 签名安装路径以本机 Xcode 生成的 `Reader.app` 为准。

### 生成双源夹具及检查步骤

将生成目录放到同一受控 HTTPS 静态地址，使用专用测试域名。先验证 `index.json`、两个脚本和 HTML 均返回 200，再从「浏览 → 扩展仓库」添加索引并安装 A、B。站点只写两个测试标记。

```bash
export WEB_LOGIN_QA_BASE='https://qa.example.test/web-login'
python3 - <<'PY'
from pathlib import Path
from urllib.parse import urlparse
import json
import os

base = os.environ["WEB_LOGIN_QA_BASE"].rstrip("/")
url = urlparse(base)
assert url.scheme == "https" and url.hostname and not (url.username or url.password or url.query or url.fragment)
out = Path("build/web-login-qa")
out.mkdir(parents=True, exist_ok=True)
index = []
for tag in ("A", "B"):
    key = f"web_login_probe_{tag.lower()}"
    name = f"Web login probe {tag}"
    file = f"{key}.js"
    index.append({"key": key, "name": name, "version": "1.0.0", "fileName": file})
    (out / file).write_text(f"""
class WebLoginProbe extends ComicSource {{
  name = {json.dumps(name)}; key = {json.dumps(key)}; version = "1.0.0"; minAppVersion = "1.0.0";
  base = {json.dumps(base)};
  probe = () => {{
    const cookie = Network.getCookies(this.base + "/probe.html").find(c => c.name === "web_login_probe");
    return "Cookie=" + (cookie?.value ?? "empty") + ", storage=" +
      (this.loadData("_localStorage")?.web_login_probe ?? "empty");
  }};
  account = {{
    loginWithWebview: {{
      url: this.base + "/probe.html?run={tag}",
      checkStatus: (url, title) => url === this.base + "/probe.html?run={tag}&done=1" && title === "Probe accepted"
    }},
    infoItems: [{{ title: "Captured state", data: this.probe }}],
    logout: () => Network.deleteCookies(this.base)
  }};
  search = {{ load: () => ({{ comics: [new Comic({{id: "state", title: this.probe()}})], maxPage: 1 }}) }};
}}
""", encoding="utf-8")
(out / "index.json").write_text(json.dumps(index), encoding="utf-8")
(out / "probe.html").write_text("""<!doctype html><meta charset="utf-8"><title>Probe start</title>
<p id="state"></p><button id="button">Finish test login</button>
<script>
const url = new URL(location.href);
const tag = url.searchParams.get("run");
if (url.searchParams.get("done") === "1") document.title = "Probe accepted";
document.querySelector("#state").textContent =
  document.cookie.includes("web_login_probe=") || localStorage.getItem("web_login_probe")
    ? "Previous markers found" : "Fresh web session";
document.querySelector("#button").onclick = () => {
  if (tag !== "A" && tag !== "B") return;
  document.cookie = "web_login_probe=" + tag + "; Path=/; Secure; SameSite=Lax";
  localStorage.setItem("web_login_probe", tag);
  url.searchParams.set("done", "1");
  location.replace(url);
};
</script>""", encoding="utf-8")
PY
```

| 步骤 | 操作与期望 |
| --- | --- |
| 打开和取消 | A 的源详情打开「账号 → 网页登录」。确认显示 `Fresh web session`，先用 Android 返回键或 iOS 取消按钮关闭，A 保持未登录。若源页提供搜索，搜索任意非空词时结果标题应为 `Cookie=empty, storage=empty`。 |
| 成功交接 | 再打开 A，点击 `Finish test login`。在 URL 和标题变更后原生窗口自行关闭，A 显示 `Cookie=A, storage=A`，重开应用后仍可读。**真机** 重复一次并在网页显示期间切到后台后返回，确认会话仍可完成。 |
| 源间隔离 | 打开同域名的 B，初始必须显示 `Fresh web session`。完成 B 后只显示 B 标记，回 A 仍只显示 A。**真机** 两端各重做，覆盖 Android 独立 profile 与 iOS 临时网站数据。 |
| 注销清理 | 注销 A，回源搜索检查 A 的两个值为空，B 仍已登录。Android 用 `adb -s "$ANDROID_SERIAL" shell am force-stop app.koyura` 再启动，iOS 从应用切换器结束并启动。**真机** 重启后检查 A 未登录、B 仍在，A 再次打开网页为全新会话。 |
| 实际站点 | **真机** 用具备网页登录的原始源和授权测试账号，按登录、取消、重定向、注销、重启的顺序检查。真实域名 Cookie、系统 WebView 或账户站点兼容性在这一步确认。只记录标记和状态，凭据与 Cookie 内容留在测试设备。 |

下一轮优先完成三处应用装配和双平台真机验收。新源公网复测保留脚本固定版本，记录实际状态码与内容类型，再判断是否需要调整传输层。
