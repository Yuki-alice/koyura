# 给实现 agent 的任务：恢复 Koyura 编译并修复书架

在 `/Users/kurisu/project x` 完成实现并运行应用层全量 JVM 回归。以当前工作区为基线，HEAD 为 `0962fd0`，已有未提交的 UI 改动和未跟踪的图标资源。开工先看 `git status` 和相关 diff。保留已有改动和资源。若使用独立工作树，先带上这些改动和资源，并用 `-PveneraReferenceDir` 指向主工作区的 Venera-Next 参考目录。审阅报告见 `docs/reviews/2026-11-18-functional-gap.md`。

## 交付行为

1. **恢复编译。** 修复 `shared/app/src/commonMain/kotlin/browse/SourceScreens.kt:694` 中 `Column` 以内隐式访问外层 `BoxWithConstraints.maxWidth` 的错误。保留宽屏封面的尺寸计算和现有 UI 改动。先确认 `:shared:app:compileKotlinJvm` 通过。

2. **隐藏分类。** `showHiddenCategories=false` 时，隐藏分类页签和仅归属隐藏分类的作品从书架 “全部” 消失。无分类作品及同时属于可见和隐藏分类的作品仍可见。搜索、分组、计数和全选都使用同一可见范围。打开开关后可查看隐藏分类。若当前页签随后被隐藏或删除，回到 “全部” 并清理已不可见的选中项。收藏身份、分类归属及历史页收藏状态保持完整。参考 `LibrarySession.visibleEntries/tabs`、`LibraryListing.kt`、`LibraryStore.listEntries` 的现有规则。

3. **真实章节已读。** 补齐生产的章节目录和已读状态存储，通过成功取得的源目录接入正常收藏/详情路径，按 `sourceKey + kind + workId + chapterId` 隔离。书架批量标记已读/未读写入实际章节状态，详情章列表读取同一状态，现有详情批量标已读入口同步接线。进入或翻过部分页面只更新进度，读到末页时记录已读。章节总数、未读数、未读筛选与角标基于真实状态。重复刷新目录保留同 ID 的状态，新章默认未读，失败或取消保留上次有效目录。老收藏缺目录时可按需补齐，无法取得目录要给可恢复错误。批量写入保证一致性，手动标记保留现有页键、页码、阅读历史及其隐藏状态。老库通过项目现有 SQLDelight 迁移与启动补表机制升级，覆盖首次创建和升级后重开，保留旧进度、收藏、分类与偏好。入口在 `ProductionLibraryRepository.markAsRead`、`VeneraBrowseService.loadWorkDetail`、`VeneraReaderService`、`HistoryStore` 和 `ExtensionSession.ensureSchema`。

4. **显示设置。** 持久化 `showUnreadBadges`、`showDownloadBadges`、`showLanguageBadges`、`showContinueButton`、`showCategoryLabels`、`showHiddenCategories`、`showWorkCount`。通过正常设置界面修改后立即生效，重开应用仍保留。旧库缺字段或单项非法值按相应默认值恢复，已有布局、列数、筛选、排序、分组不丢失。检查连续修改多个开关不会互相覆盖。入口在 `LibrarySheets.kt`、`LibrarySession.updatePrefs`、`ProductionLibraryRepository.savePrefs/parsePrefs`。

5. **从书架开始和继续阅读。** 新收藏且未读的作品可从书架开始。无分章作品使用既有 `chapterId=null` 契约，正常进入阅读、保存后重开，从书架恢复同一页；开始和继续的显示应区分无历史与 `chapterId=null` 的既有进度。已有普通章节的续读、详情和历史入口保持可用。区分有效无分章目录、请求失败和原章节从目录消失，错误时保留旧进度并提供反馈。入口在 `LibraryEntry.canContinue`、`LibraryTabContent` 和 `VeneraReaderService.openChapter`。

审阅还发现空书架的浏览引导回调为空。把已有「浏览」按钮接到 Browse 标签，隐藏或禁用暂无目的地的全局搜索和指南入口。范围限于编译和书架，全局搜索、下载、更新、迁移、追踪和小说另行处理。

## 验证与交付

优先复用 `VeneraLibraryProductionRestartTest`、`VeneraLibraryHistoryPersistenceTest`、`LibraryStoreTest`、`LibraryMigrationTest` 和 `ReaderFlowTest` 的夹具。新增能防止此次回归的断言，至少覆盖以下场景。

- 隐藏分类在默认 “全部” 和搜索中不会泄露，混合分类与无分类仍可见。切换开关和重开后结果正确，历史收藏状态不变。
- 同源不同作品及不同源同 ID 状态隔离。批量标记、详情显示、未读统计和筛选在实际生产仓储中一致，重开可恢复。阅读中途、读完、后续新增章节和老库升级分别有可核查证据。
- 七个开关经真实仓储保存并重读，旧偏好兼容。无分章作品从书架进入，翻到非首页后重开同一磁盘资料目录，并通过书架按钮恢复同一页。保留普通章节续读回归。

在主工作区用 JDK 21 执行下列命令，应用层不加 `--tests` 过滤。独立工作树替换参考目录路径。`--rerun` 放在应用 JVM 任务后，确保实际重跑整个应用层测试任务。

```sh
./gradlew :shared:domain:jvmTest :shared:features:jvmTest :shared:data:jvmTest \
  :shared:app:jvmTest --rerun --console=plain \
  -PveneraReferenceDir="$PWD/reference/Venera-Next"
```

若回归失败，定位、修复并重跑。交付时列出改动文件、数据迁移策略、关键行为、测试命令和用例通过/失败/跳过数，附日志或测试报告路径。说明工作树或提交位置以及仍有问题的用例。不要以假仓储、历史报告或缩小测试范围作为应用层验收。
