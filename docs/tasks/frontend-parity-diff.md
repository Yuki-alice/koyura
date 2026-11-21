# 前端复刻差异核对（T4.2）

对照方式：`VisualParityMatrixTest` 产出 18 页 × 窄(360×800)/宽(1280×800) × 浅/深 = **72 张 PNG**（`shared/app/build/screenshots/visual-<slug>-<form>-<theme>.png`），
与 `docs/design-whitepaper/assets/phone|tablet` 的同名参考图逐页比对。手机图 40 张、平板图 40 张，其中与本轮 18 个 slug 对应的共 21 组。

> 说明：任务书写的是「37 手机 + 37 平板」，实际白皮书目录为 40 + 40；本轮矩阵按 18 个页面 slug × 2 形态覆盖，**不是** 逐张资产一对一（多张参考图属于同一页的不同滚动位置）。未逐张核对的资产在文末登记。

## 1. 抽样核对结果（本轮实际逐张看过）

| 页面 | 参考图 | 结论 | 差异 |
| --- | --- | --- | --- |
| 引导 Onboarding | `phone/01_onboarding_theme.jpg` | 已修 | 原实现底部仍挂底栏且按钮悬空：本轮改为全屏（`hideChrome` 纳入 `OnboardingRoute`）+ 内容区滚动、Skip/Next 贴底带 1px 分隔线。仍未做「首启自动进入」判断（无首启持久化标记）。 |
| 书架 Library | `phone/10_library_grid.jpg` | 部分 | 网格列宽已按 162dp@360 / ≈120dp@1280 校正；**徽标仍是文字长条**（`3 unread`/`2 downloaded`），原版是左上角 18dp 未读方块 + 右上角下载文件夹图标；原版标题叠在封面内，我们标题在封面下方。 |
| 浏览 Browse | `phone/06_browse_sources.jpg` | 部分 | 四 Tab 在 360dp 等分会折行，已改可滚动 Tab；搜索框已固定 50dp 全圆；源行副标题改为语言母语名（`zh→中文`），**原版是国旗 + 语言**，但 `SourceInfo` 无国家字段，不猜国旗。源浏览分区（Popular/Latest 横卡）未实现——`BrowseService` 只有 `searchWorks`，没有分区接口。 |
| 详情 Detail | `phone/11_manga_detail_library.jpg` | 对齐 | 双 alpha 顶栏、100×150 封面、动作行高亮规则、描述 24dp 渐变遮罩、标签 chip、26dp 下载指示器均已落地。 |
| 阅读器 Reader | `phone/13_reader.jpg` | 对齐 | 页码双层描边（12sp Bold + 1sp 字距，`DesignTokens.Type` 固化）、chrome 95/90% 半透明。 |
| 历史 History | `phone/15_history.jpg` | 部分 | 行高 96、封面 53×80、`● 章节 · 时间 · 页码` 单行、分组头 Today/Yesterday/日期 均对齐；**行尾比原版多一个 Play 按钮**（原版只有删除）。保留 Play 是因为既有测试以它作为「继续阅读」入口。 |
| 更新 Updates | `phone/16_updates.jpg`（空态） | 对齐 | 空态换 `Σ(ಠ_ಠ)`、斜体 `Last updated in this session` 行（仓储无时间戳，只声明本次会话）、按作品分组。 |
| 更多 More | `phone/17_more.jpg` | 对齐 | Logo、开关行、7 导航行、社交图标齐全；本轮新增 3 个工具页入口后列表变长，金色采样点因此调整（见 §3）。 |
| 设置 Settings | `phone/18_settings_main.jpg` + `tablet` 双栏 | 已修 | 平板左栏 320dp → **450dp**；外观页主题卡由方形改为 **114×202.7dp（9:16）**；卡片色块撑满。左栏选中行 24dp 圆角 + surfaceVariant 已就位。 |
| 统计 Statistics | `phone/30_statistics.jpg` | 已修 | 卡片圆角 16 → **28dp**、组标题 titleMedium → titleSmall、标签 11sp、图标 24dp 置底、整页横内边距 16 → 32dp。 |
| 分类 Categories | `phone/31_categories.jpg` | 对齐 | `primaryContainer` 扩展 FAB；拖拽排序以「上移/下移按钮」替代（任务书允许）。 |
| 下载队列 | `phone/32_download_queue.jpg` | 已修 | 空态颜文字 `(˘･_･˘)` → `(︶︿︶)`。 |
| 更新错误 | `phone/16_updates.jpg`（近似） | 对齐 | 真实 `UtilityPageRoute(update-errors)`，读 `UpdatesRepository.failures()` 并可重试。 |
| 关于 / 新功能 | `phone/27_settings_about.jpg` | 对齐 | 真实 `UtilityPageRoute(about)`，Logo + 版本 + 分隔 + 社交行。 |
| Feed | `phone/29_feed.jpg` | 对齐 | 读 `UpdatesRepository` 的更新流，18+ 开关走全局设置。 |
| 迁移 Migrate | `tablet/36_migrate.jpg` | 部分 | 列书架条目所在源并可跳源；跨源自动迁移在契约里恒不可用（`migrationSupported=false`），不做假迁移。 |
| 存储用量 | `phone/25_settings_data_storage.jpg`（近似） | 对齐 | 真实工具页，读书架快照计数；无缓存层时如实说明。 |
| 次级工具页 | `phone/35_library_filter_sheet.jpg`（近似） | 对齐 | 以 `GlobalSearchRoute` 承载，白皮书无同名图。 |

