# 前端视觉对齐交付报告（最新一轮）

分支 `feat/visual-parity`，地盘 `.zcode/worktrees/visual-parity`，基线 `main` @ `c729ff6`。
本报告为覆盖写，只反映本轮；截图与生成脚本产物不入库，不建证据归档。
本轮完成：P0-1 主题机制、P0-2 组件包装（业务层替换未完成）、P0-3 视觉验收基建、P0-4 设置页与路由接线，以及部分 P1 项。DoD 未全部达成，逐项如实登记在第 5 节。

## 1. 改动清单

| SHA | 提交主题 | 影响面 |
| --- | --- | --- |
| `ad532c7` | `build(design-system): 引入已批准的主题与图片库依赖并挂到消费模块` | 版本目录 + design-system / features / app 三个消费模块 |
| `ae951e4` | `feat(design-system): 补齐 19 套静态主题、明暗模式与封面取色入口` | 新增 theme 包与 8 个单测，ReaderTheme 常量可见性 |
| `50081db` | `build(design-system): 让 materialkolor 对消费模块以 api 可见` | design-system 依赖可见性 |
| `f9acea9` | `feat(design-system): 建 Komikku 等价组件包装` | components/ 10 个组件 |
| `cad6fd1` | `feat(settings): 补齐设置页十个子页与主题偏好契约` | features/settings 7 文件 |
| `2465377` | `feat(shell): 接线设置页路由与 More 六个工具入口` | AppRoute / AppScaffold / MoreScreen / UtilityPage |
| `fc8751f` | `feat(browse): 补全浏览四 tab 与 18+ 开关占位面板` | BrowseModels / BrowseScreen / BrowseExtraPanes |
| `f748662` | `feat(reader): 对齐详情左栏、历史封面与书架空态` | SourceScreens / HistoryScreen / LibraryScreen |
| `bbf14ba` | `test(app): 建 15 页四态截图矩阵与 golden-diff 断言` | jvmTest/visual 3 文件 |

本报告自身为最后一个提交，SHA 不自指，以 `git log feat/visual-parity` 为准。

### 依赖基座

- `gradle/libs.versions.toml` 新增 4 个已批准坐标：`material-kolor 5.0.1`、`coil-compose 3.6.3`、`coil-network-ktor3 3.6.3`、`zoomimage-compose 1.7.0` 及传递依赖；**未升降 Kotlin / Compose / material3 版本**（`compose = 1.13.0-beta02` 原样）。
- 挂载：design-system → materialkolor（`api`，因为 `ThemeSelection` / `VisualTheme` 的公开签名含 `PaletteStyle`）+ coil-compose；features → coil-compose + zoomimage-compose；app → coil-compose + coil-network-ktor3。design-system 的 `commonTest` 挂既有 `kotlin-test`（非新增坐标）。
- 首次联网 `compileKotlinJvm` 通过；离线三端构建第一次因 Android AAR / iOS klib 变体未缓存失败，联网跑完整三端构建补齐缓存。**materialkolor 与 material3 1.13.0-beta02 二进制兼容已验证**（`DynamicMaterialExpressiveTheme` 带 CMP `MotionScheme`），未触发降级路径。
- `coil-network-ktor3` 与 `zoomimage-compose` 已挂载但本轮无代码消费，留给 P1-3 封面 Fetcher / P1-2 阅读器缩放；如后续不用应移除挂载。

### P0-1 主题机制（`shared/design-system/src/commonMain/kotlin/theme/`）

| 文件 | 内容 |
| --- | --- |
| `ThemePreset.kt` | `ThemeMode`（LIGHT/DARK/SYSTEM）、19 套静态主题枚举、`amoledColorScheme` 覆写、纯函数 `resolveColorScheme` |
| `StaticColorSchemes.kt` | 19 套浅/深完整色板（1315 行），由白皮书 `assets/design-tokens.json` 生成，保留主题名与「定义角色数 / 缺失角色」注释 |
| `VisualTheme.kt` | 照 Komikku `TachiyomiTheme` 两个重载：静态分支 `MaterialExpressiveTheme`；seed 分支 `DynamicMaterialExpressiveTheme(seedColor, isDark, isAmoled, style = Vibrant, animate = true)`；`seedColor == null` 回退静态主题 |
| `CoverSeedColor.kt` | `QuantizerCelebi.quantize` + `Score.score` 提取封面 vibrant 主色，纯函数，不碰网络与按源会话 |
| `SeedColorScheme.kt` / `ThemeSelection.kt` | 可单测的色板决议映射与主题设置能力契约 |

