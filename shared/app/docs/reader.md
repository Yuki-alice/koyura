# 漫画阅读器（一级导航 → 详情 → 阅读器）

漫画阅读器已迁入 `:shared:features`（包 `app.koyura.features.reader.comic`），Komikku 风格横向阅读：详情页目录可点、开始/继续阅读、返回回到详情、全屏阅读、按需加载、单页重试、跨章拒收旧结果。`shared/app` 只保留生产装配（`VeneraReaderService`、导航路由、外壳）与生产链路测试。

## 结构

| 文件 | 职责 |
| --- | --- |
| `:shared:domain` 的 `reader/ComicReader.kt` | 可注入的 UI 接口：`readProgress`、`openChapter`、`loadPageBytes`、`saveProgress`，以及纯 Kotlin 模型（无 Compose 依赖） |
| `:shared:features` 的 `reader/comic/ReaderSession.kt` | 打开章节、加载和重试单页、保存翻页停稳后或返回详情前的阅读位置；服务由调用方显式传入 |
| `:shared:features` 的 `reader/comic/ReaderScreen.kt` | 全屏横向阅读器：`HorizontalPager`、点击分区、桌面键盘、页码、上一章/下一章、单页重试 |
| `:shared:features` 的 `reader/comic/PlatformImage.kt`（+ 各平台 actual） | 图片字节解码为 `ImageBitmap` 的 expect/actual |
| `:shared:features` 的 `reader/comic/FakeReaderService.kt` | 共享内存测试替身：feature 单元测试与 app 生产链路测试共用 |
| `shared/app` 的 `reader/VeneraReaderService.kt` | 将阅读界面接入生产会话中的 `ComicReadingService` |
| `shared/app` 的 `ReaderInjection.kt` | `LocalReaderService` CompositionLocal（注入留在 app） |
| `navigation/AppRoute.kt` | 新增 `ComicReaderRoute(sourceKey, workId, chapterId?, title, chapterIds, chapterTitles)` |
| `browse/SourceScreens.kt` | 目录行可点击；有服务时显示「开始阅读」/「继续阅读·第 N 页」 |
| `App.kt` / `AppScaffold.kt` | `App(browseService, readerService)` 双注入；阅读器打开时隐藏底栏/导航轨，全屏显示 |

## 生产服务装配

Android、桌面和 iOS 通过 `ExtensionApp` 打开持久化会话，再将 `VeneraBrowseService` 和 `VeneraReaderService` 注入 `App`。阅读服务共用该会话的扩展运行时、SQLite 数据库和 Ktor 客户端。

- `openChapter` 调用 `comic.loadEp` 取得页面列表，并按同一章节的已存页面键恢复位置。页面键失效时使用仍在有效范围内的保存页码。
- `loadPageBytes` 使用已打开章节返回的图片标识，可经 `comic.onImageLoad` 配置实际请求，返回图片字节供平台解码。
- `saveProgress` 保存当前页面键与零基页码。点击「继续阅读」时读取该作品最近写入的章节。

## 并发与保存规则

- 每次 `openChapter` 递增代际令牌；页加载完成时若令牌已变则丢弃（切章/重开后，旧请求的字节永远不会写入新章节）。
- 同一页重复请求直接复用（Loading/Ready 不重复提交）；失败页可单独重试，每次重试换新令牌，旧的迟到结果同样被丢弃。
- 翻页停稳或点击阅读器顶栏返回时保存当前位置。进入新章节后也会保存首屏位置，恢复已保存的位置时跳过重复写入。
- SQLite 按 `(sourceKey, workId, chapterId)` 保存页面键与页码。已有扩展数据库会在启动时补建进度表。

## 验收

```bash
./gradlew --max-workers=1 :shared:features:jvmTest :shared:app:jvmTest \
  :apps:android:assembleDebug \
  :apps:desktop:classes \
  :shared:app:linkDebugFrameworkIosSimulatorArm64 \
  '-PveneraReferenceDir=/Users/kurisu/project x/reference/Venera-Next'
```

`VeneraReaderRestartTest` 用原版 Venera 示例脚本和磁盘 SQLite 验证第 2 章第 3 页的跨会话续读。需要跨进程复现时，先将 `READER_COMIC_RESTART_PROFILE` 指向一个空目录，再分别以 `READER_COMIC_RESTART_PHASE=read` 和 `READER_COMIC_RESTART_PHASE=resume` 运行以下命令。

```bash
./gradlew :shared:app:jvmTest --rerun --tests 'app.koyura.app.reader.VeneraReaderRestartTest'
```

`ReaderSessionTest`（`:shared:features:jvmTest`）和 `ReaderFlowTest`（`:shared:app:jvmTest`）覆盖以下场景。

| 用例 | 覆盖 |
| --- | --- |
| 恢复第三页 | 详情 → 阅读 → 翻到第 3 页 → 返回 → 销毁重建 → 继续阅读回到第 3 页 |
| 章节行直达 | 点第二章行直接打开并显示 "1 / 3"，上一章/下一章启用态正确 |
| 无分章作品 | 空目录仍可开始阅读，`chapterId = null` 全程透传并保存 |
| 单页失败重试 | 首次失败显示重试，重载成功后按钮消失 |
| 切章拒收旧结果 | 切章后旧 in-flight 页结果被丢弃（会话级断言） |
| 切章 UI | 阅读器内下一章切到新章节并保留详情返回路径 |
| 键盘翻页 | 左右方向键翻页 |

截图（`shared/app/docs/screenshots/`）：

- `reader-narrow.png`：420 dp 全屏阅读器，顶栏（返回/标题/章节/页码）与底栏（上一章/下一章）
- `reader-wide.png`：1280 dp 桌面宽度全屏阅读（导航轨隐藏）

## 与 Komikku 的差异

1. **单阅读器**：原版有 LTR/RTL/竖向/双页等多种 Viewer；首版只做横向 LTR 单页。
2. **无缩放与长图分块**：图片按 `Fit` 完整显示；长条漫、缩放手势、双页合并后续再做。
3. **无预读与缓存层**：只预加载相邻页到内存无磁盘缓存；离线阅读、下载队列后续再接。
4. **无阅读设置**：方向锁定、亮度、色调、翻页动画、点按区域自定义等偏好后续再加。
5. **菜单常驻**：控制栏默认显示，点中央区域可隐藏/显示；原版另有自动隐藏计时。
6. **返回即保存**：顶栏返回先保存当前页再出栈；系统返回依赖 settled 保存（与原版 back 栈行为一致，详情仍是返回目标）。
