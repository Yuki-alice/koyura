# P1 · 外壳与形态切换 · 视觉复核记录

日期：2026-11-18 · 任务书 `docs/visual-parity-plan.md` P1 · 白皮书 03/04 全章 + 06 §6.2

基线：Komikku `383ffaa816`（数值冲突以此为准），白皮书 commit `290267d` 作规格解释。

## 渲染方式

JVM `ImageComposeScene` 离屏渲染（沿用 a-shell 基线做法，非实机）：`Density(1f)`，
420 dp 走底栏、1280 dp 走导航轨，浅色/深色经 `ReaderTheme(darkTheme=…)` 切换。
与原版对照以参考提交源码与布局数值为准，画布尺寸与语言（zh_CN）在两侧保持一致。

## 差距清单与修复

| # | 审计点 | 差距 | 修复 |
| --- | --- | --- | --- |
| 1 | 形态判定（03.1） | `usesNavigationRail(maxWidth)` 用当前宽度，非最小宽度；阈值硬编码 720；旋转（360×720↔720×360）会闪回手机布局；缺自动 700/600 与四种强制模式 | 新增 `navigation/TabletUiMode.kt`：`minOf(width,height)` + `AUTOMATIC(700竖/600横)/ALWAYS/LANDSCAPE/NEVER`，阈值引用 `DesignTokens.Breakpoints`；`AppShell` 默认 `AUTOMATIC`（与 Komikku 新装默认一致）；`RailWidthThreshold` 改由令牌导出，基线 719/720 单参数规则保留 |
| 2 | Tab 切换动画（04.1/4.3） | `NavDisplay` 直接切换，无 fadeThrough | `AppNavigationContent` 包 `AnimatedContent`（key=selectedTab）：`fadeOut(200ms)` + 延迟 200ms 的 `fadeIn(200ms)`，先出后进非交叉淡入；过渡内按 `stackOf(tab)` 取各 Tab 栈 |
| 3 | 返回优先级（04.2） | 已合规，无代码改动 | 核对：子页 pop（NavDisplay）→ 多选消费（Library 选择态）→ 回 Library（非 Library 根）→ 平台；弹层 BackHandler 注册在内容侧（shell 先注册，内容后注册优先），与原版"内容先注册"一致 |
| 4 | Rail/Bar（06 §6.2） | Rail 三个内部常量裸写 `80/4/3dp`；Bar 高度依赖 M3 默认未显式断言；Updates 角标无接线 | Rail 常量改引 `DesignTokens.Sizes/Elevation/Spacing`（值不变）；Bar 80dp 由 M3 `NavigationBar` 默认保证（与原版自定容器同高），截图断言；`AppScaffold` 新增 `updatesBadgeCount`（默认 0，数据源随 P7 更新页落地），Browse 角标已接通（截图见 `p1-browse-*-420-light` 右下 `1`） |
| 5 | reselect（04.1） | 同 Tab 点击只透出事件，不断栈：详情子页上重按 Library 停留在子页 | `select()` 同 Tab 时先 `popToRoot()` 再发射事件（Library 回首页、Updates 刷新等由各屏事件处理）；栈已在根时行为不变 |
| 6 | 顶栏三态（04.4） | 外壳层合规 | Browse/History `TopAppBar` 容器色= surface（常驻实色 surface@0dp）；详情滚动渐显归 P3，阅读器透明归 P6 |
| 7 | 触摸目标与标签 | 合规为主；桌面 Tooltip 曾尝试 `TooltipBox` | M3 `IconButton` 自带 48dp 最小触摸；全部 `ActionIconButton` 带 label→contentDescription，导航图标/角标均有可访问文本。`TooltipBox` 方案已回退：其 Popup 即使隐藏也会产生第二个语义根，导致全仓 `onRoot().captureToImage()` 截图 helper 失败（`VeneraLibraryHistoryMutationRestartTest` 双根 `420×760` + `1024×768`），Tooltip 推迟到测试 helper 兼容后再做 |
| 8 | insets | 合规 | Android `enableEdgeToEdge` 已接；`AppScaffold` 按归属拆分消费（bar 横向+底部、rail 纵向+起始、内容仅垫剩余边）；阅读器全屏分支保持，scrim 归 P6 |

## 验收

- 白皮书 03 章复刻检查清单：720（AUTOMATIC 下 700/600）处底栏/Rail 切换无中间态；1280 详情左栏上限归 P3；360 书架 2 列/1280 9 列归 P2；手机弹层/平板卡片归 P4；edge-to-edge 内容不被遮挡；旋转（700×1000↔1000×700）最小宽度不变不闪回（新增测试）；全局横幅归 P5。
- 白皮书 04 章复刻检查清单：非 Library 返回先回 Library；弹层/多选优先于 Tab 返回；普通页 push/pop 方向由 Navigation3 处理；Tab 切换 fadeThrough（本轮新增）；各 Tab 滚动位置经 saveable holder 保留（`AppShellStateRetentionTest` 全绿）；阅读器退出回详情归 P6；详情顶栏渐显归 P3。
- 截图矩阵（`docs/alignment/p1-shell/screenshots/`）：外壳四形态 + 浏览两页 × 窄/宽 × 浅/深共 12 张；与白皮书 `assets/phone/10_library_grid`（底栏 5 项/选中药丸）、`assets/tablet/11_manga_detail`（Rail 居中）、`assets/crops/nav-bar.jpg`（80dp/5等宽/secondaryContainer 药丸）逐项核对一致。Browse 角标 `1` 在窄/宽截图均可见。
- 测试：`:shared:design-system:jvmTest` NO-SOURCE 通过；`:shared:app:jvmTest` 261→263 全绿（含新增窗口规则/旋转/reselect 回根）；`:shared:features:jvmTest` 全绿。
