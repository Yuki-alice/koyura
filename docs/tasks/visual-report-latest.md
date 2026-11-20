# 前端视觉对齐交付报告（最新一轮）

`feat/visual-parity` · 第二轮基线 `a486af1` · Komikku 参考 `290267d`。

## 1. 改动清单

| SHA | 提交主题 | 影响面 |
| --- | --- | --- |
| `92adb59` | refactor(design-system): 统一业务界面的组件与主题入口 | 业务页和 JVM 测试改用设计系统组件，Material 3 依赖由设计系统提供 |
| `1af4412` | feat(browse): 详情页随封面取色并渐显滚动顶栏 | 共用封面位图、滚动顶栏、禁用样式、推荐区及截图夹具 |
| `ef85c71` | feat(reader): 更新页码药丸与阅读器沉浸栏 | 页码拖动、书签槽、黑底回退及共享 Android 系统栏控制 |
| `b78834b` | feat(library): 对齐封面选中态、分类计数与自适应弹层 | 未读对比、分类计数、Coil 封面、滤选面板 |
| `b791eb4` | feat(history): 精简历史行并显示全局阅读状态 | 阅读时间、未读点、行菜单、More 开关和横幅 |
| `cc65b7f` | test(settings): 截图矩阵渲染真实设置路由并补足书架样本 | 真实 `SettingsRoute` 四态，书架增加多列夹具 |
| `6495556` | test(reader): 复核黑底阅读器并稳定封面截图帧 | 分步推进截图帧，校验黑底阅读器的画布和栏色 |
| `4c10a30` | feat(design-system): 统一触摸目标、状态组件与语义尺寸 | 图标按钮与提示、书架继续阅读命中区、状态组件 |
| `4f80f81` | feat(settings): 联动外观、导航转场与双语设置 | 全局 Fake 主题、AMOLED 导航、转场和设置资源 |
| `f1ea8c0` | feat(design-system): 应用共享状态、下载进度和图标语义 | 业务页面接入统一样式、下载环形进度、深色前景继承 |

## 2. 逐页对照表

逐页对照白皮书 `assets/phone` 与 `assets/tablet`，查看本实现每页 360×800、1280×800 的浅色和深色，共 60 张。下表中的键对应 `visual-<键>-<narrow|wide>-<light|dark>.png`。白皮书同一画布的两种配色参考有限，书架和详情补看了手机与平板的 33、34 深色图，其余结合白皮书色板和四态渲染。截图中的内容、系统栏和字体取决于各自平台与数据夹具。

