# 前端表现层复刻（第二版白皮书）交付报告

- 基线：`main@4344a23`（夜间批次已并入）
- 工作分支：`feat/frontend-parity-v2`（20 个提交）
- 规划：`docs/tasks/frontend-parity-v2-track.md`
- 差异核对：`docs/tasks/frontend-parity-diff.md`
- 复刻依据：`docs/design-whitepaper/` 第二版（7667 行 / 83 张截图 / 4 个复算脚本），参考实现钉死 `komikku@290267dd`

## 1. 结论

T0 地基、T1 组件配方、T2 页面差异（11 项中 9 项落地、2 项受数据层限制）、T3 状态与反馈、T4 验收基线（矩阵扩到 18 页、尺寸采样、双端截图核对、构建）全部按顺序推进完毕。

**没有做到的**：
- Android / iOS 构建本环境无法验证（见 §4），只有 desktop 通过。
- 浏览源首页分区、书架徽标 BadgeGroup 形态、引导首启自动进入、下拉刷新四项未落地，理由逐条登记在差异文档 §2。

## 2. 逐项状态

### T0 地基（4/4）

| 任务 | 结果 |
| --- | --- |
| T0.1 令牌纠错 | 6 处错值全部改正并改齐引用点：`UnreadDot 8`、`DownloadIndicator 26`、`DownloadProgressStroke 13`、`ThemePreviewCard 114`、`DetailCoverMaxWidthPhone 100`、`LibraryListCoverSize 40`；新增 `DownloadArrow 19`、`ProgressBarStroke 2`、`PageSharedAxisMs 300`、`Type.PageIndicator*`。`DesignTokensTest` 固化数值。 |
| T0.2 主题三路 + AMOLED | 三路（Static/Monet/Custom）与 AMOLED 五档容器规则经既有测试复核；`compare_tokens.py` 0 差异。 |
| T0.3 单一断点 | 720dp 只在 `ProvideSmallestWidth` 一处；底栏改满宽自绘（去掉 M3 两端 8dp 空隙），内容高 80dp；Rail 宽 80dp。 |
| T0.4 转场与 AppBar | 页面转场改手写 shared-axis-X（位移 1/3、退场先淡出、进场延后淡入）——CMP material3 1.13.0-beta02 没有 `materialSharedAxisX*` API；AppBar 三态与标题单行 22sp 落地。 |

### T1 组件配方（8/8）

| 任务 | 结果 |
| --- | --- |
| T1.1 选中态与徽标 | `Modifier.selectedBackground()`（secondary × 0.22/0.16）+ `BadgeGroup`（18dp 整组 clip 4dp、子块 RectangleShape）。徽标**用法**未全站替换（书架仍是文字胶囊，见差异文档）。 |
| T1.2 Pill / Chip | `CountPill(fontSizeSp)`、`TagChip`。书架标题计数胶囊 14sp。 |
| T1.3 三套设置行 | `SettingsRow` 增 `selected`/`twoPane`（平板 24dp 圆角 + surfaceVariant）；新增 `SettingsLegacyRow`（24/10）与 `PreferenceGroupHeader`。 |
| T1.4 下载指示器 | 26dp 直径 / 13dp 描边 / 19dp 箭头 / 5 态。 |
| T1.5 页码与 chrome | 页码双层 Text（底层 `#2D2D2D` Stroke 4f + 上层 primary，12sp Bold + 1sp）；chrome 按亮度 95%/90%。 |
| T1.6 底部动作栏与多选顶栏 | `BottomActionBar`（上圆角 16dp、surfaceContainerHigh）；书架/历史多选顶栏 ✕ + 计数 + 全选/反选。 |
| T1.7 自适应弹层 | 手机底部 Sheet 28dp 圆角 + 下滑 56dp 关闭；平板 460dp 居中卡片。 |
| T1.8 横幅与空态 | `StatusBanner`（INCOGNITO/DOWNLOADED_ONLY）+ `EmptyScreen` 12 个颜文字、强制 LTR、动作上图下文。 |

### T2 页面差异（9/11 落地）

