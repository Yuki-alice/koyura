# 统一数据夹具规范 · V0-04

依据：`docs/frontend-alignment-roadmap.md` §5 V0 第 3 项（固定相同封面、章节标题、
计数、日期与阅读图片；本地资源 + 受控网络响应；原版若需另一个来源适配器，
两个适配器输出相同可见数据）。本文件只定义口径与盘点现状，不改业务代码与测试；
首个 `unifiedHash` 值与双适配器并跑验证记 pending（需跑 gradle 门，本 ask 未跑）。

HEAD：`4f82b71ac4e95df00461f8e5d83060a5e977f5b96a`（实测 `git rev-parse HEAD`）。

## 1. 五类统一字段（可见口径）

| # | 字段 | 统一值（目标） | Fake 侧现状 | Venera 受控侧现状 | 缺口 |
|---|------|---------------|------------|-------------------|------|
| F1 | 封面图 | 同一可解码图片字节（见 §2） | `FakeBrowseService.coverBytes` 按 `(sourceKey, coverUrl)` 供字节（`shared/app/src/commonTest/kotlin/browse/FakeBrowseService.kt:156-166`）；书架 `FakeLibraryRepository` 三态：正常 URL→`thumbnailBytes`、损坏 URL→`thumbnailFailures` 抛错、空串→抛错（`shared/features/src/commonMain/kotlin/library/FakeLibraryRepository.kt:25-32,189-198`）。但截图宿主多用 `cover = ""`（占位路径），见 `shared/app/src/jvmTest/kotlin/ShellScreenshotsTest.kt:76-77,87` | `VeneraTestFixtures.searchJson/detailJson` 的 cover 均为 `https://example.invalid/cover.jpg`（`shared/app/src/jvmTest/kotlin/browse/venera/VeneraTestFixtures.kt:47-55`）；图片字节仅部分流提供（如 `VeneraLibraryProductionRestartTest.kt:423` 的 `/cover.jpg` 路由，`VeneraHistoryFlowTest.kt:417-425` 的 `/images/*.png` 纯色块） | Fake 截图宿主 `cover=""` 与 Venera `cover.jpg` 可见不一致；书架 `thumbnailBytes` 是 ASCII 占位串（`"bytes-a1"`，`FakeLibraryRepository.kt:25-28`），不可解码；`LibraryScreenTest.kt:149` 用运行时 `pngBytes(0x336699)` 4×4 块。统一可解码封面文件记 pending |
| F2 | 章节标题 | 同一标题表（目标：`["第一章","第二章"]` 倒序展示，见 §4） | 浏览截图宿主：`ep_3 第三章 / ep_2 第二章 / ep_1 第一章`（`ShellScreenshotsTest.kt:90-94`）；历史行：`第二章`（`ShellScreenshotsTest.kt:104`）；Chrome 宿主：`ep_1 第一章 / ep_2 第二章`（`shared/app/src/jvmTest/kotlin/reader/chrome/ChromeScreenshots.kt:36-47`） | `detailJson`：`[{"id":"2","title":"第二章"},{"id":"1","title":"第一章"}]`（`VeneraTestFixtures.kt:54`）；`VeneraBrowseFlowTest.kt:93-96` 另有英文分组表（`Chapter Two/One/Extra`，仅分组语义用例） | Viewer 固定夹具用英文标题 `Chapter 1/2`（`shared/app/src/jvmTest/kotlin/reader/viewer/ViewerFixture.kt:46,52`），与其他宿主中文标题不一致，待统一 |
| F3 | 计数 | 章节数 / 总页数 / 当前页 / 未读数同一数字 | 详情 3 章（上）；Chrome `pageCount = 3, pageIndex = 2`（`ChromeScreenshots.kt:41`）；书架 `totalChapters/unreadCount/downloadedCount`（如 w1 `10/3/2`，`FakeLibraryRepository.kt:211-218`） | 搜索 `totalPages: 3`（`VeneraTestFixtures.kt:48`）；章节 `/chapters/2` 返回 3 图（`VeneraHistoryFlowTest.kt:414-416`）；断言 `page_index = 2`（`VeneraHistoryFlowTest.kt:130-132`） | 书架计数（10/8/20/5/1/12/1）与浏览/阅读计数（3）本就描述不同作品，并非不一致，但统一基线需为**同一作品**固定一套计数，记 pending |
| F4 | 日期 | 同一 `readAtMillis` 与同一 `updatedOn` | `historyEntry` 默认 `readAtMillis = 1_794_916_800_000`（`shared/app/src/commonTest/kotlin/history/FakeHistoryService.kt:38-58`）；`HistoryScreenStateTest` 用 `1_794_916_800_100/000` 固定值（`HistoryScreenStateTest.kt:53-56`） | `detailJson.updatedOn = "2026-09-01"` 固定（`VeneraTestFixtures.kt:53`）；`HistoryScreenshotsTest` 用 `now/now-1d/now-2d` 动态值（`HistoryScreenshotsTest.kt:83-113`）；data 层种子固定 `1_700_000_000_000`（`LibraryFixture.kt:60`） | 动态 `now` 截图不可复现日期分组；三处固定值互不相同。统一日期记 pending（建议：固定 `readAt` + 固定 `updatedOn`，截图宿主禁用 `now`） |
| F5 | 阅读图 | 同一页图字节（页数、尺寸、顺序一致） | Viewer 固定两章：ep_1 三张 400×600 竖图（`1-1..1-3`），ep_2 七张：q0 竖封面、q1 800×400 横跨页、q2 竖、q3 400×1200 长条、q4 竖、q5 首载失败后重试、q6 竖尾（`ViewerFixture.kt:28-61`）；另有裁剪两页 cb0/cb1（`:66-87`）。全部运行时 `numberedPng/framedPng/cornerMarkedPng` 生成（`:91-177`），**非文件** | Venera 流用 4×4 纯色 `pngBytes`（`shared/app/src/jvmTest/kotlin/reader/TestImages.kt:4-13`；`VeneraHistoryFlowTest.kt:419-424` 按 URL 分色），data 层用非图片 opaque 字节 `byteArrayOf(1)/(2,3)/(4,5,6)`（`ComicReadingFixtureTest.kt:63-68`） | 两侧页图语义完全不同（编号/尺寸/失败页 vs 纯色块 vs 非图片字节）；`ComicViewerScreenshotTest` 的 `mode/layout cur/total` 叠字标签（`:74-78`）依赖 Viewer 七页结构。统一阅读图集（编号页/横跨页/长图/损坏页/两章，见 roadmap §7 性能节）记 pending |