| 页面与本实现键 | 白皮书手机／平板图 | 人工核对结果 |
| --- | --- | --- |
| 引导 `onboarding-placeholder` | `01_onboarding_theme.jpg`／同名 | 四张均为明示占位。参考图的欢迎说明、主题选择和下一步尚未呈现 |
| 书架 `library` | `10_library_grid.jpg`／同名 | 窄 2 列、宽 9 列。分类计数、未读／已读明度、封面比例和失败占位可辨。参考示例的标题叠在封面上，本实现紧凑卡的标题位于封面下。主题和封面夹具也不同 |
| 浏览 `browse` | `06_browse_sources.jpg`／同名 | 四个标签、搜索、分组与置顶入口可见。工具栏入口组合与样本来源不同，订阅与迁移保持占位功能 |
| 详情 `detail` | `11_manga_detail_library.jpg`／`11_manga_detail.jpg` | 窄屏左封面、宽屏双栏。矩阵使用无封面夹具，补图分别展示封面取色、静态回退及滚动顶栏。推荐区只有空态，宽屏位置与参考不同 |
| 阅读器 `reader` | `13_reader.jpg`／`12_reader.jpg` | 矩阵抓取打开控制栏的状态，黑底、药丸滑杆、两端页码与书签槽可见。白皮书两张为阅读内容和翻页触区参考，菜单细节另对照手机 14、平板 13 |
| 历史 `history` | `15_history.jpg`／`14_history.jpg` | 63×94 封面、日期组、阅读时间、继续和菜单。时间单独一行，参考图时间与章节同列。未读点取已有书架快照 |
| 更新 `updates` | `16_updates.jpg`／`15_updates.jpg` | 参考图为空，矩阵展示分组与有数据的章节状态。筛选和日历入口沿用已有装配 |
| 更多 `more` | `17_more.jpg`／`16_more.jpg` | 开关、行序与分隔线可辨。使用现有品牌占位。默认关态见四张矩阵，开态与全局横幅另有 UI 回归 |
| 设置 `settings` | `18_settings_main.jpg`／同名 | 四张走真实路由。手机显示设置列表，平板左侧 24 dp 选中态和右侧外观子页。卡片预览、搜索入口与参考仍有差异 |
| 统计 `statistics` | `30_statistics.jpg`／同名 | 概览、条目、章节、追踪四组可见。数据为现有 Fake，卡片和标题间距与参考有差异 |
| 分类 `categories` | `31_categories.jpg`／同名 | 参考图为空，矩阵展示已有分类。创建按钮、排序与可见性操作可辨 |
| 下载队列 `download-queue` | `32_download_queue.jpg`／同名 | 两边均显示空队列。非空、失败环色及百分比通过现有 Fake 队列检查，数据装配沿用本轮前状态 |
| 更新错误 `update-errors-placeholder` | `16_updates.jpg`／`15_updates.jpg` | 参考集无独立错误页，邻近画面为更新页。矩阵仍为占位，未验收独立错误列表 |
| 关于／新功能 `about-placeholder` | `27_settings_about.jpg`／同名 | 四张为明示占位。参考的标志、版本及链接列表未验收 |
| 次级工具 `secondary-tools` | `29_feed.jpg`／同名 | 矩阵为真实全局搜索，白皮书只提供相邻工具 Feed。可核对顶栏和搜索骨架，页面内容相异 |

## 3. 9.5 的 30 条清单

通过数 **11 → 26／30**。未覆盖全组合的条目按部分计，分子仅统计通过。数值以本轮测试、截图与实现检查为依据。