`ReaderTheme.kt` 仅把三套既有色板常量由 `private` 改为 `internal`，色值一字未改；单测逐角色断言生成的 DEFAULT 色板与既有三套完全一致。
生成器 `build/theme-gen/generate_static_schemes.py`（`build/` 下，不入库）：读 `design-tokens.json`，按参考实现 `TachiyomiTheme.colorSchemes` 顺序映射枚举，输出 `lightColorScheme(...)` / `darkColorScheme(...)`，并同步生成单测期望值表。

### P0-2 组件层（`shared/design-system/src/commonMain/kotlin/components/`）

新建 10 个 Komikku 等价包装：`ReaderScaffold`、`ReaderTopAppBar`（scrollFraction 渐显）、`ReaderTabs` / `ReaderPillTab`、`ReaderSheet`（手机抽屉 / 平板 460dp 居中卡片 28dp 圆角）、`ReaderSlider`（圆点药丸 + 两端页码）、`ReaderBadge` / `ReaderPill`（18dp）、`SettingsItem` / `PreferenceGroup`（48dp + 24dp 选中态）、`EmptyScreen`（12 颜文字 + 解释 + 动作）、`FastScroller`、`CoverAspect`（2:3 固定比例）。规格数值取自 `DesignTokens` 与白皮书 06 / 03 / 9.5。
**业务层尚未替换裸 M3**：`grep -rn "^import androidx.compose.material3" shared/features shared/app` 仍有 186 处，白名单检查未通过，属本轮最大未完成项。

### P0-3 视觉验收基建（`shared/app/src/jvmTest/kotlin/visual/`）

- `VisualParityMatrixTest.kt`：15 个页面用例 × 窄 360×800 / 宽 1280×800 × light / dark = **60 张确定性 PNG**，输出 `build/visual-parity/`。固定 `Density(1f)`、固定主题、固定 Fake 数据源与固定颜文字；未实现的页面（Onboarding / Settings 对照键 / 更新错误 / 关于）渲染明确标注的占位画面，不假装有真实页面。
- `GoldenDiffAssertions.kt`：每张图都做真实断言 —— 画布尺寸、像素不透明度、6 点主题色采样（容差 ±12）、非背景像素比例 > 1%（±6）、窄屏底栏区域 / 宽屏 rail 区域非背景内容 > 1%；阈值与平台字体 / 抗锯齿差异说明写在文件头注释。**无零断言截图**。
- `VisualMatrixCatalog.kt`：15 页 slug 与白皮书 `assets/phone|tablet` 截图名的对照键。

### P0-4 设置页与接线

- `shared/features/src/commonMain/kotlin/settings/` 7 个文件：契约（`SettingsSubpage` 10 项 / `SettingsPreferences` / `SettingsUiState` / `SettingsRepository`）、`SettingsSession`、`FakeSettingsRepository`、`SettingsScreen`（master-detail，平板 24dp 圆角 + surfaceVariant 选中态）、`AppearanceSettingsScreen`（模式分段 / 19 套主题卡 / AMOLED / 封面取色 / `CoverBlendStyle`）、`SettingsSubpages`（8 个其余子页）。**只接 Fake，未接生产持久化**。
- `AppRoute.kt` 新增 `SettingsRoute` / `SettingsSubpageRoute` / `UtilityPageRoute` 并注册到 `ReaderSavedStateConfiguration`；`AppScaffold.kt` 共享一份 `SettingsSession`，手机 push 子页、平板双栏共用状态。
- `MoreScreen` 6 个原本不可点的行全部接线：设置 → 真实设置页；更新错误 / 数据与存储 / 批量添加 / 关于 / 帮助 → `UtilityPageScreen`（64dp 顶栏 + 颜文字空态 + 返回出口）。
- 外观子页的调色风格用 `CoverBlendStyle`（保留 `paletteStyleName`），避免 features 直接依赖 materialkolor；生产桥接时用 `PaletteStyle.valueOf(name)` 还原。

