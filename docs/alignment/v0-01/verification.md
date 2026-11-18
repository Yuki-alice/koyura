# V0-01 verification · 五类基线可复现记录 (基线快照, 未宣布通过)

任务: V0-01 统一页面状态与证据manifest。允许范围: `docs/alignment`, `docs` (纯文档, 未改业务实现)。
基线: 项目HEAD `4f82b71` (含98行未提交改动, 详见manifest), 原版 `290267d`, Venera `c0c4597`。
本文件登记**实际产生**的证据与**空缺 (pending)**, 缺原版运行证据的项明确待补, 不据此宣布V0通过。

## 1. 本次实际产生的证据 (本ask内新写)

- `docs/alignment/v0-01/manifest.json` — 五类 (书架/浏览/详情/历史/阅读器) 基线manifest, 字段按路线图7.2节
  (两侧commit/未提交摘要、构建标识、夹具描述+unifiedHash pending、页面入口、状态ID、画布dp、density、
  字体/语言、主题/种子、设备/系统、insets、动画采样)。原版运行项全部记为pending。
- `docs/alignment/v0-01/verification.md` — 本文件 (可复现记录与待补清单)。
- `docs/alignment/execution-state.md` — 目标/HEAD摘要/当前任务/队列/证据/待解决/恢复指令。

以上三份为本次实际落盘。其余路径均为历史证据引用, 未新拍。

## 2. 历史证据引用 (非本次产生, 仅登记现状)

| 类 | 现状历史截图/报告 | 条件 (与两侧一致性说明) |
| --- | --- | --- |
| 外壳+浏览+详情+历史 | `docs/alignment/a-shell/screenshots/` 27张: `a-shell/a-browse-sources/a-browse-extensions/a-detail/a-history` × `{420,1280}-{light,dark}` + `a-state-*` 7张 | JVM `ImageComposeScene` 离屏, `Density(1f)`, `ReaderTheme(darkTheme=…)` 切换, 语言zh_CN; 420窄走底栏/1280宽走导航轨。原版设备实拍未做 (a-shell README: 无可用原版Android设备, 以`383ffaa`源码数值对照; 现统一基线为`290267d`, 需重核差异项见路线图2.2)。420≠360配对, 保留为回归扩展。 |
| 外壳四形态+浏览两页 | `docs/alignment/p1-shell/screenshots/` 12张: `p1-shell/p1-browse-sources/p1-browse-extensions` × `{420,1280}-{light,dark}` | 同上JVM离屏; 对照`383ffaa816` +白皮书03/04章; 形态AUTOMATIC (竖700/横600) 四模式 (komikku-alignment.md P1节)。 |
| 阅读器Viewer | `docs/alignment/b-viewer/shots/` 17张 + `compare/` 12组 + `rec/flow-*.mp4` + `frames/` 14帧 | 独立宿主 `ViewerFixture` 两章夹具 (第2章7张编号页) + Skia解码; `compare/` 右侧为原版行为示意 (源码推导), 已在b-viewer README标注性质, 不得改名充当原版截图/录屏。 |
| 阅读器Chrome | `docs/alignment/c-chrome/README.md` 仅文字登记; `build/komikku-c/` 29张为gitignored临时输出 (本ask实测29个文件存在, `git check-ignore`命中`.gitignore:6:**/build/`, `git status`无记录), **未入库、不计为current, pending归档至`docs/alignment/c-chrome/`** | 独立宿主 `ChromeTestHost` + 真实文件存储 (`ChromeSettingsHostTest`); JVM无录屏能力, 以流程截图序列代替, 真机录屏待主agent集成验收。 |
| 先前回归记录 | 路线图10节: `design-system/features/app`三模块JVM回归 `BUILD SUCCESSFUL`; 功能差距审阅: domain/features/data/source-runtime四模块232项0失败, 但`SourceScreens.kt:694 DSL_SCOPE_VIOLATION`曾阻断`:shared:app:jvmTest` | 本次未跑任何gradle (按任务要求脚本统一执行); 历史XML/日志 (`/tmp/visual-roadmap-jvm-20261118.log` 等为本机临时文件) 不计为本次通过。 |

## 3. 可复现步骤 (现状侧历史宿主, 本次未执行)

