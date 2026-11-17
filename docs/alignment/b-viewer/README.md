# B-viewer · Komikku 风格共享漫画 Viewer

任务分支 `feat/komikku-comic-viewer`（基线 `6fef228`），负责人 B。
Viewer 与阅读状态放在 `:shared:features`，独立 UI 测试与截图宿主放在
`:shared:app/src/jvmTest/kotlin/reader/viewer/`，包名
`app.koyura.app.reader.viewer`。生产接线文件 `ReaderScreen.kt` 由主 agent 负责，
本目录是 B 的全部任务材料。

## 交付物

- `shared/features/.../reader/comic/viewer/`：`ComicViewerOptions.kt`（模式/布局/
  适配/选项/跳页/边界）、`ComicSpreads.kt`（双页配对与位置映射纯逻辑）、
  `ComicViewer.kt`（`ComicViewer` 及分页/条漫/缩放实现）。
- `shared/features/src/commonTest/.../viewer/ComicSpreadsTest.kt`：配对与锚定位单测。
- `shared/app/src/jvmTest/kotlin/reader/viewer/`：`ViewerFixture.kt`（两章固定夹具，
  第 2 章 7 张编号页）、`ComicViewerTest.kt`（11 个 UI 测试）、
  `ComicViewerScreenshotTest.kt`（10 个截图测试，11 张截图）、
  `FlowFramesTest.kt`（3 个操作分镜测试，11 帧）、
  `OriginalReferenceShotsTest.kt`（1 个对照表测试：12 张原版行为示意 + 12 张对照表）。
- `shared/app/.../values/reader_viewer.xml`：独立 `reader_viewer_` 前缀资源，
  不与生产 `reader_*` 混用。
- `docs/alignment/b-viewer/shots/`：Koyura 截图提交件（由截图测试生成）。
- `docs/alignment/b-viewer/compare/`：同数据同画布对照表（左 Koyura 实拍，
  右原版行为示意）。
- `docs/alignment/b-viewer/rec/`：三段操作录屏 MP4（`flow-rtl.mp4`、
  `flow-chapter.mp4`、`flow-modes.mp4`）及逐帧 PNG（`frames/`）。

## 接口签名（与主 agent / C 的约定）

```kotlin
@Composable
fun ComicViewer(
    session: ReaderSession,
    content: ChapterContent,
    options: ComicViewerOptions,
    jump: ComicViewerJump?,
    onToggleMenu: () -> Unit,
    onChapterBoundary: (ComicChapterBoundary) -> Unit,
    modifier: Modifier = Modifier,
)

data class ComicViewerOptions(
    val mode: ComicReadingMode = ComicReadingMode.LEFT_TO_RIGHT,
    val pagerLayout: ComicPagerLayout = ComicPagerLayout.SINGLE_PAGE,
    val firstPageSingle: Boolean = true,
    val invertDoublePages: Boolean = false,
    val scaleFit: ComicScaleFit = ComicScaleFit.FIT,
    val cropBorders: Boolean = false,
    val doubleTapZoom: Boolean = true,
)

enum class ComicReadingMode { LEFT_TO_RIGHT, RIGHT_TO_LEFT, VERTICAL, WEBTOON, CONTINUOUS_VERTICAL }
enum class ComicPagerLayout { SINGLE_PAGE, DOUBLE_PAGES, AUTOMATIC }
enum class ComicScaleFit { FIT, FIT_WIDTH, FIT_HEIGHT, ORIGINAL }
enum class ComicChapterBoundary { PREVIOUS, NEXT }
data class ComicViewerJump(val requestId: Long, val contentSeq: Long, val pageIndex: Int)
```

- 模式 ID 与布局 ID 即约定字符串（`id` 字段），主 agent 把 C 的有效偏好映射到
  `options`，Viewer 类型归 B。
- `jump.pageIndex` 是零基原始图片下标；同一 `requestId` 只消费一次，
  `contentSeq` 与 `session.contentSeq` 不一致的旧章节命令直接忽略。
  页码的一基显示由 C 负责（`session.currentPage + 1`）。
