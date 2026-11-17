# C · 阅读器菜单 / 跳页 / 章列表 / 阅读设置

分支 `feat/komikku-reader-chrome`（基线 `6fef228`）。菜单、选择器、设置状态与对应测试归 C；`ReaderScreen.kt`、`ReaderSession.kt`、Viewer、应用外壳与共享 Gradle 文件由各自负责人维护，本任务未改动。

## 交付物

| 路径 | 内容 |
| --- | --- |
| `shared/features/…/reader/comic/chrome/ReaderChromeState.kt` | `ReaderChromeState`（作品标题、当前章 ID、章节列表及标题、零基页索引、总页数、菜单显隐、前后章可用性、全局设置、作品覆盖、有效设置、当前对话框）与 `ReaderChromeAction`（Back / SetMenuVisible / JumpToPage / OpenChapter / ChangeGlobalSettings / ChangeWorkOverride / ResetWorkOverride / SetDialog） |
| `…/chrome/ReaderChrome.kt` | `ReaderChrome(state, onAction, modifier)`：顶栏（返回、标题+章节副标题、一点基页码，点页码开跳页框）、底栏（上一章/下一章 + 滑杆 + 章节/模式/裁边/设置四个入口）、焦点恢复（关弹层回到打开它的按钮） |
| `…/chrome/ChapterListDialog.kt` | 章节弹层：打开即滚动到当前章、当前章高亮、无章节占位、点选发出 `OpenChapter` 并关框 |
| `…/chrome/ReadingModeDialog.kt` | 模式弹层：5 种模式单选 + 应用；`allowDefault` 时可回到 DEFAULT（清覆盖字段） |
| `…/chrome/ReaderSettingsPanel.kt`（`ReaderSettingsDialog`） | 设置弹层：全局默认区（全量替换）与作品覆盖区（DEFAULT 继承 + 按字段补丁 + 恢复全局按钮） |
| `shared/domain/…/reader/preferences/ReaderPreferences.kt` | `ReadingMode`（5 个 ID）、`ReaderLayout`、`ReaderFit`、`ReaderSettings`、`WorkReaderOverride`（全可空，`patchedWith`/`resolvedOver`/`withOverride`） |
| `shared/data/…/reader/preferences/ReaderPreferencesStore.kt` | 文件存储：`reader_global.json` + `reader_overrides.json`（`sourceKey/workId` 键），同目录新实例可恢复；缺文件/缺字段/坏文件回默认值，原子写。SQLite schema、扩展事务、阅读进度文件原样未动 |
| `shared/features/…/reader_chrome.xml`（`reader_chrome_` 前缀，中英） | chrome 专有文案；`nav_screen_back`、`reader-back`、`reader-page-indicator` 及前后章语义沿用原 key |
| 测试 | features commonTest：`ReaderChromeStateTest`（当前章定位、前后章边界、页码钳制、合并语义）；data commonTest：`ReaderPreferencesStoreTest`（默认、新实例恢复、单作品合并、双源同名隔离、清除、旧配置缺字段、**单字段清除、并发补丁合并、写清交错、写失败保旧**）；app jvmTest `app.koyura.app.reader.chrome`：`ChromeTestHost` 独立宿主、`ChromeBehaviorTest`（显隐、遮挡、高亮、首尾禁用、无章节空页、页码边界、**鼠标拖拽只发一次**、焦点恢复、键盘回车、键盘选模式、裁边补丁、**模式选择应用、跟随全局清单字段**）、`ChromeSettingsHostTest`（真实文件存储 + 第 2 章第 3 页保持）、`ChromeScreenshots`（窄/宽 × 浅/深 × 6 状态 + 流程 5 步） |

## 接口签名

```kotlin
@Composable
fun ReaderChrome(state: ReaderChromeState, onAction: (ReaderChromeAction) -> Unit, modifier: Modifier = Modifier)

sealed interface ReaderChromeAction {
    data object Back
    data class SetMenuVisible(val visible: Boolean)
    data class JumpToPage(val pageIndex: Int)          // 零基；拖动只预览，松手提交一次
    data class OpenChapter(val chapterId: String?)     // 前后章复用
    data class ChangeGlobalSettings(val settings: ReaderSettings)
    data class ChangeWorkOverride(val patch: WorkReaderOverridePatch)
    data object ResetWorkOverride
    data class SetDialog(val dialog: ReaderChromeDialog)
}

/** 三态补丁字段：Keep = 保持，Set = 设值，Clear = 清除并继承全局。 */
sealed interface OverrideField<out T> {
    data object Keep : OverrideField<Nothing>
    data class Set<T>(val value: T) : OverrideField<T>
    data object Clear : OverrideField<Nothing>
}
```

## 设置支持矩阵