## 2. 本地资源路径（已存在文件的实测 SHA 见 `fixture-manifest.json`）

- `shared/app/src/jvmTest/resources/venera/minimal_source.js`（3034 B）——Venera 受控脚本源，`VeneraFixtures.upstreamScriptBytes()` 读取并断言 SHA（`VeneraTestFixtures.kt:31-39`）。
- `shared/data/src/jvmTest/resources/venera/library_minimal_source.js`（3034 B）——实测与上者**逐字节一致**（同 SHA `41ff3cdc…8459d`，与 `ExpectedScriptSha256` 及 `ComicReadingFixtureTest.kt:42-43`、`ExtensionLifecycleFixtureTest.kt:37-38` 断言一致）。
- `CoverFixtureUiTest.kt:31-35` 内联 1×1 PNG（base64，解码 70 B，实测 SHA 见 manifest `inline/cover-ok-1x1`）与 `byteArrayOf(0,1,2,3)` 损坏字节——三态夹具（正常/损坏/空，`:23-29`），无对应文件。
- `shared/media/fixtures/txt/` 下 `.txt` 夹具 12 个（另有 `generate_fixtures.py` 生成器 1 个）与 `shared/media/fixtures/epub/*.epub`（30 个，另有同目录生成器 1 个）——小说（TXT/EPUB）阅读器夹具，由 `FixtureRoot`（`shared/media/src/commonTest/kotlin/FixtureRoot.kt:1-13`，expect/actual 经构建注入路径）定位；属 V0 五类漫画基线**范围外邻接项**，哈希照录以备查，`unifiedHash` 不含它们。
- 运行时生成（无文件、无 SHA，manifest 记 `pending/generated`）：`ViewerFixture` 全部页图、`LibraryScreenTest` 的 `pngBytes` 封面、Venera 流的纯色页图、`FakeLibraryRepository` 的 `"bytes-a1"/"bytes-twins"` ASCII 串。

## 3. 受控网络响应要点（MockEngine 路由契约）

1. `/index.json` → `catalogJson()`（`VeneraTestFixtures.kt:41-44`），支持 `indexFailures` 容错注入（`:60-73`）。
2. `/sources/example.js` → 脚本字节（`:75-76`），data 层按 `reader.venera.example` 系统属性优先取外部检出（`ComicReadingFixtureTest.kt:36-43`），缺省跳过（`assumeTrue`）。
3. `/api/search` → `searchJson()`（F1/F2 行）；`/api/comics/demo` → `detailJson()`（F2/F4 行）；`/api/comics/demo/chapters/2` → `{"images":[…]}` 3 图（`VeneraHistoryFlowTest.kt:414-416`）。
4. 图片路由断言 `Referer: https://example.invalid/`（`VeneraHistoryFlowTest.kt:418`；脚本 `onImageLoad/onThumbnailLoad` 回调，`minimal_source.js:71-80`）。
5. 历史写入断言：`page_index/page_key/read_at/work_title/chapter_title` 全列（`VeneraHistoryFlowTest.kt:127-137,370-379`）；pre-metadata 老库（无 `read_at/work_title/chapter_title` 列）保持可见并明确报错（`:141-172`）。

## 4. 双适配器同可见数据要求