- Viewer 经 `ReaderSession.markDisplayed` 上报当前页、`savePage` 上报已停稳位置，
  保存沿用原始 `pageKey`；双页按逻辑阅读顺序取第一页为主页面。
- 中央点按只回调 `onToggleMenu`；到头/到尾的翻页或点按只上报
  `PREVIOUS`/`NEXT`，切章由宿主完成。
- 测试钩子与生产一致：`reader-tap-prev/next/menu`、`reader-page-<i>`、
  `reader-page-image-<i>`、`reader-page-retry-<pageKey>`，重试走
  `session.retryPage`，供生产回归复用。

## 模式行为表（对照原版）

| 本项目模式 | 原版来源 | 行为 |
| --- | --- | --- |
| `LEFT_TO_RIGHT` | `L2RPagerViewer` + `PagerViewerAdapter.setJoinedItems` | 横向分页，左=上一页，右=下一页，方向键/点按走视觉方向 |
| `RIGHT_TO_LEFT` | `R2LPagerViewer`（adapter 逆序，`moveToNext=moveLeft`） | 同一份 spread 列表逆序展示；点按与方向键保持视觉方向，逻辑上左侧=下一页 |
| `VERTICAL` | `VerticalPagerViewer` | 纵向分页，上=上一页，下=下一页 |
| `WEBTOON` | `WebtoonViewer(isContinuous=true)` | 纵向连续条漫，页间距 0；点按边区步进滚动（原版 `scrollDistance=3/4` 屏），中央切菜单 |
| `CONTINUOUS_VERTICAL` | `WebtoonViewer(isContinuous=false)`（`ReadingMode` 映射） | 纵向条漫，页间距 15dp（原版 `WebtoonPageHolder` 的 `bottomMargin=15dpToPx`），其余同上 |

- 分页布局：`SINGLE_PAGE` 始终单页；`DOUBLE_PAGES` 相邻配对，`firstPageSingle`
  时首张单页（封面）；`AUTOMATIC` 仅横屏（宽>高）配对，对应原版
  `ReaderActivity.setDoublePageMode`（横屏双页/竖屏单页）。
- 宽跨页（解码后宽>高）在双页/横屏自动布局下独占一个 spread，解码后动态修正
  （对应原版 `PagerPageHolder` 的运行时 split/merge）；`invertDoublePages` 只镜像
  spread 内的左右顺序（原版 `isLTR = !isRtl xor invert`）。
- 章边界：分页在首/尾 spread 继续向前/向后翻时上报；条漫滚动到端部点按边区时
  上报；不预加载相邻章节内容（宿主切章）。
- 缩放：`FIT/FIT_WIDTH/FIT_HEIGHT/ORIGINAL`；`cropBorders=true`
  时按填满裁剪显示（原版像素级裁边在跨平台解码侧，主 agent 审阅后可替换）；
  `doubleTapZoom` 双击在 1x/2.5x 切换，另支持双指捏合；桌面方向键/翻页键已接，
  滚轮沿用 pager/列表原生滚动。
- 条漫重建与切章：`LazyListState` 按 `session.contentSeq` 重置，
  以 `header + session.currentPage` 为初始位置，保证续读落点。

## 固定夹具（`ViewerFixture`）

- 第 1 章 3 张竖页（跨章用）。
- 第 2 章 7 张编号页：q0 竖封（400x600）、q1 横跨页（800x400，解码后独占）、
  q2 普通、q3 长图（400x1200）、q4 普通、q5 首次失败第二次成功（可重试）、
  q6 奇数尾页。PNG 由 `java.awt` 现场绘制编号，Skia 解码。

## 测试与结果（独立宿主，2026-11-17，返修后）

- `:shared:features:jvmTest`：`ReaderSessionTest`（12）+ `ComicSpreadsTest`（7）
  + `ComicCropTest`（8），全过。