### 已推进的 P1 项

- 详情页平板左栏由 `weight(0.42f)` 改为 `min(width/2, 450.dp)`，右栏 `weight(1f)`（9.5 #07）。
- 历史行封面 56×80 → 63×94，填满行高。
- 书架空态补固定颜文字（9.5 #17），保留原有文案与全部测试锚点。
- Browse 由 2 tab 补为原版 4 tab（Sources / Feed / Extensions / Migrate），新增 Feed / Migrate 占位面板与 18+ 开关（仅页内 Fake 状态，数据接线不在本轮）；全局搜索独立入口本就存在。

## 2. 逐页对照表

截图由 `VisualParityMatrixTest` 一次产出 60 张，命名 `visual-<page>-<narrow|wide>-<light|dark>.png`；白皮书对照物为 `docs/design-whitepaper/assets/phone/`（360dp）与 `assets/tablet/`（1280×800dp）同名截图，索引见 `10-附录.md` B 节与 `VisualMatrixCatalog.kt`。本轮未做人工逐像素比对（按 `docs/verification.md` 属按需人工核对），差异栏据任务书 4.1 审计基线与本轮改动填写。

| 页面 | 白皮书截图 | 本实现截图 | 差异 | 处置 |
| --- | --- | --- | --- | --- |
| 引导 Onboarding | 07 对应小节 | `visual-onboarding-placeholder-*` | 页面未实现，占位 | 后续轮实现 |
| 书架 Library | 同上 | `visual-library-*` | 已补空态颜文字；已读/未读 alpha、选中态、Coil 淡入仍缺 | P1-3 |
| 浏览 Browse | 同上 | `visual-browse-*` | 已补 Feed / Migrate 两 tab 与 18+ 开关；行副标题仍退化 | P1-4 / P2 |
| 详情 Detail | 同上 | `visual-detail-*` | 左栏已 450dp 封顶；顶栏不沉浸、无封面取色、缺 Suggestions | P1-1 |
| 阅读器 Reader | 同上 | `visual-reader-*` | M3 Slider 页码条、默认背景 AUTOMATIC、未隐藏系统栏 | P1-2（含禁改区） |
| 历史 History | 同上 | `visual-history-*` | 封面已 63×94；多余行内动作、阅读时间、未读点未处理 | P1-5 |
| 更新 Updates | 同上 | `visual-updates-*` | 结构对齐，筛选/日历禁用 | P2 |
| 更多 More | 同上 | `visual-more-*` | 6 行已可点接路由/占位；开关反馈仍为页内状态 | P1-6 |
| 设置 Settings | 同上 | `visual-settings-placeholder-*` | **矩阵仍渲染占位**，但真实设置页已接线到 More | 下一轮把矩阵用例换成真实 `SettingsRoute` |
| 统计 Statistics | 同上 | `visual-statistics-*` | 四卡结构对齐，数据为 Fake（不在本轮修） | 数据接线 |
| 分类 Categories | 同上 | `visual-categories-*` | 56dp 行/拖拽/对话框齐 | P2 |
| 下载队列 | 同上 | `visual-download-queue-*` | 骨架在但数据 Fake，行形态与原版高密度行不同 | P2 |
| 更新错误 | 同上 | `visual-update-errors-placeholder-*` | 占位页已可点可返回，独立数据页未接 | 后续轮 |
| 关于 / 新功能 | 同上 | `visual-about-placeholder-*` | 占位页 | 后续轮 |
| 次级工具页 | 同上 | `visual-secondary-tools-*` | 仓库管理 / 扩展详情有 | P0-4 已补工具占位 |

