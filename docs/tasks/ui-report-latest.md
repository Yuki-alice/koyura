# UI 轨交付报告（第二轮）

分支 `feat/ui-parity`（第一轮已合入 main，本轮在其上继续）。本轮按主 agent 裁决完成任务一至五。

## 1. 本轮改动清单

| 提交 | 内容 |
| --- | --- |
| `fix(library): 书架网格改自适应并移除列数偏好` | 网格改 `Adaptive(128dp)`、内边距 8dp、间距 4dp，分组头改 `maxLineSpan`（Code: LazyLibraryGrid.kt:22-37、CommonMangaItem 55-56）；删除 `LibraryPrefs.columns`、显示页列数滑杆、落盘十二键中的列数键（减为十一键，parse 侧不再引用老键无害）；列数滑杆测试退役，新增 360dp=2 列/1280dp=9 列自适应断言 |
| `fix(reader): 根背景改 Automatic 固定映射` | 暗 `#FF202125`/亮白（Code: ReaderActivity.kt:1432），不用主题 token；chrome 跟随主题现状不变；Black/Gray 需 dark 包裹的耦合语义留注释（Code: ContextExtensions.kt:111-118）；浅深两主题色值断言 |
| `fix(design-system): 图标终稿` | 隐身眼镜取 Komikku anim_incognito 原生路径逐字转写；品牌占位改 library_books 官方路径；cloud_off/标签/统计/存储/日历/帮助/更新徽标 7 个由 Gradle 缓存 material-icons 1.7.3 反编译提取官方 pathData 逐个替换；batch_add 与官方逐字一致保留，info 为纯几何保留；离屏渲染验过 10 字形可辨认 |
| `feat(downloads): 下载队列页 Fake 实现` | 队列行（标题/进度/状态/动作）+ 暂停恢复 FAB + 空态，Fake 状态机对齐 `ChapterDownloadStore`；更多下载队列入口改为可点并推子路由；新增暂停图标；会话状态机与行动作/UI 入口测试覆盖 |
| `docs(more): 品牌占位注释更新` | 注释跟随图标终稿（library_books） |

## 2. 视觉对照结论与四态自查

| 页/项 | 差异项 → 处置 |
| --- | --- |
| 书架网格 | 自适应列/边距/间距/分组头跨栏 → 已对齐（Code 值逐字）；浏览源、相关推荐无同型固定列网格（一处为 `LazyRow`，一处为对话框 `Adaptive(104dp)`），无需改动 |
| 阅读器背景 | 根背景 Automatic 映射 → 已对齐（Code 值逐字）；chrome 不动（Automatic 下跟随主题，现状正确） |
| 图标 | 9 替换 + 2 保留 → 已终稿（来源：Komikku drawable 1 个、反编译官方路径 7 个、几何/逐字验证保留 2 个）；离屏渲染 phone 浅色 2 张核对通过，探针与 PNG 已删 |
| 下载队列 | 空态与白皮书 32 一致（返回 + 标题 + 颜文字 + No downloads）；列表行按 7.12（标题/进度/状态/右侧动作）+ FAB；拖动手柄未做（Fake 无排序语义），筛选/排序未做（Fake 无维度）→ 明确缺口见下 |
| 任务五细项 | 简介收起 3 行 == Code（MangaSummary 最小 3 行占位）→ 已对齐；ReadingButtons：Komikku 无内联按钮（动作为可拖拽 FAB，MangaScreen.kt:532）→ 无直接出处，按现状保留；竖轨 280/64：Code 竖滑杆自适应（weight + 自定义旋转布局，无固定尺寸）→ 固定尺寸无出处按现状保留；页码指示器 12/6：隐藏指示器为白皮书 Inference、Code 无该组件 → 按现状保留 |

四态自查：本环境无真机/模拟器运行条件，沿用 JVM 离屏渲染（360×720 浅/深、360×1000 浅、1280×800 深）比对图标与新页结构；既有 JVM 渲染测试（Shell/Screenshots/Browse/Reader/History 系）全过。

## 3. 需主 agent 裁决项与下一轮计划

1. **详情 FAB 化**：ReadingButtons（内联 Start/Continue 双钮）与 Komikku 可拖拽 SmallExtendedFAB（Start/Resume、左右吸附、位置持久化）在结构与交互上都不同。替换需 Scaffold FAB 槽 + 拖拽手势 + 位置偏好，建议与阅读器轮次一起做，本轮未动。
2. **竖滑杆自适应重写**：当前 requiredWidth(280)+64 轨可用且有测试覆盖；改为 Code 式 weight + 自定义旋转布局是中等重写，建议与阅读器轮次一起做。
3. **下载队列缺口**：拖动手柄排序、顶部排序菜单、多选删除未做（均需真实引擎/偏好支撑）；章节点行当前无跳转（Fake 无目录），真实源接入后接详情/阅读器。
4. **上一轮 flake 跟踪**：本轮两遍全量门均为一次通过（`--rerun-tasks`，无 UncaughtExceptions 误杀）。上一轮的 `OriginalReferenceShotsTest` 组合污染嫌疑仍建议引擎轨排查，本轮未复现。
5. **图标 info 取舍**：info 沿用首轮纯几何环形（圆环/条/点精确图形），未引入官方路径——圆环类字形无偏差风险，如需统一可随时替换。

测试门（JDK 21，`-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`）：

```bash
JAVA_HOME="$HOME/devtools/jdk-21" ./gradlew \
  :shared:design-system:jvmTest :shared:domain:jvmTest :shared:features:jvmTest \
  :shared:data:jvmTest :shared:source-runtime:jvmTest :shared:app:jvmTest \
  -PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next" --rerun-tasks
```

结果：连续两遍全绿。第二遍：design-system 无测试源集（NO-SOURCE），其余五模块 525 例全绿（domain 11 / features 78 / data 109 / source-runtime 55 / app 272）。本轮新增 9 例（会话 5 + UI 4；data/source-runtime 的增量为引擎轨同期交付）。