| 设置 | 全局 | 作品覆盖 | 说明 |
| --- | --- | --- | --- |
| 阅读模式（5 ID）/ 布局（3 ID）/ 适配（4 值） | ✅ | ✅（DEFAULT 继承） | 模式按钮改作品覆盖；设置面板两区都可改 |
| 首页单页 / 双页反序 / 裁边 / 双击缩放 | ✅ 开关 | ✅ 三态（跟随全局/开/关） | 底栏裁边按钮发覆盖补丁 |
| 书签、WebView、浏览器、分享、旋转锁定、页面布局双页移位 | — | — | 未实现：需系统能力（Custom Tab/外部浏览器/分享表/方向锁定），记为当前差异 |
| 点击区、手势、缩放手势、长图分块、RTL 滑杆对调 | — | — | Viewer（B）范围；chrome 滑杆恒 LTR，拖动按任务要求只预览、松手提交一次（与原版实时提交不同） |
| 亮度/颜色滤镜/主题/下载指示 | — | — | 后续任务 |

## 截图与录屏

截图由 `ChromeScreenshots` 生成，输出目录为 `-PreaderScreenshotDir`（本轮为 `build/komikku-c/`）：`chrome-{hidden,open,chapters,mode,settings}-{narrow,wide}-{light,dark}.png`，
流程 `chrome-flow-{1-menu-open,2-jump,3-chapters,4-override,5-reset}.png`（打开菜单 → 跳页 → 选章 → 设置作品覆盖 → 恢复默认）。
录屏：JVM 无录屏能力，以流程截图序列代替；真机操作录屏待主 agent 集成验收时补。

## 本轮验收结果（2026-11-17，基线 `6fef228`；返修提交见本分支顶）

- `ReaderPreferencesTest`（domain）6/6：默认值、继承、按字段合并、补丁保留、**补丁三态（保持/设值/清除）、布尔显式关闭与继承区分**、清除恢复。
- `ReaderPreferencesStoreTest`（data）10/10：默认、新实例恢复、单作品合并、双源同名隔离、清除、旧配置缺字段、**单字段清除保留他字段、并发补丁合并（门控交错）、写清交错一致、写失败保旧**。无锁实现下并发测试按预期失败（已验证复现），加锁后通过。
- `ReaderChromeStateTest`（features）4/4：当前章定位、前后章边界、页码钳制、合并语义。
- `ChromeBehaviorTest`（app）12/12：显隐与遮挡、章列表高亮与首尾禁用、无章节空页、页码边界、**鼠标拖拽只发一次**、弹层关闭焦点恢复、键盘回车开框、**键盘选模式**、裁边补丁、**模式选择应用单补丁、跟随全局只清模式字段**。
- `ChromeSettingsHostTest`（app）2/2：真实文件存储的全局/覆盖/恢复全链路，切换设置保持第 2 章第 3 页。
- `ChromeScreenshots`（app）7/7：29 张对照与流程截图落 `build/komikku-c/`（新增模式跟随全局态）。
- 构建：`:shared:features:assembleAndroidMain`、`:shared:features:jvmJar`、`:shared:features:compileKotlinIosSimulatorArm64` 全过；Venera 夹具未修改（只读引用）。
- 待主 agent 集成验收：实际 Viewer 联动、历史跨进程续读、真机杀进程重建、Windows/Linux 包、操作录屏视频。

## 生产装配所需输入（交主 agent 接线）

1. 状态持有：`chapterId`、`pageIndex`、`pageCount`、`chapters: List<ChapterEntry>`（从详情/目录拼）、`menuVisible`（Viewer 中央点按区切换）、`hasPrev/NextChapter`（首尾禁用）。
2. 动作落地：`JumpToPage` → pager 跳页；`OpenChapter` → `session.open` + 历史写入；`Back` → 先 `saveCurrent` 再退栈；`Change*`/`Reset*` → 调 `ReaderPreferencesStore`（目录由应用数据目录注入，`FileSystem.SYSTEM`）对应方法。补丁示例：
   ```kotlin
   // 覆盖某作品为条漫，其余字段不动
   store.writeOverride(key, id, WorkReaderOverridePatch(mode = OverrideField.Set(ReadingMode.WEBTOON)))
   // 只清模式字段（跟随全局），保留 fit 等其他覆盖
   store.writeOverride(key, id, WorkReaderOverridePatch(mode = OverrideField.Clear))
   // 整组清除
   store.clearOverride(key, workId)
   ```
   并发写同一作品由存储内互斥合并，调用方无需加锁。
3. 覆盖位置：`ReaderChrome` 叠在 Viewer 上层；菜单显隐由 Viewer 点按区发 `SetMenuVisible`。
4. `ReaderScreen.kt` 现有私有 `ReaderChrome` 与新组件共存，本任务不做替换，接线时由主 agent 统一。