## 3. 9.5 的 30 条清单：改动前 → 改动后

结论：通过数由 **8 → 11**（新增 #07 / #17 / #27 在代码层达标），仍远低于目标 ≥26。代码层达标不等于截图人工核对完成，未达项逐条写理由。

| # | 检查项 | 改动前 | 改动后 | 说明 |
| --- | --- | --- | --- | --- |
| 01 | 语义令牌 | 部分 | 部分 | 217 处硬编码 dp 未收拢，P2 |
| 02 | 亮暗双模 | 通过 | 通过 | 扩展到 19 套静态主题，8 个单测覆盖 |
| 03 | AMOLED 导航栏非纯黑 | 部分 | 部分 | 覆写机制与 `#0C0C0C` 单测已具备；导航栏消费待 P0-2 替换 |
| 04 | 360dp 书架 2 列 | 通过 | 通过 | 未触及 |
| 05 | 1280dp 书架 9 列 | 部分 | 部分 | 实测约 8 列，P1-3 |
| 06 | 720dp 切侧栏 | 通过 | 通过 | 未触及 |
| 07 | 左栏 450dp 封顶 | 未达标 | **通过** | `min(width/2, 450.dp)` 已落地；宽屏截图可复核 |
| 08 | 平板弹层 460dp | 部分 | 部分 | `ReaderSheet` 组件已建，业务层未接 |
| 09 | Tab fadeThrough | 通过 | 通过 | 未触及 |
| 10 | 页面转场方向 | 部分 | 部分 | P0-2 |
| 11 | 顶栏滚动渐显 | 未达标 | 部分 | `ReaderTopAppBar(scrollFraction)` 已建，详情页未接 |
| 12 | 阅读器栏 200/150ms | 通过 | 通过 | 未触及 |
| 13 | 未读/已读对比 | 未达标 | 未达标 | P1-3 |
| 14 | 徽标 18dp | 通过 | 通过 | 未触及；`ReaderBadge` 已封装 |
| 15 | 封面比例固定 | 通过 | 通过 | 未触及；`CoverAspect` 已封装 |
| 16 | 选中态即时 | 部分 | 部分 | P1-3 |
| 17 | 空状态 | 部分 | **通过** | 书架补颜文字，更新页原有；`EmptyScreen` 统一 |
| 18 | 全局横幅顺序 | 未达标 | 未达标 | P1-6 未做 |
| 19 | 返回优先关弹层 | 部分 | 部分 | P0-2 |
| 20 | 触摸目标 ≥48dp | 部分 | 部分 | 新组件均满足，既有页面未全量核查 |
| 21 | 图标可读标签 | 部分 | 部分 | 仍有 11 处 `contentDescription = null`，P2 |
| 22 | 长按 Tooltip | 未达标 | 未达标 | P2 |
| 23 | RTL 控件 | 部分 | 部分 | P2 |
| 24 | 图片 300ms 交叉淡入 | 未达标 | 未达标 | Coil 已挂载，Fetcher 未接（P1-3） |
| 25 | 下载进度可读 | 未达标 | 未达标 | P2 |
| 26 | 多选数量正确 | 通过 | 通过 | 未触及 |
| 27 | 平板设置选中态 | 未达标 | **通过** | `SettingsScreen` 双栏 + 24dp 圆角 + surfaceVariant |
| 28 | 主题切换无残留 | 未达标 | 部分 | `ThemeSelection` 契约 + `VisualTheme` 入口 + 设置页 UI，未接生产偏好 |
| 29 | 字体缩放 200% | 部分 | 部分 | P2 |
| 30 | 性能 | 部分 | 部分 | P2 |

## 4. 截图与复现命令

截图目录：`build/visual-parity/`（60 张 PNG，不入库）。对照键见 `VisualMatrixCatalog.kt`；白皮书截图在 `docs/design-whitepaper/assets/phone|tablet/`。