按下述历史命令与宿主可重放现状截图/测试; 本ask未执行, 记为未跑, 由脚本统一执行:

```bash
JAVA_HOME="$HOME/devtools/jdk-21" ./gradlew \
  :shared:design-system:jvmTest :shared:features:jvmTest :shared:app:jvmTest \
  -PveneraReferenceDir="$PWD/reference/Venera-Next" --console=plain
```

- 外壳/浏览/详情/历史离屏: `ShellScreenshotsTest` (`shared/app/src/jvmTest/kotlin/ShellScreenshotsTest.kt:38-69`,
  `-PreaderScreenshotDir=build/komikku-a`, `Density(1f)`, 420/1280×浅/深)。
- 浏览宽窄: `BrowseScreenshotTest` (`shared/app/src/jvmTest/kotlin/browse/BrowseScreenshotTest.kt:30-57`,
  窄420x760/宽1100x760)。
- 生产链路: `VeneraBrowseFlowTest` (仓库预览→安装→搜索→详情→章节)、`VeneraHistoryFlowTest`
  (阅读到第3页→历史离屏→继续→返回)、`AppShellStateRetentionTest` (719→720dp保留)。
- 阅读器Viewer: `shared/app/src/jvmTest/kotlin/reader/viewer/` (`ViewerFixture` + `ComicViewerTest` +
  `ComicViewerScreenshotTest` + `FlowFramesTest` + `OriginalReferenceShotsTest`)。
- 阅读器Chrome: `shared/app/src/jvmTest/kotlin/reader/chrome/` (`ChromeTestHost`, `ChromeBehaviorTest`,
  `ChromeDialogLayoutTest`, `ChromeScreenshots` → `build/komikku-c/` (gitignored临时输出, 归档至`docs/alignment/c-chrome/`前不计为current), `ChromeSettingsHostTest`)。
- 同一无状态Content服务生产预览: 现状为同一`LibraryScreen`/`BrowseScreen`/`HistoryScreen`/
  `ComicViewer`/`ReaderChrome`组件同时服务生产装配 (`AppScaffold`/`LibraryTabContent`/
  `ComicReaderScreen`) 与上述Fake/Fixture截图宿主 (CompositionLocal注入Fake服务), 不为每状态复制页面。
  V0统一夹具 (同封面/章节/计数/日期/阅读图) 尚未落地, 故本次只登记该复用关系, 未新增同Content预览截图。

## 4. 空缺清单 (pending, 判V0未达成)

1. 原版可复现构建/安装路径、APK SHA、设备/API/density/字体比例/语言/主题/系统栏方式: 全部pending。
   白皮书同版截图仅在数据/主题/字体/画布匹配时复用; 缺失状态 (书架真实内容、浏览Feed/全局搜索、
   详情封面主题、历史多选、阅读器五模式真机) 待补采。`reference/komikku实机截图/` 29张仅作氛围参照,
   不作为配对证据。
2. 目标画布 `360×720` 手机 / `1280×800` 平板成对图: pending。历史420/1280图保留, 不冒充360配对。
3. 统一数据夹具 (同封面/章节标题/计数/日期/阅读图, 本地资源+受控网络响应, 双适配器同可见数据):
   pending; `unifiedHash` 待计算SHA-256。
4. 每页实际入口/主页导航显隐/状态集合/宽窄布局索引: 本次以manifest登记现状调用链
   (`AppScaffold.kt:81-164` 导航显隐; `TabletUiMode.kt:36-55` 形态判定;
   `LibraryScreen.kt:16-31/55-...` 书架三态; `BrowseScreen.kt`/`SourceScreens.kt`/`HistoryScreen.kt`/
   `ReaderScreen.kt:1-60` + `chrome/ReaderChrome.kt:121` + `viewer/ComicViewer.kt:1-50`),
   但旧文档"占位/未接线"是否过时 (如浏览Feed/迁移、更新/更多占位) 仍需逐页复核, 本次未逐项关闭。
5. 已提交/未提交/历史截图分离标注: 本次manifest已标注HEAD脏 (98行) 与历史截图归属;
   `reference/` 目录本次无修改 (`reference/komikku` clean), 但工作树未提交业务改动仍待脚本验收后提交。