- `:shared:app:jvmTest --tests 'app.koyura.app.reader.viewer.*'`：
  `ComicViewerTest` 14 项、`ComicViewerScreenshotTest` 15 项（17 张截图）、
  `FlowFramesTest` 4 项（14 帧）、`OriginalReferenceShotsTest` 1 项
  （12 张原版示意 + 12 张对照表），全过（34/34；另 features 27 项）。
  - LTR 点按前进/中央切菜单/横向滑动（即时 down/move/up 注入不依赖测试时钟）；
    RTL 左侧点按=下一页、左/右方向键镜像、右向滑动前进（滑动后方向键仍可达，
    根容器带 `focusable()`）；
    纵向分页上/下点按与上/下滑动、方向键；
    中央双击识别为缩放（两次点按都不翻页，之后点按仍可翻页）；
    WEBTOON 滚到底边区点按上报 NEXT 并由宿主切到第 2 章；
    双页配对与反序保持主页面；运行时宽跨页解码后自动拆分独占并重锚同页；
    跳页消费一次、忽略旧章节命令；
    切模式（LTR→WEBTOON→CONTINUOUS）保持同一原始页；
    q5 失败态出现重试键、重试后同一页键变 Ready；
    重建 Viewer 后第 2 章第 3 页仍是同一页键（q2）。
  - 返修定向用例：
    `automaticDoubleAnchorsOriginalPageOnResize`（420→1000→420dp、切模式、重建；
    显示页、保存调用与 pageKey 三项断言，见「返修 1」）；
    `rtlChapterBoundariesMirrorVisualEdges`（RTL 点按/方向键的 PREVIOUS/NEXT
    方向与全作品首尾静默，见「返修 2」）；
    `cropBordersKeepsPositionAndRetrySemantics`（裁边开关下页面、位置与重试语义）。
  - 根容器带 `focusable()` + `focusRequester`，方向键可达；点按区使用不消费
    手势的自研双击识别（400ms 窗口、确认延迟走真实时钟），滑动/捏合直达
    pager/列表；测试另用语义点击做单次点按。
- 回归命令（以 `reference/Venera-Next` 为 `-PveneraReferenceDir`，截图目录
  `build/komikku-b`，见「截图」）：

```bash
REF='/Users/kurisu/project x/reference/Venera-Next'
./gradlew :shared:features:jvmTest --rerun \
  :shared:app:jvmTest --rerun --tests 'app.koyura.app.reader.viewer.*' \
  :shared:features:assembleAndroidMain :shared:features:jvmJar \
  :shared:features:compileKotlinIosSimulatorArm64 \
  -PveneraReferenceDir="$REF" -PreaderScreenshotDir="$PWD/build/komikku-b"
```

## 截图（同数据、同画布）

截图测试按 420x780（窄）/1280x800（宽）、density=1 渲染，输出目录
`build/komikku-b/`，Koyura 提交件复制于 `docs/alignment/b-viewer/shots/`：

- `viewer-ltr-narrow.png` / `viewer-rtl-narrow.png` / `viewer-vertical-narrow.png` /
  `viewer-webtoon-narrow.png` / `viewer-continuous-narrow.png`：五种模式首屏。
- `viewer-double-wide.png`：宽屏双页 `[q2,q3]` 配对（封面单页、q1 解码后独占）。
- `viewer-double-wide-inverted.png`：同上，RTL+反序。
- `viewer-double-wide-spread.png`：横跨页 q1 解码后独占一屏。
- `viewer-webtoon-long.png`：条漫跳到长图 q3。
- `viewer-pager-long-fitwidth.png`：分页 `FIT_WIDTH` 下的长图。
- `viewer-failed.png` / `viewer-failed-retried.png`：q5 失败态与重试成功态。
- `viewer-crop-off.png` / `viewer-crop-on.png`：同一 40px 白框页面、同一
  420x780 视口的裁边前后对照（白框消失、内容放大填满、四角标记完整）。
- `viewer-crop-off-borderless.png` / `viewer-crop-on-borderless.png`：无边框页
  四角带内容标记的裁边前后对照（开启裁边后四角黄块仍完整可见，证明只去边框、
  不切内容）。

## 原版对照（同数据、同画布）