```bash
export JAVA_HOME="$HOME/devtools/jdk-21"
cd .zcode/worktrees/visual-parity

# 主题单测（8 用例）
./gradlew --offline :shared:design-system:jvmTest

# 60 张视觉矩阵 + golden-diff
./gradlew --offline :shared:app:jvmTest --tests '*VisualParityMatrixTest*' \
  -PreaderScreenshotDir="$PWD/build/visual-parity" \
  -PveneraReferenceDir="$PWD/reference/Venera-Next"

# 全量门（连续两遍，第二遍 --rerun-tasks）
./gradlew --offline :shared:domain:jvmTest :shared:features:jvmTest :shared:data:jvmTest \
  :shared:source-runtime:jvmTest :shared:app:jvmTest \
  -PveneraReferenceDir="$PWD/reference/Venera-Next" \
  -PreaderScreenshotDir="$PWD/build/visual-parity"
./gradlew --offline --rerun-tasks :shared:domain:jvmTest :shared:features:jvmTest :shared:data:jvmTest \
  :shared:source-runtime:jvmTest :shared:app:jvmTest \
  -PveneraReferenceDir="$PWD/reference/Venera-Next" \
  -PreaderScreenshotDir="$PWD/build/visual-parity"

# 三端构建
./gradlew --offline --continue :apps:android:assembleDebug :apps:desktop:classes \
  :shared:app:linkDebugFrameworkIosSimulatorArm64
```

**golden-diff 机制与阈值**：不依赖像素基线文件（跨平台字体 / 抗锯齿必然有差异），改为对每张 `ImageBitmap` 做结构与颜色断言：画布尺寸相等；像素不透明度；6 个采样点与主题背景色差 ≤12；非背景像素比例 > 1%（容差 ±6）；窄屏底栏 / 宽屏 rail 区块非背景内容 > 1%。阈值偏低是为了只拦截「全空白 / 未渲染 / 主题没生效」这类失败，不冒充逐像素比对；逐像素一致性仍按 `docs/verification.md` 由人工与白皮书截图核对。

**最终验证结果（全部通过）**

- `:shared:design-system:jvmTest`：8 个主题用例全绿。
- 60 张视觉矩阵 + golden-diff：`BUILD SUCCESSFUL`（1m15s），`build/visual-parity/` 实测 60 张 PNG。
- 全量门连续两遍：第一遍 4m05s、第二遍 `--rerun-tasks`，合计 7m49s，`BOTH GATES GREEN`。
- 三端构建：1m56s，`BUILD SUCCESSFUL`（`apps:android:assembleDebug` / `apps:desktop:classes` / `shared:app:linkDebugFrameworkIosSimulatorArm64`）。
- 仅有 Skiko 版本不一致与 deprecated `Slider` 重载警告，无编译错误、无测试失败。

## 5. 未完成项、风险、需裁决 / 接线项

**DoD 逐项**

| # | 要求 | 状态 |
| --- | --- | --- |
| 1 | 全量门连续两遍全绿 | **通过**（7m49s，两遍 BUILD SUCCESSFUL） |
| 2 | 三端构建 EXIT 0 | **通过**（1m56s，BUILD SUCCESSFUL） |
| 3 | 15 页 × 4 态矩阵 + 对照表 + golden-diff | **达成**（60 张 + 对照表 + 全图断言；设置页矩阵仍为占位） |
| 4 | 9.5 清单 ≥26 通过 | **未达**（11 / 30） |
| 5 | 依赖白名单 + 不升降版本 | 部分：版本未动、只引入 4 坐标；material3 import 白名单未通过（186 处） |
| 6 | 图标 contentDescription + 48dp 触摸目标 | 部分：新组件满足，既有 11 处 `null` 未修、既有页未全量核查 |
| 7 | 提交规范 | 达成（按内聚分组，无阶段编号） |
| 8 | 报告覆盖写 | 本文件 |

**未完成项**：P0-2 业务层替换与白名单、P1-1 详情沉浸化与封面取色接线、P1-2 阅读器 chrome 与系统栏、P1-3 书架细节、P1-5 历史细节、P1-6 全局横幅、全部 P2 细节层、P3 的 30 条自查。