| # | 检查项 | 改动前 | 改动后 | 依据 |
| --- | --- | --- | --- | --- |
| 01 | 语义令牌 | 部分 | 通过 | 业务源码没有 `Color(0x…)`，阅读背景及大部分布局尺寸使用令牌。阅读设置弹层仍有一处 20 dp 内边距 |
| 02 | 亮暗可用和文字对比 | 通过 | 部分 | 四态通过，深色前景继承已修。未全量测量 4.5∶1，且已读和禁用使用规格要求的低透明度 |
| 03 | AMOLED 导航栏分层 | 部分 | 通过 | 200% 字号外观 UI 用例切至 AMOLED，采样底栏 `#0C0C0C`。导航轨消费同一容器角色 |
| 04 | 360 dp 书架 2 列 | 通过 | 通过 | 窄屏截图 2 列，卡宽 170 dp，紧凑标题最多两行 |
| 05 | 1280 dp 书架 9 列 | 部分 | 通过 | 宽屏截图两色均为 9 列。80 dp 导航轨之后的卡宽为 128 dp |
| 06 | 720 dp 导航断点 | 通过 | 通过 | 719／720 dp 窗口回归及窄底栏、宽导航轨截图 |
| 07 | 详情左栏上限 | 通过 | 通过 | 详情 UI 用例实测 450 dp，1280 dp 矩阵可见双栏 |
| 08 | 平板弹层 | 部分 | 通过 | 桌面离屏实测书架所用弹层居中、宽 460 dp、点击遮罩关闭，圆角取 28 dp 令牌 |
| 09 | 标签顺序淡出淡入 | 通过 | 通过 | 将全局标签转场总时长校正为 200 ms，导航交互回归绿 |
| 10 | 子页转场方向 | 部分 | 通过 | 前进、返回、预测返回分别配置方向；详情、阅读器与设置往返 UI 回归绿 |
| 11 | 滚动顶栏渐显 | 部分 | 通过 | 补图与像素断言验证首屏透明及滚动后表面变色，顶栏抬升至 3 dp |
| 12 | 阅读器栏 200／150 ms | 通过 | 通过 | 保留滑入滑出 200 ms、淡入淡出 150 ms，控制栏用例绿 |
| 13 | 未读与已读 | 未达 | 通过 | 网格和列表分别使用标题 100%／38%、副标题 78%／38%，截图可辨两态 |
| 14 | 18 dp 徽标 | 通过 | 通过 | 书架徽标保留 18 dp，宽窄夹具均可见且封面仍为固定比例 |
| 15 | 封面比例固定 | 通过 | 通过 | 2∶3 封面约束套在占位和 Coil 帧外。失败占位与成功图片用例绿 |
| 16 | 即时选中 | 部分 | 通过 | 封面直接使用 0.76 alpha 及 secondary 描边，书架多选回归绿 |
| 17 | 空状态 | 通过 | 通过 | 共用颜文字、说明和动作槽，下载、更新、浏览和书架空态回归绿 |
| 18 | 全局横幅顺序 | 未达 | 通过 | UI 用例按三条横幅纵向坐标核对顺序，切页保持两个 Fake 开关 |
| 19 | 返回优先关闭弹层 | 部分 | 部分 | 阅读器先清当前弹层状态。书架遮罩能关闭弹层，但桌面离屏 Escape 未触发关闭，原生桌面和 Android 返回动作未确认 |
| 20 | 48 dp 命中区 | 部分 | 通过 | 图标按钮使用 48 dp 布局，下调到 28 dp 的书架图案保留 48×48 dp 命中，继续按钮有 UI 尺寸断言 |
| 21 | 图标标签 | 部分 | 通过 | 独立图标按钮、分类排序和开关提供标签。伴随文字的图标由装饰组件绘制，交互名称来自可见文字 |
| 22 | 长按 Tooltip | 未达 | 通过 | 通用图标与书架继续按钮接入同一 Tooltip，桌面离屏长按探针成功显示标签 |
| 23 | RTL 阅读器控件 | 部分 | 通过 | 控件容器强制 LTR，显式右到左阅读模式单独控制滑杆方向，阅读器回归绿 |
| 24 | 图片 300 ms 交叉淡入 | 未达 | 通过 | Coil 请求为 300 ms，成功与失败夹具均出图，命中共用按源字节缓存 |
| 25 | 可读的下载进度 | 未达 | 通过 | 下载 UI 用例验证 30% 与 16%，失败行圆环、数值和状态使用 error |
| 26 | 多选数量正确 | 通过 | 通过 | 书架与历史多选回归保留 |
| 27 | 平板设置选中态 | 通过 | 通过 | 宽屏浅／深截图可见 24 dp 圆角和 surfaceVariant 选中行 |
| 28 | 主题切换无残留 | 部分 | 通过 | 同一设置会话驱动应用主题，UI 用例切主题和自定义种子后保留设置子页，导航栏即时更新 |
| 29 | 字号缩放到 200% | 部分 | 部分 | 外观设置实测 200%；高字号下选项值换行。未逐页逐语种检查所有长文本 |
| 30 | 长列表与转场性能 | 部分 | 部分 | 封面按可见项加载，网格保留稳定键和现有近屏预取。详情章节仍整体排布，未采集设备帧耗时 |

## 4. 截图与复现命令

矩阵 PNG 在 `build/visual-parity/`，详情补图在 `build/visual-parity/detail-evidence/`。图片为本地构建产物。矩阵仅统计目录顶层的 `visual-*.png`。

