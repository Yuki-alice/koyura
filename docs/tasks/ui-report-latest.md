# UI 轨交付报告（第三轮）

分支 `feat/ui-parity`（前两轮已合入 main）。本轮按任务书完成任务一至三；任务四（详情 FAB、竖滑杆重写）经评估留下一轮，见第 4 节取舍说明。

## 1. 本轮改动清单

| 提交 | 内容 |
| --- | --- |
| `feat(ui-host): Venera脚本UI的Compose宿主与剪贴板注入` | `SourceUiHost` 实现 `VeneraUiHost`（snackbar 消息、顶层加载态、三形态挂起对话框，取消释放槽位）；`SourceClipboard` + 三端实现；装配层创建宿主并注入 `openExtensionSession`、App 顶层渲染呈现层；对话框/输入校验/选择/snackbar/加载态与状态机单测覆盖（13 例） |
| `feat(account): 扩展详情按源账号区契约与 Fake 实现` | `features/account` 定义契约（`AccountState`/`AccountFieldConfig`/`AccountRepository`，与任务书字面一致）+ 五形态 Fake + 会话；详情页按源渲染账号区（未配置整块隐藏），登录表单/网页登录/注册外链/重登/注销确认；`ExtensionInfo.sources` 携带源 key；会话状态机与五形态 UI 回归覆盖 |
| `fix(android): 剪贴板文本转换改用应用上下文` | `ClipboardManager` 无 `getContext`，改持应用上下文；三端构建全绿 |
| `feat(updates): 更新错误入口与失败重试` | `UpdatesRepository.failures`（默认空）+ 会话随快照加载失败集；Fake 自带失败样例且 `retryFailures` 清空之；顶栏溢出菜单切换错误视图（失败作品 + 错误信息 + 全部重试）；会话与 UI 展示/重试回归覆盖 |
| `docs(ui): 第三轮交付说明覆盖写入` | 本报告 |

另含任务三配套提交（`fix(design-system): 图标终稿…`、`docs(more): 品牌占位注释…`，见下）。

**任务四取舍**：详情 FAB（可拖拽 SmallExtendedFAB + 位置持久化）与竖滑杆自适应重写均为结构性改动，涉及详情 Scaffold 槽位、阅读偏好管线与 viewer 布局，建议与阅读器轮次一起做。本轮未动代码，现状（内联 Start/Continue 双钮、固定 280/64 竖轨）保持，UI 测试继续覆盖既有行为。

## 2. 视觉对照结论与四态自查

| 页/项 | 差异项 → 处置 |
| --- | --- |
| 扩展详情账号区（新） | 无配置源整块隐藏（Venera `_buildAccount` 同）；未登录三形态（表单/纯网页/混合）按字段动态渲染，注册外链 + 网页登录 + 失败提示走宿主；已登录 info 行 + 重登（表单登录）+ 注销确认 → 已交付，中英文案 |
| 宿主呈现（新） | AlertDialog 居中 24dp 内边距（06 §6.7）；输入校验行内标红；snackbar 底部；加载条顶部 + 可取消时附取消键 → 已交付 |
| 更新错误入口（新） | 顶栏溢出菜单「更新错误（N）」切换错误视图；失败标题 + error 色信息；全部重试调 `retryFailures`，Fake 重试后清空 → 已交付 |
| 图标终稿（任务三） | 隐身眼镜取 Komikku anim_incognito 原生路径逐字转写；品牌改 library_books 官方路径；cloud_off 等 7 个由 Gradle 缓存 material-icons 1.7.3 反编译提取官方 pathData 逐个替换；batch_add 与官方逐字一致、info 为纯几何保留 → 已终稿 |
| 更新列表/下载队列/书架/阅读器等既有页 | 本轮未改视觉；回归测试全过 |

四态自查：本环境无真机/模拟器，沿用 JVM 离屏渲染（360×720/360×1000 浅色、1280×800 深色）+ 语义截图（captureToImage）核对账号展开表单与更新错误列表，字形/版式符合预期；探针测试与 PNG 已删除，不留归档。

## 3. 需主 agent 裁决/接线项与下一轮计划

1. **生产桥接（请主 agent 接线）**：账号页当前 Fake 驱动，需真实 `SourceAccountService` 桥接（`state/login/captureWebLogin/logout` 映射见 `SourceAccountService.kt:48-62`）；更新错误列表的 `failures()` 需接 `LibraryUpdateScanner.failures`（生产 `retryFailures` 已通）；`SourceUiHost.launchUrl` 已有三端实现可直接复用。
2. **详情 FAB 化 + 竖滑杆自适应**：留下一轮与阅读器轮次一起做（需偏好管线与 viewer 布局配合）。
3. **Release 说明**：`MoreScreen` 导航行中下载队列已可点其余仍非交互；账号区依赖装配层 `LocalSourceUiHost`（App 未注宿主时回退 Fake 仓储 + 无提示）。
4. **上一轮 flake 跟踪**：本轮三遍全量门（两遍交付门 + 一遍终态确认）一次通过未复现误杀；`OriginalReferenceShotsTest` 全局快照污染嫌疑仍建议引擎轨有空排查。

测试门（JDK 21，`-PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next"`）：

```bash
JAVA_HOME="$HOME/devtools/jdk-21" ./gradlew \
  :shared:design-system:jvmTest :shared:domain:jvmTest :shared:features:jvmTest \
  :shared:data:jvmTest :shared:source-runtime:jvmTest :shared:app:jvmTest \
  -PveneraReferenceDir="/Users/kurisu/project x/reference/Venera-Next" --rerun-tasks
```

结果：连续两遍全绿（第三遍终态确认亦绿）。末遍：design-system 无测试源集（NO-SOURCE），其余五模块 574 例全绿（domain 11 / features 90 / data 117 / source-runtime 57 / app 299）。本轮新增 25 例（账号会话 8 + 账号 UI 8 + 宿主 13 + 更新错误会话 2 + 更新错误 UI 1 − 有交叠计入各模块；features +12 / app +27 含引擎轨同期增量）。

三端构建（本轮平台粘合后必跑）：`:apps:android:assembleDebug` EXIT 0（中途修过一次 `ClipboardManager.getContext` 误用），`:apps:desktop:classes` EXIT 0，`:shared:app:linkDebugFrameworkIosSimulatorArm64` EXIT 0。
