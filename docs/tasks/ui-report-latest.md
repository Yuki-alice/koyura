# UI 轨交付报告（第四轮 · 阅读器轮次）

分支 `feat/ui-parity`（前三轮与夜间轨均已合入）。本轮完成五项任务，全部提交在本分支。

## 1. 本轮改动清单

| 提交 | 内容 |
| --- | --- |
| `fix(statistics): 字段与口径对照原版校准` | 模型字段映射 `StatsData`（阅读时长改毫秒、平均分改 NaN 语义）；时长按原版 `TimeUtils.toDurationString`（DateUtils 短单位 `%dd/%dh/%dm/%ds`，全零回 None）；平均分按 `"%.2f ★"` 且 `trackedTitleCount > 0 && !isNaN` 才展示；Fake 样例与计算语义一致；UI 断言改语言无关 |
| `fix(reader): 点击区改走偏好双路径并移除会话字段` | `ReaderChromeState.tapZone/invertTapZone` 改从 `effective`（全局+覆盖）派生映射（域 `ReaderTapZone` ↔ `ComicTapZoneMode` 一一对应）；设置对话框点击区段并入偏好双路径（全局整份替换 + 覆盖字段补丁含「跟随全局」清除）；移除 `SetTapZone`/`SetInvertTapZone` 动作与 `rememberSaveable` 会话字段；改设置重进保持、覆盖清除回全局断言 |
| `feat(details): 详情页继续阅读改可拖拽悬浮钮` | 内联 Start/Resume 双钮换 SmallExtendedFAB（原版 `MangaScreen.kt:532`）：有未读章节、非多选、阅读器接线时出现（无分章作品同样可开）；点击直达继续/首章；横向拖动松手按中心过半左右吸边，吸附侧落盘书架偏好（Komikku `reading_button_position` 键，序列化十一→十二键） |
| `fix(reader): 竖排页码滑杆改自适应旋转布局` | 去掉固定 280dp 长/64dp 轨，按原版 `ChapterNavigator` 用 `weight(1f)` + 变换原点 + 宽高互换 `layout`，长度随画布、厚度随轨宽；补当前页/总页文本；值域 `1..pageCount`、步进与松手提交不变 |
| `feat(categories): 分类行加长按拖拽排序并保留按钮替代` | 行首加拖动手柄（Komikku `ic_drag_handle_24dp` 路径逐字），长按拖动按行高折算落点提交 `setCategoryOrder`；上移/下移按钮保留为无障碍替代；落点计算纯函数 + 两条路径测试 |

## 2. 统计字段映射表（任务一要求）

| 本模型 | 原版 `StatsData` | 口径（`StatsScreenModel.kt`） |
| --- | --- | --- |
| `overview.inLibrary` | `Overview.libraryMangaCount` | 去重书架作品数 |
| `overview.readDurationMillis` | `Overview.totalReadDuration`（毫秒） | 累计阅读毫秒 |
| `overview.completedEntries` | `Overview.completedMangaCount` | 状态 COMPLETED 计数 |
| `entries.inGlobalUpdate` | `Titles.globalUpdateItemCount` | 纳入全局更新的作品数 |
| `entries.started` | `Titles.startedMangaCount` | `hasStarted` 计数 |
| `entries.local` | `Titles.localMangaCount` | 本地来源作品数 |
| `chapters.total` | `Chapters.totalChapterCount` | 章节总数 |
| `chapters.read` | `Chapters.readChapterCount` | 已读章节数 |
| `chapters.downloaded` | `Chapters.downloadCount` | 下载管理器计数 |
| `trackers.trackedEntries` | `Trackers.trackedTitleCount` | 有追踪记录的作品数 |
| `trackers.meanScore` | `Trackers.meanScore`（NaN 表无分） | 仅 `tracked>0 && !NaN` 展示，`%.2f ★` |
| `trackers.used` | `Trackers.trackerCount` | 已登录追踪服务数 |

格式串：时长 `4m 13s`（无则 `None`/`无`）；平均分 `7.84 ★`（无则 `N/A`/`不适用`）。

## 3. 视觉对照结论与四态自查

| 页/项 | 差异 → 处置 |
| --- | --- |
| 统计页 | 字段名与口径、时长与均分格式 → 已按原版对齐（映射表见上） |
| 阅读器点击区 | 会话级字段 → 改真实偏好链（全局/覆盖/清除），三档映射与原版 navigation mode 一致 |
| 详情页 | 内联双钮 → 悬浮钮（含显隐条件与拖拽吸边），落盘位置键对齐原版 |
| 阅读器竖滑杆 | 固定 280/64 → 自适应（weight + 旋转布局），值域/步进不变 |
| 分类页 | 仅按钮 → 拖动手柄 + 保留按钮（无障碍），手柄图标取原版 drawable |

四态自查：无真机/模拟器，沿用 JVM 语义测试与既有截图测试（`ReaderScreenshotTest`、`ChromeScreenshots` 系全绿）核对；本轮手势类改动（FAB 拖动、分类拖拽）在 JVM 语义测试可稳定驱动并通过，真机手感仍需验收（见第 4 节）。

## 4. 需主 agent 裁决/接线项与下一轮计划

1. **真机验收项（手势手感）**：详情 FAB 拖拽吸附（位移跟手、抬升阴影）与分类拖拽的行跟随为最小实现，JVM 只能验证提交语义；建议在 Android 真机走一遍。
2. **FAB 位置通道**：现用应用层自有键 `reading_button_position`（与 Komikku 键名一致），落在书架偏好序列化（十二键）。若后续统一进设置模块，迁移时保留读旧键兼容即可。
3. **点击区三档限制**：偏好链只收录 DEFAULT/LEFT_RIGHT/KINDLE（与 domain 契约一致）；查看器 EDGE/L_SHAPE/DISABLED 为几何自有用例，不进偏好 UI——若要开放需 domain 扩枚举（禁改区，需引擎轨配合）。
4. **下一轮**：夜间轨其余页面（全局搜索、历史多选）按原版复核；设置页 master-detail 是白皮书 7.9 的未覆盖大项，建议单独立轮。

测试门（JDK 21，`-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`）：

```bash
JAVA_HOME="$HOME/devtools/jdk-21" ./gradlew \
  :shared:design-system:jvmTest :shared:domain:jvmTest :shared:features:jvmTest \
  :shared:data:jvmTest :shared:source-runtime:jvmTest :shared:app:jvmTest \
  -PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next" --rerun-tasks
```

结果：连续两遍 `--rerun-tasks` 全绿。末遍 635 例（domain 13 / features 101 / data 120 / source-runtime 64 / app 337；design-system 无测试源集），本轮 UI 轨新增 11 例（FAB 5 + 竖滑杆 3 + 分类 3）。