原版是 Android View 实现，本机无 Android 运行环境，且原版仓库
（`reference/komikku/.github/readme-images/screens.png`）只有浏览/主题宣传图、
没有阅读器截图，无法逐像素对照。因此原版侧提供**同数据同画布的行为示意**：
`OriginalReferenceShotsTest` 用同一份夹具字节在同一画布尺寸上，
按原版代码逐项绘制（`orig-*.png`），再与 Koyura 实拍拼成对照表
（`compare-*.png`，左 Koyura 右原版），提交于 `docs/alignment/b-viewer/compare/`。
每张示意都可由引用的源码行复核，面板标题只写模式与页码（对照表头已注明
code-derived；绝不冒充真机截图）。

| 对照表 | 原版规则与源码出处 |
| --- | --- |
| `compare-ltr/rtl/vertical-narrow` | 三种分页器的首屏单页 Fit 居中；点按区按 `ViewerNavigation` 的 `RightAndLeftNavigation`（左=LEFT/右=RIGHT）与 `LNavigation`（上=PREV/下=NEXT/中=MENU）绘制，配色取 `NavigationRegion` 的 ARGB；RTL 下同一视觉分区映射为 next（`R2LPagerViewer` 把 `moveToNext` 反转为 `moveLeft`，adapter 逆序） |
| `compare-webtoon/continuous-narrow` | WEBTOON 页间 0 间距、`CONTINUOUS_VERTICAL` 15dp 间距（`WebtoonPageHolder.refreshLayoutParams` 的 `bottomMargin=15.dpToPx`，仅 `!isContinuous` 时加）；`ReadingMode` 把 WEBTOON 映射到 `WebtoonViewer(isContinuous=true)`，CONTINUOUS_VERTICAL 映射到 `(isContinuous=false)` |
| `compare-double-wide[-inverted]` | `PagerViewerAdapter.setJoinedItems` 的相邻配对 + `firstPageSingle`（封面单页）；合并方向 `isLTR = !isRtl xor invertDoublePages`（`PagerPageImageView`/`mergePages`），反序表把左右互换 |
| `compare-double-wide-spread` | 宽跨页解码后独占（`onPageSplit`/`InsertPage` + `fullPage` 隔离；Koyura 侧同机制，见 `wideSpreadSplitsAndReanchors` 测试） |
| `compare-webtoon-long` / `compare-pager-long-fitwidth` | 长图按宽填满纵向延展（webtoon 从目标页顶开始；分页 `FIT_WIDTH` 上下裁剪居中；原版 `landscapeZoom`/长图走 `SubsamplingScaleImageView`，示意取同构图） |
| `compare-failed[-retried]` | `PagerPageHolder.showErrorLayout` 的错误文案 + `actionRetry → pageLoader.retryPage`（原版另有 http 才有的 webview 入口，本地夹具无，故省略） |

真机逐像素对照仍待主 agent 在 Android 侧补拍；补拍时可用同一份夹具
（`ViewerFixture` 的编号与尺寸见「固定夹具」）复现。

## 录屏（四段，均已落盘）

`FlowFramesTest` 用同一交互宿主逐步操作并逐帧截图
（`build/komikku-b/frames/flow-*.png`，帧即测试断言过的状态），再用 ffmpeg
按 1fps 拼成 MP4（`docs/alignment/b-viewer/rec/flow-*.mp4`，840px 宽）：

```bash
ffmpeg -y -framerate 1 -i flow-rtl-%d.png -c:v libx264 -pix_fmt yuv420p \
  -vf "scale=840:-2" rec/flow-rtl.mp4
```

1. RTL 操作：`rec/flow-rtl.mp4`（`recordRtlFlow`：RTL 第 2 章 p1 → 左侧点按到 p2 →
   左方向键到 p3 → 右向滑动到 p4）。
2. 跨章：`rec/flow-chapter.mp4`（`recordChapterFlow`：WEBTOON 第 1 章顶部 →
   滚动 → 到底边区点按上报 NEXT → 宿主切到第 2 章顶部 → 再滚一屏）。
