# c-chrome verification · 29 张 Chrome 流程截图归档确认 (V0-02, 未宣布最终通过)

任务: V0-02 c-chrome 证据归档入库。允许范围仅 `docs/alignment`；未改业务代码与测试，
未删用例降断言，未自跑全量 gradle 门。

## 1. 归档内容 (本 ask 实际落盘)

- `docs/alignment/c-chrome/current/` — 29 个 PNG（`build/komikku-c/` 的逐字节拷贝，
  本 ask 以 `diff` 比对 SHA-256 逐个一致：`COPIES_IDENTICAL`）。
- `docs/alignment/c-chrome/manifest.json` — 逐文件 SHA-256、画布、主题、来源宿主、
  生成代码状态（`chrome-flow-1-menu-open.png` 与 `chrome-open-narrow-light.png`
  SHA 相同 `1a0e74…`，与生成代码语义一致：两者同为 demoState 菜单打开窄屏浅色渲染）。
- 本文件 — verification 记录。

## 2. 真实来源与生成条件 (本 ask 已读代码核实)

- 生成器：`shared/app/src/jvmTest/kotlin/reader/chrome/ChromeScreenshots.kt:33-152`
  （7 个 `@Test`：`captureMenuHidden`、`captureMenuOpen`、`captureChapterList`、
  `captureReadingMode`、`captureModeDefault`、`captureSettings`、`captureScriptedFlow`）。
- 流程宿主：`shared/app/src/jvmTest/kotlin/reader/chrome/ChromeTestHost.kt:22-71`
  （独立宿主，按生产宿主语义归约 `ReaderChromeAction`；`ChromeScreenshots.kt:131` 流程用它驱动 5 步）。
- 渲染路径：JVM `ImageComposeScene` 离屏（`ChromeScreenshots.kt:49-75`，
  `SwingUtilities.invokeAndWait` + 双 `render` + Skia `EncodedImageFormat.PNG`），
  `Density(1f)`，`ReaderTheme(darkTheme=…)` 切换，背后为灰色 Viewer 占位。
- 画布：窄 `420x780` / 宽 `1280x800`（`captureAll:77-82`）；流程 5 步全为窄屏浅色
  （`snap:132` 写死 `420, 780, dark=false`）。
- 输出目录接线：`shared/app/build.gradle.kts:76`
  （`systemProperty("reader.screenshotDir", -PreaderScreenshotDir，默认 build/screenshots）`；
  本批以 `-PreaderScreenshotDir` 指向 `build/komikku-c/` 产生。
- 生成代码状态：`ChromeScreenshots.kt` / `ChromeTestHost.kt`
  均已提交（lineage `891c1a7`/`cf1f84f`），归档时工作树干净
  （`git status --porcelain -- <这两文件>` 无输出）；同目录 `ChromeDialogLayoutTest.kt`
  有未提交修改，但它不是截图生成器，不影响本批来源认定。
- 源文件状态：`build/komikku-c/` 29 文件 mtime 均为 2026-11-17 11:55；
  `file` + PIL 全开验证均为有效 PNG（窄 420x780 / 宽 1280x800，RGBA）；
  `git check-ignore` 命中 `.gitignore:6:**/build/`，`git status` 无记录 ——
  归档前确为 gitignored 临时输出，未入库不计为 current（与 v0-01 verification §2/§4 一致）。

## 3. 能力与基线声明 (如实记录，不宣称为最终通过)

- JVM 离屏：JVM 无真机录屏能力，录屏以流程截图序列
  （`chrome-flow-{1..5}`：打开菜单 → 跳页 → 选章 → 设置作品覆盖 → 恢复默认）代替；
  真机操作录屏待主 agent 集成验收时补（沿用 `c-chrome/README.md` §截图与录屏 口径）。
- 脏基线：生成时工作树为脏基线（HEAD `4f82b71` + 未提交改动；
  归档时 `git status --porcelain | wc -l` 实测 100 行），故本批标记**待重跑确认**，
  不宣称为最终通过。干净基线重跑后以新 SHA 更新 manifest。
- 目标画布 `360×720` / `1280×800` 成对图仍 pending：本批窄为 420（≠360 配对），
  保留为回归扩展，不得冒充 360 配对。
- 原版侧配对（Komikku `290267d` 同状态截图/录屏）仍 pending； viewer 原版侧仅示意、
  `rec/*.mp4` 非真机录屏口径不变。

## 4. 本次未做的事

- 未跑全量 gradle 门与三端构建（按任务要求不自跑；`ChromeScreenshots` 本次未重执行，记为 not run）。
- 未新增/修改业务实现与测试；未删除失败用例或降低断言。
- 未伪造原版运行截图/录屏与通过状态；空缺记为 pending。