```bash
export JAVA_HOME="$HOME/devtools/jdk-21"

./gradlew --offline --console=plain :shared:app:jvmTest --tests '*VisualParityMatrixTest*' \
  -PreaderScreenshotDir="$PWD/build/visual-parity" -PveneraReferenceDir="$PWD/reference/Venera-Next"

./gradlew --offline --console=plain :shared:app:jvmTest --tests '*AppShellDetailUiTest*' \
  -PreaderScreenshotDir="$PWD/build/visual-parity" -PveneraReferenceDir="$PWD/reference/Venera-Next"

./gradlew --offline --console=plain :shared:domain:jvmTest :shared:features:jvmTest :shared:data:jvmTest \
  :shared:source-runtime:jvmTest :shared:app:jvmTest -PveneraReferenceDir="$PWD/reference/Venera-Next" \
  -PreaderScreenshotDir="$PWD/build/visual-parity"
./gradlew --offline --console=plain --rerun-tasks :shared:domain:jvmTest :shared:features:jvmTest \
  :shared:data:jvmTest :shared:source-runtime:jvmTest :shared:app:jvmTest \
  -PveneraReferenceDir="$PWD/reference/Venera-Next" -PreaderScreenshotDir="$PWD/build/visual-parity"

./gradlew --offline --console=plain --continue :apps:android:assembleDebug :apps:desktop:classes \
  :shared:app:linkDebugFrameworkIosSimulatorArm64
```

Golden-diff 继续检查画布精确尺寸、至少 95% 不透明像素、6 个采样点逐通道误差 ≤12、非背景比例 >1%（容差 ±6）以及底栏／导航轨区域内容比例 >1%。阅读器的黑底与暗色控制栏使用该页面自己的预期色板，其余页面沿用原色板。

- 全量 JVM 门连续两遍退出码 0，首遍 3 分 46 秒，第二遍带 `--rerun-tasks` 用时 4 分 06 秒。第二遍记录 668 个用例，657 通过，11 个沿用条件跳过的社区源用例，0 失败。模块计数为 domain 13、features 101、data 131、source-runtime 65、app 358。设计系统另有 8 个 JVM 用例通过。
- Android Debug APK、桌面 JVM classes、iOS arm64 模拟器 framework 均构建成功，合计 1 分 40 秒，退出码 0。未做 Android 或 iOS 真机验收。
- 最终矩阵 60 张，30 张 360×800、30 张 1280×800。15 个键各四张均通过 golden-diff 并逐页人工核对。详情另有静态、种子色和滚动后顶栏三张补图。
- 桌面离屏补测确认宽屏弹层居中且宽 460 dp，点遮罩可关闭，长按图标会显示 Tooltip。Escape 未使弹层关闭。隔离配置的桌面应用已启动，电脑控制工具无法绑定 Java 窗口，原生 Escape 结果未确认。
- `rg -n '^import androidx.compose.material3' shared/features shared/app` 匹配数为零。版本目录、禁改模块及参考目录在本轮提交中无变更。

## 5. 未完成项与范围边界

- 本轮核对 15 个目录键。引导、独立更新错误和关于／新功能仍占位，因此其中 12 个键为真实页面。订阅、迁移、统计和下载的数据接线沿用原装配，详情推荐区显示空态。
- 共享应用层为加载阶段及没有注入阅读偏好的入口启用黑底。注入原生产偏好时，应用仍遵从存储中的 Automatic 或用户选择。默认值修改涉及本轮禁改的域层。
- Android 系统栏控制位于 `shared/app`，已通过 Android 编译和打包。真机入场与退出手势待复核。书签位已显示，书签动作与存储待接入。
- 外观主题通过内存 Fake 跨页面生效。生产偏好持久化、Android Monet 和 ZoomImage 替换留待对应范围。Skiko 依赖兼容性警告仍存在。
- 历史行的收藏和删除改从行菜单进入，持久化断言仍沿用。未读点由现有书架快照提供，缺少可用状态时保持空白。
- 粗体通过数以第 3 节为准。开发期曾重现详情取色时点击丢失和新增外观用例定位失败，已修正后重跑。

## 6. 下一轮建议

1. 实现引导、独立更新错误和关于／新功能，替换三个占位键。补齐详情推荐来源和次级工具同页对照物。
2. 复核桌面 Escape、Android 返回与沉浸栏的实际手势，再覆盖 200% 字号长文本和长列表帧耗时。
3. 在偏好数据工作范围内，确认新阅读配置黑底默认及旧配置 Automatic 的兼容语义，再接主题持久化。