| 任务 | 结果 |
| --- | --- |
| T2.1 书架 | 计数胶囊、分类 Tab 显示条件、四种显示密度、Sheet 四等分 tab、排序箭头、网格列宽（162dp@360 / ≈120dp@1280）。**下拉刷新未做**（项目无此实现）。 |
| T2.2 详情 | 拆两个 alpha、100×150 封面、动作行等分与恒 primary 规则、描述 3 行 + 24dp 渐变、标签 chip、FAB 贴边。 |
| T2.3 章节列表 | 头部 `▤` + 章节筛选 Sheet（ALL/UNREAD/READ/DOWNLOADED/NOT_DOWNLOADED，已下载分支如实恒 false）、未读点 8dp、已读 38%、26dp 指示器 + 40dp 状态层。 |
| T2.4 阅读器 | 页码、chrome 四态、页面横 padding、五种模式保持。 |
| T2.5 历史 | 96dp 行、53×80 封面、`●` 一行、分组头、三入口、空态；行尾直动作（Play + 收藏 + 删除）。 |
| T2.6 更新 | 斜体 Last updated、分组、折叠、顶栏三图标、`Σ(ಠ_ಠ)` 空态。 |
| T2.7 浏览与源浏览 | 可滚动 4 Tab、50dp 全圆搜索框、语言母语名、NSFW 大写 error、`ExtendedFAB`；Feed / Migrate 真实化。**源首页分区未做**（`BrowseService` 无接口）。 |
| T2.8 更多 / 关于 / 工具页 | Logo 64dp、开关行、7 导航行、社交图标；**5 个工具页真实化**（update-errors 读失败集、data-storage 读书架快照、batch-add 逐行加入、about/help 有真实内容）。 |
| T2.9 设置 | 11 行主页、外观分段 + 主题卡 9:16、10 子页、平板双栏 450dp、**12 处空回调全部接线**、**SettingsRepository 生产桥接**（`BridgedSettingsRepository` 双向映射 + `LaunchedEffect` hydrate）。 |
| T2.10 统计 / 分类 / 队列 | 统计卡 28dp、标题 titleSmall、值 22sp、标签 11sp、图标 24dp 置底、横内边距 32dp；分类 `primaryContainer` FAB；队列 `(︶︿︶)` 空态。 |
| T2.11 引导 | **新建**四步（主题/存储/权限/指南）+ 进度条 + 贴底按钮 + 全屏（隐藏底栏）；接入路由与「设置 → 关于 → 重看引导」。首启自动进入未做。 |

### T3 状态与反馈（3/3）

- **T3.1**：书架移出给 Short Snackbar + Undo（先抓快照再删，撤销时 `addToLibrary` + `setEntryCategories` 回填分类）；标记已读/改分类不给撤销；加载居中无骨架。
- **T3.2**：书架/历史多选时隐藏底栏；返回键先清选择（`consumeBack` 顺序有测试）；关闭选择后顶栏复位。更新/详情/源浏览没有多选数据层，未虚构。
- **T3.3**：`GlobalModeBanner`（incognito 优先于 downloadedOnly，overlay 贴顶不改变页面可用高度）。

### T4 验收基线（3/3，含未验证声明）

- **T4.1**：矩阵 15 → **18 页**（新增 Feed、Migrate、存储用量），断言从「6 个边缘采样点」升级为「采样 + 尺寸采样」（书架卡 162/120、左栏 450、平板弹层 460、指示器 26）。
- **T4.2**：`docs/tasks/frontend-parity-diff.md`，逐页差异 + 未核对清单。
- **T4.3**：见 §3、§4。

## 3. 测试与稳定性

| 门 | 结果 |
| --- | --- |
| `:shared:domain:jvmTest` | **13 通过 / 0 失败** |
| `:shared:features:jvmTest` | **101 通过 / 0 失败** |
| `:shared:data:jvmTest` | **136 通过 / 0 失败** |
| `:shared:source-runtime:jvmTest` | **65 通过 / 0 失败** |
| `:shared:app:jvmTest` | **387 通过 / 0 失败**（含 18 页 × 4 态视觉矩阵） |
| 视觉矩阵连续两遍 | 两遍均全绿（`--rerun-tasks` 复跑），72 张 PNG 稳定 |
| `Xvfb :99` | 本环境默认无显示服务，测试前需 `Xvfb :99 -screen 0 1920x1080x24` |

本轮修掉的既有红灯：
- `AppAppearanceUiTest`（主题切换不生效）：`AppAppearance` 显式传 `null` 跳过 `rememberSettingsSession` 的默认值，导致设置写入临时 Fake；改为 `repository ?: LocalSettingsRepository.current`。
- 3 个 Venera 重启测试：行尾动作区/按钮被行合并语义吸收；历史行改为「点击区只包封面+文字」，动作区独立，测试改为直接点常驻按钮。
- `VisualParityMatrixTest.moreMatrix`：更多页新增 3 行后列表延伸到底部，宽屏左下采样点压到正文；采样点移到 rail 内。
- `AppShellDetailUiTest`：章节下载位由 `ActionIconButton` 换成纯绘制状态层，tag 只在未合并树可见；测试改用 `useUnmergedTree`。

## 4. 构建（如实登记）

| 目标 | 结果 |
| --- | --- |
| `:apps:desktop:classes` | ✅ 通过 |
| `:apps:android:assembleDebug` | ❌ **未验证**：环境无 Android SDK（`SDK location not found`），未安装 SDK 也不联网下载 |
| `linkDebugFrameworkIosSimulatorArm64` | ❌ **未验证**：离线环境缺 `kotlin-native-prebuilt:2.4.21`；即使下载，Linux 主机也无法链接 iOS framework（需 macOS + Xcode） |

## 5. 剩余风险

1. 书架徽标形态、卡片标题层级仍与原版有肉眼可见差异（差异文档 §2 第 1–2 条）。
2. 浏览源首页分区、权限横幅依赖数据层能力，当前只能做到「搜索 + 已装源列表」。
3. 引导页只在设置入口可达，未做首启判定。
4. 72 张截图只做了抽样人工核对（7 个页面），其余为矩阵数值断言覆盖。