3. 切模式保位：`rec/flow-modes.mp4`（`recordModeSwitchFlow`：LTR p3 →
   WEBTOON p3 → CONTINUOUS_VERTICAL p3，计数器全程 `3 / 7`）。
4. 缩放窗口保位：`rec/flow-resize.mp4`（`recordResizeAnchoringFlow`：AUTOMATIC
   420dp 到第 3 页 → 拉到 1000dp 组成 [1-2, 1-3] 双页，帧头仍为 `AUTOMATIC 3 / 3`
   且 1-3 完整可见 → 缩回 420dp 仍 `3 / 3`）。同场景的断言版本见
   `automaticDoubleAnchorsOriginalPageOnResize`（显示页、保存调用、pageKey 三项）。
5. 双击缩放：由 `doubleTapZoomsInsteadOfTurningTwice` 覆盖（双击不翻页、
   之后点按仍可翻页；2.5x 缩放刻度本身是确定性代码路径，生产侧可在真机复核手感）。

## 与生产接线的状态（分别报告）

- 独立宿主：通过（features 27 项 + app viewer 34 项，共 61 项，见上）。Viewer 不依赖
  `ReaderScreen.kt`，可直接被生产阅读器嵌入。MP4 与对照表见上（`rec/`、`compare/`）。
- 生产接线：未接线。`ReaderScreen.kt` 仍是旧单页横向阅读器（主 agent 负责把
  `ComicViewer`、`ComicViewerOptions`（C 的有效偏好映射）、`ComicViewerJump`
 （跳页/续读/章列表）与 `onChapterBoundary`（切章）接入，C 负责 chrome/设置；
  接入后生产回归可复用本目录的钩子与语义）。

## 已知近似与后续（主 agent 审阅）

1. 裁边（`cropBorders`）按原版语义实现为「检测实色外框 → 去除 → 按用户适配渲染」：
   `ComicCrop.detectBorderCrop` 逐行/列取最外沿主色并向内延伸，命中内容即停，
   单边上限 25% 防止吃掉近纯色页；随后用 `contentFitTransform` 按 FIT/FIT_WIDTH/
   FIT_HEIGHT/ORIGINAL 适配**内容矩形**，由 `ZoomableComicPage` 直接以
   DrawScope 的 `drawImage(srcOffset/srcSize)` 裁剪绘制。无边框页检出 0 边距，
   四角内容不受影响。原版的 `SubsamplingScaleImageView.setCropBorders` 是原生
   位图级裁边，本项目在 Compose 层等价实现；像素级一致性由 `ComicCropTest`
   （含手绘边框/噪声/无边框角标/横跨页/长图/近纯色页）与两张同视口截图证明。
2. 长图无子采样解码（大长图全图解码），待性能验证后决定是否引入。
3. `shiftDoublePage`（任意位置起配对）未暴露为选项，当前仅首张单页开关；
   需要时可扩展 `options`。
4. 条漫图按宽度填满、高度按位图纵横比包裹；`FIT/FIT_HEIGHT/ORIGINAL` 在条漫下
   的行为与分页一致（高度包裹），细节可审阅。
5. 条漫末尾有一屏高的占位，便于最后一页置顶；是否保留由主 agent 定。

## 返修记录（2026-11-17）

### 返修 1 · AUTOMATIC 双页重排覆盖阅读锚点

- 复现：三张竖页章节，AUTOMATIC + 首页单页，420x780dp 读到第 3 页并等保存，
  改 1000x720dp 让 2、3 页配对。旧实现把显示与保存索引从 2 改成 1。
- 根因：重排后 `displayed/settled` 上报直接用 `spread.masterPageIndex`，
  未保留“用户已到达的原始页”；宽屏配对后主页面变成 1（第二页）。
- 修复：`reportedPage(displayIndex)` —— 若当前锚点页仍在该 spread 内则原样上报，
  否则才回退到 spread 主页面（新翻页的自然语义）；重排 effect 的 key 增加
  `options.mode`；`savePage` 走到同一判定。