6. `c-chrome` 29张仅存于gitignored临时路径`build/komikku-c/` (未归档至`docs/alignment/c-chrome/`前不计为current)、Viewer原版侧仅示意、b-viewer `rec/*.mp4`非真机录屏: pending归档/补采。
7. 三端构建运行、真实操作录屏、性能基线 (200项长列表/高分辨率跨页/长图/坏图/峰值内存/帧时间):
   本次均未做, 按路线图7.4/7.5由脚本统一执行。

## 5. 差异清单 (引用路线图3.2, 不复测)

G01–G13仍stillOpen (任务输入), 其中G13即本任务补的证据口径。
实现差异以原版文件与现状落点对照为准, 详见manifest `pages[].original/current` 与 `sharedCapabilities`;
旧功能审阅结论不直接沿用 (如书架隐藏分类、标记已读、七开关持久化等已在未提交改动中变化,
以当前调用链+测试为准, 见 `docs/reviews/2026-11-18-functional-gap.md`)。

## 6. 本次未做的事 (如实记录)

- 未跑全量gradle门与三端构建 (任务第7条要求脚本统一执行)。
- 未新增/修改业务实现与测试 (允许范围仅`docs/alignment,docs`)。
- 未删除失败用例或降低断言。
- 未伪造原版运行截图/录屏与通过状态; 所有空缺记为pending。

## 7. 引用

- 路线图V0出口与7.2字段: `docs/frontend-alignment-roadmap.md:166-178,361-378`。
- 任务约束 (先只读、复用令牌/组件/服务/状态契约、同Content预览、落盘execution-state与<task-id>/manifest+verification、
  不删用例降断言、不自跑全量门): 本ask正文第1–7条。
- 原版入口: `reference/komikku/.../tachiyomi/ui/home/HomeScreen.kt:1-80`,
  `presentation/browse/SourcesScreen.kt:1-50`, `presentation/manga/MangaScreen.kt:1-60`,
  `presentation/history/HistoryScreen.kt:1-60`,
  `tachiyomi/ui/reader/viewer/pager/PagerConfig.kt:1-60`,
  `presentation/reader/appbars/ReaderAppBars.kt:1-60`,
  `tachiyomi/ui/library/LibraryTab.kt:1-50`。
- 现状调用链: `shared/app/.../AppScaffold.kt:81-164`,
  `shared/app/.../navigation/TabletUiMode.kt:13-55`,
  `shared/features/.../library/LibraryScreen.kt:1-80,580+`,
  `shared/features/.../library/LibrarySession.kt:15-60`,
  `shared/app/.../library/LibraryTab.kt:26-70`,
  `shared/app/.../browse/BrowseScreen.kt:1-70`,
  `shared/app/.../browse/SourceScreens.kt:1-60`,
  `shared/app/.../history/HistoryScreen.kt:1-60`,
  `shared/features/.../reader/comic/ReaderScreen.kt:1-60`,
  `shared/features/.../reader/comic/chrome/ReaderChrome.kt:1-60,121+`,
  `shared/features/.../reader/comic/viewer/ComicViewer.kt:1-50`,
  `shared/design-system/.../DesignTokens.kt:13-80`,
  `shared/design-system/.../Covers.kt:24-55`。
- 历史证据: `docs/alignment/a-shell/README.md`, `docs/alignment/b-viewer/README.md`,
  `docs/alignment/c-chrome/README.md`, `docs/alignment/p1-shell/screenshots/`,
  `docs/reviews/2026-11-18-functional-gap.md`, `docs/komikku-alignment.md:60-70`。
- 检查命令与输出: `git rev-parse HEAD` → `4f82b71ac4e95df00461f8d83060a5e977f5b96a`;
  `git -C reference/komikku rev-parse HEAD` → `290267dd4c99f5b46812240fb0e4bc6b3ee8c562`;
  `git status --porcelain | wc -l` → `100` (本ask实测: 98行任务前已存在改动 + 2行本次V0-01新增 `?? docs/alignment/execution-state.md` 与 `?? docs/alignment/v0-01/`); `git -C reference/komikku status --porcelain` → 空 (clean)。
  gradle门: 本次未跑 (not run)。