## 2. 已知未做（不在本轮范围或数据层不支持）

| 项 | 原因 |
| --- | --- |
| 书架徽标 BadgeGroup 形态（18dp 方块 + 图标） | 当前徽标是文字胶囊，改动会牵动 5 个既有断言；列为下一轮首要项。 |
| 书架卡片标题叠在封面内 | 需重排卡片内部层级；当前标题在封面下方，信息不丢失。 |
| 浏览源首页分区（Popular/Latest 横卡 96/205dp + ExtendedFAB） | `BrowseService` 无分区接口，需要数据层先支持。 |
| 权限横幅（16dp 居中两行） | 无「源需要权限」语义来源。 |
| 主题卡宽度 114dp 精确值 | 卡片实测约 92dp（`AppCard` 内边距 + 6 卡排布），比例已 9:16。 |
| 引导首启自动进入 | 无首启标记持久化；当前仅「设置 → 关于 → 重看引导」可达。 |
| 下拉刷新（书架 1s 假状态） | 项目无下拉刷新实现，新增需引入 `PullToRefresh`（material3 实验 API），本轮未做。 |

## 3. 断言侧的两处必要调整（不是放宽标准）

1. **宽屏左下采样点** `x=12%` → `2%`：更多页新增工具页入口后列表延伸到底部，12% 处会压到正文文字（`0x0-6b6c69`）。改为落在 rail 容器内（rail 全高覆盖），rail 本身仍由「区域非背景比例」断言覆盖。
2. **全屏页新增 `ChromeExpectation.None`**：引导页现在无底栏/轨，仍要求「底栏区域有内容」会误报；`None` 会跳过该区域断言，只保留尺寸/主题色/非背景比例/不透明四条。

## 4. 未逐张核对的资产

`phone/02-05`（引导后续三步）、`07_local_source_list`、`12_chapter_list`、`14_reader_menu`、`17b/18b/19b`（滚动位置变体）、`19-26`（设置各子页）、`28_extensions`、`33/34`（深色变体）、`35_library_filter_sheet`、`37_chapter_filter_sheet`、以及 `tablet/` 下除 `36_migrate` 外的全部。

这些页面本轮没有结构性改动，或只在 72 张矩阵里以「近似 slug」覆盖（设置子页 → settings 双栏、深色 → `-dark` 变体）。逐张核对留给下一轮，避免把没有改动的页面当成已验收。
