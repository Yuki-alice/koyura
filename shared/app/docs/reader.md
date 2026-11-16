# 漫画阅读器（一级导航 → 详情 → 阅读器）

`shared/app` 新增 Komikku 风格的横向漫画阅读器：详情页目录可点、开始/继续阅读、返回回到详情、全屏阅读、按需加载、单页重试、跨章拒收旧结果。

## 结构

| 文件 | 职责 |
| --- | --- |
| `reader/ReaderService.kt` | 可注入的 UI 接口：`readProgress`、`openChapter`、`loadPageBytes`、`saveProgress`，以及 `LocalReaderService` |
| `reader/ReaderSession.kt` | 章节打开、按页加载（逐页 generation 令牌）、已读页持久化（仅 settled 页 + 退出时当前页，打开时不写） |
| `reader/ReaderScreen.kt` | 全屏横向阅读器：`HorizontalPager`、点击分区、桌面键盘、页码、上一章/下一章、单页重试 |
| `navigation/AppRoute.kt` | 新增 `ComicReaderRoute(sourceKey, workId, chapterId?, title, chapterIds, chapterTitles)` |
| `browse/SourceScreens.kt` | 目录行可点击；有服务时显示「开始阅读」/「继续阅读·第 N 页」 |
| `App.kt` / `AppScaffold.kt` | `App(browseService, readerService)` 双注入；阅读器打开时隐藏底栏/导航轨，全屏显示 |

## 主 agent 接生产服务的签名

```kotlin
interface ReaderService {
    suspend fun readProgress(sourceKey: String, workId: String): ReaderProgress?
    suspend fun openChapter(sourceKey: String, workId: String, chapterId: String?): ChapterContent
    suspend fun loadPageBytes(sourceKey: String, workId: String, chapterId: String?, pageKey: String): ByteArray
    suspend fun saveProgress(sourceKey: String, workId: String, chapterId: String?, pageIndex: Int)
}
data class ReaderProgress(val chapterId: String?, val pageIndex: Int)
data class ChapterContent(val chapterId: String?, val title: String, val pageKeys: List<String>, val startPageIndex: Int)
```

装配（二选一，推荐第一种）：

```kotlin
App(browseService = ..., readerService = VeneraReaderService(...))
// 或测试/预览中：
CompositionLocalProvider(LocalReaderService provides service) { App(...) }
// AppScaffold 也接受 readerSession = ReaderSession(service) 以便预置状态。
```

Venera 映射建议（供生产实现参考，本轮未实现）：

- `openChapter`：`loadEp(workId, epId)`，`chapterId` 即目录返回的 ep 键；`chapterId == null` 时传空 ep 由脚本决定（无分章作品）。返回 `images` 列表，转 `pageKeys`（用图片 URL/键），`startPageIndex` 取服务端的已存进度（同作品同章节则回传保存页，否则 0）。
- `loadPageBytes`：`onImageLoad(imageKey, workId, epId)` 取 `{url, headers}`，再用该 URL 与 headers 做一次 Ktor GET，返回响应字节。
- `readProgress` / `saveProgress`：对接作品阅读进度存储（按 `sourceKey + workId` 存 `(chapterId, pageIndex)`）。
- `updateCheck` 等与阅读无关，不需要。

## 并发与保存规则

- 每次 `openChapter` 递增代际令牌；页加载完成时若令牌已变则丢弃（切章/重开后，旧请求的字节永远不会写入新章节）。
- 同一页重复请求直接复用（Loading/Ready 不重复提交）；失败页可单独重试，每次重试换新令牌，旧的迟到结果同样被丢弃。
- 保存只发生在两处：翻页停稳后的 settled 页，以及退出阅读器时的当前页；打开时的初始页被记为已保存因此恢复进度不会先写第 0 页覆盖。
- 进度按 `(sourceKey, workId)` 存 `(chapterId, pageIndex)`；「继续阅读」打开保存的章节并从保存页进入。

## 验收

```bash
./gradlew --max-workers=1 :shared:app:jvmTest \
  :apps:android:assembleDebug \
  :apps:desktop:classes \
  :shared:app:linkDebugFrameworkIosSimulatorArm64 \
  '-PveneraReferenceDir=/Users/kurisu/project x/reference/Venera-Next'
```

测试覆盖（`ReaderSessionTest` 11 + `ReaderFlowTest` 6）：

| 用例 | 覆盖 |
| --- | --- |
| 恢复第三页 | 详情 → 阅读 → 翻到第 3 页 → 返回 → 销毁重建 → 继续阅读回到第 3 页；初始打开不写进度 |
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