**风险与登记**

1. `coil 3.6.3` / `zoomimage 1.7.0` 依赖 `skiko 0.150.1`，CMP 1.13.0-beta02 用 `0.156.1`，Gradle 报版本不一致警告。任务书禁止升降版本，未做处理；需真机 / 桌面运行时验证。
2. 组件层 `ReaderSlider` 与设置页 `SettingsSliderRow` 使用已废弃的 `Slider(value, onValueChange, ...)` 重载（与既有 `ReaderChrome` / `VerticalPageSlider` 一致），仅警告、不影响构建。
3. 开发期排障记录：Kotlin 块注释嵌套（注释里写了 `colorscheme/*.kt`）、`useDarkTheme` 参数名应为 `isDark`、缺 `ExperimentalMaterial3ExpressiveApi` opt-in、`ColorScheme` 无值语义 equals（改为逐角色断言）、`androidx.compose.foundation.layout.weight` 是 internal 不能 import、`Column.() -> Unit` 应为 `ColumnScope.() -> Unit`、CMP commonMain 无 `WindowInsets.statusBars`（改用 `safeDrawing.only(Top)`）。均已修复。
4. 任务书要求的 60 张截图矩阵与逐页对照表，与 `docs/verification.md`「视觉核对按需、不留档案」口径不一致。按任务书执行，产物限定 `build/` 且不入库，仅报告入库；请主 agent 确认是否调整仓库验收口径。
5. 视觉矩阵使 `:shared:app:jvmTest` 每次多渲染 60 张图（约 1 分钟），`--rerun-tasks` 第二遍同样重跑，全量门耗时上升。
6. 设置页矩阵用例仍是占位图，而真实设置页已可经 More 进入；下一轮应把该用例改为 `SettingsRoute` 渲染后再做逐页对照。
7. Android 系统 Monet（`dynamicLightColorScheme` / `WallpaperManager`）为平台专有，本轮未实现；封面取色走 `QuantizerCelebi + Score` 等价路径。
8. `ThemeSelection` / 设置偏好是设计系统契约 + features Fake，主题偏好不会跨页面 / 跨进程生效。

**需主 agent 裁决 / 接线（涉及禁改区或行为契约，本轮未越界）**

- P0-2 业务层 186 处 material3 import 替换（工作量大，需逐页回归）；
- 阅读器默认背景改 Black 的默认值在 `shared/domain/.../ReaderPreferences.kt`（禁改区）；Android 系统栏隐藏需改 `apps/android`（禁改区）；
- 主题偏好生产桥接（features 契约 + Fake → 生产适配）；
- 封面取色接入现有 `LocalCoverImageLoader` 字节管线（不得绕过按源会话直连 URL）；
- Android 平台 Monet 取色的 `expect/actual` 实现；
- 真机 / 模拟器视觉验收与 Skiko 版本警告的运行时确认。

## 6. 下一轮建议

1. **P0-2 收口**：逐页把裸 M3 换成 `components/` 包装，执行 material3 import 白名单检查（仅剩 design-system），这是 30 条里 #03 / #08 / #10 / #11 / #19 / #28 的前置。
2. **P1-1 / P1-2**：详情页接 `VisualTheme(seedColor)`、透明顶栏滚动渐显、Suggestions；阅读器页码药丸滑杆、书签位、默认背景（需裁决 domain）、系统栏隐藏（需裁决 apps）。
3. **P1-3 / P1-5 / P1-6**：书架已读/未读 alpha + 选中态 + Coil 300ms 淡入；历史行精简与未读点；全局横幅顺序与 More 开关反馈。
4. **P0-3 修正**：把设置页矩阵用例换成真实 `SettingsRoute`，并把人工逐页比对结论补进对照表。
5. **P2 / P3**：无障碍 11 处 `contentDescription`、48dp 复核、长按 Tooltip、217 处硬编码 dp 收进 `DesignTokens`、双语化，最后按 9.5 逐条自查冲 ≥26 通过。
