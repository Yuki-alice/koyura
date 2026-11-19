# UI 轨交付报告（最新）

分支 `feat/ui-parity`。本轮完成任务一（五页视觉收敛）与任务二（Updates / More 真实页面）。

## 1. 本轮改动清单

| 提交 | 内容 |
| --- | --- |
| `fix(design-system): 封面圆角与详情背景按白皮书数值收敛` | 封面统一圆角 8dp→extraSmall(4dp)（Code: MangaCover.kt 默认 shape）；详情背景按 MangaInfoHeader.kt 补 surfaceTint 40% 衬底、7dp 模糊、0.2 透明度，渐隐起点改 50% 高度；新增令牌均带 Code 出处 |
| `fix(library,reader): 徽标与控制条动效走设计令牌并修正错档` | 徽标改 extraSmall 圆角 + bodySmall Medium + 3dp/1dp 内边距（Code: Badges.kt），徽标组距边 4dp；继续阅读钮改 primaryContainer 90% 填充钮（Code 小尺寸档 28dp/16dp）并去硬编码白字；列表多选罩改 secondary 16/22% 叠加；阅读器控制条动效与栏透明度（亮 0.95/暗 0.90）引用 DesignTokens |
| `fix(browse,history): 章节行与详情标签按组件规格收敛` | 详情双栏阈值与章节行内边距引用 DesignTokens；未读点 6dp 改 8dp（Code + 白皮书 06）；类型标签改 M3 SuggestionChip（Code: Chip.kt）；宽屏封面占宽 0.62 改 0.65；历史行高/搜索框边距走令牌，未收藏图标色与浏览页统一为 onSurfaceVariant；扩展副文本色修正 0.5 错档 |
| `feat(design-system): 新增更多页与更新页所需图标与令牌` | 按 MoreScreen.kt 图标映射补 10 个 24dp 字形并注册 ActionIcon；新增品牌占位组件与更新组 48dp 封面宽令牌 |
| `feat(updates,more): 更新分组列表与更多入口页真实结构` | UpdatesSession/UpdatesScreen（领导者行 + 缩进章节行 + EmptyScreen 空态，Fake 仓储默认空）；MoreSession/MoreScreen（品牌区 + 双开关 + 13 行入口 + 赞助按钮）；中英字符串；会话单元测试 |
| `feat(shell): 更新与更多页签接入真实页面并联通徽标` | AppScaffold 挂载新页面并 hoist 会话（切页/换 Rail 保活），更新徽标读会话未读数；作品点开进详情；各页签标题断言与滚动保留测试同步；新增 UpdatesMoreUiTest（空态/分组/点击/开关） |

## 2. 视觉对照结论

对照物：白皮书 phone（360dp）/tablet（1280×800dp）实机截图 + Komikku `290267d` 源码（行为存疑以源码为准）。

| 页面 | 差异项 → 处置 |
| --- | --- |
| 书架 | 网格 Fixed 列数/padding 12（白皮书 Adaptive 128dp/padding 8/gap 4）→ 未动：列数偏好与现有测试耦合，主 agent 裁决；其余（徽标/继续钮/多选罩/封面圆角）→ 已对齐 |
| 浏览 | 来源/扩展行、搜索框 → 已对齐；商店页自定义头（非 TopAppBar）、空态两处文案不一致 → 未动，主 agent 裁决 |
| 详情 | 背景层/徽标/动作态/章节行/标签 → 已对齐；手机封面 96×144（实机 83×125，max 100 内）→ 保留；简介收起行数、ReadingButtons 样式 → 白皮书未覆盖，需裁决 |
| 历史 | 行高/分组/字阶 → 已对齐（字面量改为令牌引用，视觉无变化） |
| 阅读器 Chrome | 动效 200/150 与栏透明度 → 已对齐；根背景仍 `background`（白皮书默认纯黑沉浸）→ 未动：涉及阅读偏好管线，需裁决；竖轨 280/64、页码指示器 12/6 等 → 白皮书未覆盖，需裁决 |
| Updates（新） | 空态与白皮书 16_updates 一致（顶栏三动作 + 居中颜文字）；分组列表按 UpdatesUiItem.kt 行边距实现；筛选/日历禁用待真实引擎；章节点击进详情（直达阅读器待真实源后接）→ 已交付 |
| More（新） | 结构与白皮书 17/17b 一致（品牌区/双开关/分隔线/行列/赞助按钮）；导航行目标页未落地前非交互，下载队列留入口并禁用 → 已交付 |

四态自查：本环境无真机/模拟器运行条件，未做端上四态截图；改为 JVM 离屏渲染自查（360×720 浅/深、360×1000 浅/深、1280×800 深，共 6 张，存 `build/probe` 人工比对后即弃）：新图标 10 个字形均可辨认（云斜线、眼镜、星爆徽标、标签、柱状图、服务器栈、列表+、i、?、日历），空态/分组/品牌区/开关行与白皮书一致；自查后探针测试与 PNG 已删除，不留归档。既有 JVM 渲染测试（Shell/Screenshots/Browse/Reader/History 系）全过。

## 3. 需主 agent 裁决项与下一轮计划

1. **书架网格几何**：Adaptive(128dp)+padding 8/gap 4（白皮书）vs 现状 Fixed 列数+12（偏好列数可配）。改动影响偏好模型与既有布局测试，请定方向。
2. **阅读器纯黑沉浸**：根背景 `background` 改纯黑需阅读偏好（Black/Gray/Automatic/White）管线配合，属阅读器轮次事项，本轮未动。
3. **1–8dp 微值令牌化**：徽标内边距（3/1）、边框（2dp/1dp）、徽标圆点（8dp 用 Spacing.Small）已有 Code 出处的字面量留在 UI 内，DesignTokens 只收白皮书 §2.7 间距档（4/8/16/24/32）。是否继续下沉由你定。
4. **全量门偶发 flakes（重点）**：`:shared:app:jvmTest` 全量 269 例中，本轮新增 `updatesListGroupsLeaderAndChapterRows` 约 1/2 概率被 `UncaughtExceptionsBeforeTest` 误杀。证据：隔离与子集运行 100% 通过；全量失败时它恒为首个执行的本类用例；其直接前序 `OriginalReferenceShotsTest` 的 system-err 残留 `ComposeRuntimeError: pending composition has not been applied`（ImageComposeScene 子组合竞态，测试本身仍通过）。判断为阅读器 viewer 截图测试的全局快照污染，顺延波及首个后续组合测试，非本轮页面逻辑问题。本轮交付以前述绿门（506 例全绿，见下）为准；建议引擎轨排查该场景测试的线程/重组作用域。
5. **图标终稿**：10 个新字形为按 Code 映射手绘（Material Symbols 同形），隐身眼镜/星爆徽标为原创几何；品牌区为导航书架字形占位。待视觉定稿替换 `BrandPlaceholder` 一处即可。

测试门（JDK 21，`-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`）：

```bash
JAVA_HOME="$HOME/devtools/jdk-21" ./gradlew \
  :shared:design-system:jvmTest :shared:domain:jvmTest :shared:features:jvmTest \
  :shared:data:jvmTest :shared:source-runtime:jvmTest :shared:app:jvmTest \
  -PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"
```

结果：design-system 无测试源集（NO-SOURCE，其余五模块 506 例全绿（domain 11 / features 73 / data 105 / source-runtime 48 / app 269）。基线 499 例，本轮新增 7 例（会话 4 + UI 3），无既有测试丢失。
