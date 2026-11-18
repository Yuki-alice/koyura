# v0-05 verification · 目标画布基线重拍归档确认 (V0-05, 未宣布最终通过)

任务: V0-05 目标画布 360×720（手机）/ 1280×800（平板）基线重拍。
允许范围仅 `docs/alignment` 加 `shared/app/src/jvmTest`（仅加法）；
本ask增量（新文件 `V005BaselineCanvasesTest.kt` + `docs/alignment/v0-05/`）未改业务代码、
未动既有断言、未自跑全量 gradle 门（工作树级任务前已存删除/修改见§4，不在本ask增量内）。

## 1. 归档内容 (本 ask 实际落盘)

- `docs/alignment/v0-05/current/` — 20 个 PNG（`build/v0-05/` 的逐字节拷贝，
  本 ask 以 `cmp -s` 逐个比对：`identical=20 diff=0`）。
- `docs/alignment/v0-05/manifest.json` — 20 条目：文件名 / SHA-256 / 画布 /
  density / 主题 / 宿主 / 生成代码状态（`python3 json.load` 校验通过，count=20）。
- 本文件 — verification 记录。

## 2. 为什么新增用例而不是传参 (本 ask 已读代码核实)

- 输出目录可传参：`shared/app/build.gradle.kts:76`
  （`systemProperty("reader.screenshotDir", -PreaderScreenshotDir，默认 `build/screenshots`）`；
  本批以 `-PreaderScreenshotDir="$PWD/build/v0-05"` 指向新建目录，未覆盖既有输出。
- 画布不可传参：`ShellScreenshotsTest.kt:121-126,142-145`
  （`420/1280` 写死；注意该文件在本工作树为 `??` 未跟踪新文件，非已提交基线，
  其余三文件为已跟踪未修改，引用行号内容以本ask已读文件核对属实）、
  `BrowseScreenshotTest.kt:33,57`（`420x760/1100x760` 写死）、
  `HistoryScreenshotsTest.kt:43,58`（`420x780/1100x780` 写死）、
  `ReaderScreenshotTest.kt:95,123`（`420x780/1280x800` 写死，且 `ReaderTheme(darkTheme=false)` 写死）。
  故加法新增 `shared/app/src/jvmTest/kotlin/V005BaselineCanvasesTest.kt`
  （5 个 `@Test` × 窄 / 宽 × 浅 / 深 = 20 张），本ask增量外既有文件零改动。
- 新增用例宿主（均复用既有 fake/host 能力，无新业务代码）：
  书架经 `LocalLibraryRepository` 注入 `FakeLibraryRepository`
  （`LibrarySession.kt:476-480` compositionLocal 默认 null → 未接线空态，
  本批注入后呈真实 3 条数据，见 `v005-library-360-light.png` 抽看）；
  浏览 / 详情复用 `FakeBrowseService` + `BrowseSession.refresh` 模式
  （同 `ShellScreenshotsTest.kt:111-117`）；历史复用 `FakeHistoryService` 3 行；
  阅读器复用 `FakeReaderService` 3 页 `pngBytes`（同 `ReaderScreenshotTest.kt:35-53`）。

## 3. 实际执行的命令与输出 (本 ask 实跑，非推测)

- 命令：
  `JAVA_HOME="$HOME/devtools/jdk-21" ./gradlew :shared:app:jvmTest --tests "app.koyura.app.V005BaselineCanvasesTest" -PreaderScreenshotDir="$PWD/build/v0-05"`
- 输出尾：`BUILD SUCCESSFUL in 26s`（`55 actionable tasks: 13 executed, 42 up-to-date`）。
  注意：仅跑了新增类（ask 指定的规模），不是全量门；全量门仍 not run。
- 落盘：`build/v0-05/` 20 个 PNG；本 ask 以 PNG 头解析实测全部尺寸：
  `360x720` × 10（5 类 × 浅深）、`1280x800` × 10（5 类 × 浅深），与目标精确一致。
- 抽看（本 ask 以 Read 工具目视，非推测）：
  `v005-library-360-light.png` 书架真实 3 条（徽标 / 底栏齐全）；
  `v005-reader-360-light.png` 阅读器已开页（内容色块 + `1/3`  chrome）；
  `v005-browse-1280-dark.png` 深色 rail + 来源列表。
  其余 17 张未逐张目视，以尺寸 + SHA 归档，manifest 如实标注。

## 4. 脏基线声明 (未宣布最终通过)

- 项目 HEAD：`4f82b71ac4e95df00461f8d83060a5e977f5b96a`（short `4f82b71`，
  本 ask `git rev-parse HEAD` 实测）。
- 参考：`reference/komikku` HEAD `290267d`（全值 `290267dd4c99f5b46812240fb0e4bc6b3ee8c562`；
  修复ask在本工作树实跑 `git -C reference/komikku rev-parse HEAD` 成功输出即此值，
  可复核路径即该命令；若在其他工作树该目录不存在则以本记录为准）；
  `reference/Venera-Next` HEAD `c0c4597`（沿用 execution-state 登记值，本 ask 未复核）。
- 脏基线：本ask改动前 `git status --porcelain | wc -l` = `105`（全部任务前已存在）；
  截图生成时为 `106`（105 + 本 ask 新增 `?? V005BaselineCanvasesTest.kt`）；
  归档时为 `107`（106 + `?? docs/alignment/v0-05/` 目录项；修复ask实测现仍为107，
  构成 `2 D + 44 M + 2 RM + 59 ??`，其中 `D` 为 `KomikkuShellScreenshotsTest.kt` /
  `KomikkuTokens.kt` 等任务前已存删除，`M`/`RM` 亦全部任务前已存在）。
  故本批为脏基线产物，标记待干净基线重跑确认，不作最终通过。
- JDK：`~/devtools/jdk-21`（Corretto `21.0.12.1`，本 ask `java -version` 实测）。

## 5. 未覆盖 / pending（诚实登记，不填数）

- 原版（Komikku `290267d`）同画布成对图：pending（本批仅我方重拍，无原版侧证据）。
- 真机 / 三端构建 / 录屏 / 性能基线：pending（本批为 JVM `ImageComposeScene` 离屏，
  `Density(1f)`，非设备像素）。
- 其余 17 张未逐张目视：已在 §3 声明，不宣称全部人工确认。
- 全量 gradle 门（含 `-PveneraReferenceDir` 的 domain/data/source-runtime 回归）：
  not run，留给脚本按路线图 7.5 统一跑。