- 定向用例 `automaticDoubleAnchorsOriginalPageOnResize` 断言：
  - 宽屏后 `session.currentPage == 2` 且 `pageKeys[2] == "p2"`；
  - `saveCalls` 中 `pageIndex == 1` 的计数不变，最后一条保存仍是 `2`；
  - 缩回 420dp、切 CONTINUOUS_VERTICAL 再切回 LTR，仍为 `3 / 3` 且键 `p2`；
  - 以保存进度重建新 Viewer，仍为 `3 / 3` 且键 `p2`。
- 反向验证：`git stash` 掉实现后该用例与 RTL 用例双双失败（见验收日志）。

### 返修 2 · RTL 章边界方向错误

- 复现：RTL 打开第 2 章第 1 页，点右侧期望一次 PREVIOUS，旧实现无回调。
- 根因：`goVisual` 用视觉索引 ±1 判定，但回调方向写成固定的“越界即 NEXT/PREVIOUS”，
  未按 RTL 镜像。RTL 下视觉左侧是故事前进方向。
- 修复：视觉起点（`target < 0`）= LTR 的故事起点 / RTL 的故事终点；视觉终点反之。
  即 LTR：左→PREVIOUS、右→NEXT；RTL：右→PREVIOUS、左→NEXT。邻居存在才上报。
- 定向用例 `rtlChapterBoundariesMirrorVisualEdges`：
  - RTL 第 2 章第 1 页：右侧点按 → 恰好一次 `PREVIOUS`；再按右方向键 → 第二次
    `PREVIOUS`（点按与键盘两路都覆盖）；
  - 跳到全作品最后一页（第 2 章第 7 页）向左：无 NEXT 邻居，不产生回调；
  - 第 1 章最后一页向左 → 一次 `NEXT`；左方向键再 → 再一次 `NEXT`；
  - 第 1 章第 1 页向右/右方向键：无 PREVIOUS 邻居，不产生回调。

### 返修 3 · 裁边裁掉正文

- 复现：旧实现对 `cropBorders=true` 直接改用 `ContentScale.Crop`，等于裁掉
  视口外的一切；无边框页的四周内容（尤其角部）会消失。
- 修复：见「已知近似」第 1 条——先按像素检测实色边框，再以选定的适配方式
  渲染内容矩形；绘制用 `drawImage(srcOffset, srcSize, dstSize)` 精确取源矩形，
  不再依赖嵌套布局约束（此前 `size()` 在父约束下会被压缩，也是右侧白边的来源）。
- 证据：`viewer-crop-off/on.png`（带框页）与
  `viewer-crop-off/on-borderless.png`（无边框、四角有内容），同数据同画布
  420x780、density 1；`ComicCropTest` 8 项覆盖边框、噪声、角标、横跨页、长图、
  近纯色页与 FIT/FIT_WIDTH 变换。

### 验收日志（返修后完整命令）

```text
$ ./gradlew :shared:features:jvmTest --rerun \
    :shared:app:jvmTest --rerun --tests 'app.koyura.app.reader.viewer.*' \
    :shared:features:assembleAndroidMain :shared:features:jvmJar \
    :shared:features:compileKotlinIosSimulatorArm64 \
    -PveneraReferenceDir=... -PreaderScreenshotDir=...
BUILD SUCCESSFUL

ComicViewerTest[jvm]             14   fail/errors: 0
ComicViewerScreenshotTest[jvm]   15   fail/errors: 0
FlowFramesTest[jvm]               4   fail/errors: 0
OriginalReferenceShotsTest[jvm]   1   fail/errors: 0
ComicSpreadsTest[jvm]             7   fail/errors: 0
ComicCropTest[jvm]                8   fail/errors: 0
ReaderSessionTest[jvm]           12   fail/errors: 0
TOTAL 61  FAILED 0
```

反向验证（把 `ComicViewer.kt` 回退到修复前）：

```text
ComicViewerTest > automaticDoubleAnchorsOriginalPageOnResize FAILED
ComicViewerTest > rtlChapterBoundariesMirrorVisualEdges   FAILED
```