- 定义：同一统一作品（建议 `example_source/demo`《星图》作者“作者”）经 Fake 适配器（`FakeBrowseService` + `FakeHistoryService` + `FakeLibraryRepository` + `FakeReaderService`/`demoReaderService`）与 Venera 生产适配器（`VeneraBrowseService` + 真实 `ExtensionService`/`HistoryStore`/`ComicReadingService` + MockEngine）输出的**可见字符串、计数、日期分组、图片像素**必须一致；允许传输形态不同（内存快照 vs SQLite + MockEngine），不允许可见差异。
- 现状不一致清单（均为本 ask 只读源码所得，改动留给后续任务）：
  1. 封面：Fake 截图宿主多为空串占位，Venera 侧为 `cover.jpg`（§1 F1）。
  2. Viewer 章节英文标题 vs 全网中文标题（§1 F2）。
  3. 日期三套固定值 + 截图动态 `now`（§1 F4）。
  4. 页图三套互不相同的字节（§1 F5）。
  5. `LibraryFixture` 契约注记：规范形状要求落在 `shared/app/…/library/LibraryFixture.kt`（app 路径，归主 agent/A 轨），data 侧实现为规范内容（`LibraryFixture.kt:17-19`）——转存/复出口待集成负责人执行，本任务仅记录。
- Chrome 宿主（`ChromeTestHost.kt:22-26`，`menuVisible/pageIndex/pageCount` 状态机）与截图（`ChromeScreenshots.kt`）无独立数据源，其 `星图/ep_2/3页` 必须随统一值联动，不得再硬编码分叉（当前 `pageCount = 3` 与 Venera ep_2 恰好一致，属巧合而非契约）。

## 5. `unifiedHash` 计算口径（已定义，首值 pending）

- 输入：§1 五类统一字段的**可见规范形** JSON：`{cover:{sha256,bytes}, chapters:[{id,title}], counts:{chapters,pages,pageIndex,unread,totalPages}, dates:{readAtMillis,updatedOn}, pages:[{key,sha256,w,h}]}`；键排序、UTF-8、无空白、数组保序。
- 算法：`SHA-256(规范形字节)`，小写 hex。
- 判定：Fake 侧输入与 Venera 侧解码后输入分别求值，两值相等即双适配器同可见；任一宿主改动统一字段即更新 manifest 并重跑门。
- 状态：**pending**——统一值本身尚未落定（§1 缺口），且计算需跑代码（本 ask 依约束不自跑 gradle 门，见 `execution-state.md` §7 恢复指令）。此处只定口径，不编造首值。

## 6. 宿主盘点索引（位置 → 覆盖 → 缺口速查）

| 宿主文件 | 覆盖字段 | 缺口 |
|---|---|---|
| `shared/app/src/jvmTest/kotlin/reader/viewer/ViewerFixture.kt`（+ `ComicViewerScreenshotTest.kt` 七页同数据同画布，`:31-38`） | F2（英文）/F3 页计数/F5 七页+裁剪两页 | 无封面、无日期、英文标题、运行时生成无 SHA |
| `shared/features/src/commonMain/kotlin/library/FakeLibraryRepository.kt` + `libraryFixtureSnapshot()` | F1 三态/F3 计数全套/日期合成值 | 封面不可解码、日期非日历值 |
| `shared/data/src/commonTest/kotlin/library/LibraryFixture.kt`（种子契约） | 分类/同名/隐藏/空分类/chapterless + 固定 readAt | 封面 URL 无字节、app 路径转存待办 |
| `shared/app/src/commonTest/kotlin/browse/FakeBrowseService.kt`（defaults）+ `ShellScreenshotsTest.kt` demo 接线 | F2 中文标题/F3 章节 3 | 截图 cover 全空串 |
| `shared/app/src/jvmTest/kotlin/browse/venera/VeneraTestFixtures.kt` + 各 `Venera*Flow/Restart` 测试 | F1–F5 受控全链路 | 页图为色块、部分流才供 cover 字节 |
| `shared/app/src/commonTest/kotlin/history/FakeHistoryService.kt` + `HistoryScreenshotsTest.kt` 种子 | F2/F4（固定或动态） | cover 恒空、截图用动态 now |
| `shared/app/src/jvmTest/kotlin/reader/chrome/ChromeTestHost.kt` + `ChromeScreenshots.kt` | F2/F3（状态硬编码） | 无独立数据、与统一值无契约绑定 |
| `shared/app/src/jvmTest/kotlin/browse/CoverFixtureUiTest.kt` + `AppShellDetailUiTest.kt` | F1 三态（1×1 真图/坏字节/空） | 1×1 图无代表性、无共享文件 |
| `shared/data/src/jvmTest/kotlin/reading/ComicReadingFixtureTest.kt`、`extension/ExtensionLifecycleFixtureTest.kt` | 未改动脚本门（SHA 断言）+ 路由 | 图片为 opaque 字节、外部检出缺省跳过 |
| `shared/media/fixtures/**`（TXT/EPUB） | 范围外邻接：小说夹具自洽 | 不纳入 unifiedHash（见 §2） |
