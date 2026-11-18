# execution-state · 前端视觉对齐执行状态 (V0-01起)

更新: 2026-11-18 (V0-06 原版构建探查与三端构建运行落盘)。本文件为跨任务恢复指令的唯一入口;
业务实现未动, 仅`docs/alignment/v0-06/build-probe.md` (新建) + 本文件为本次落盘。
前轮: V0-05 目标画布基线重拍归档落盘 (20 张, 门禁退出码 0)。

## 1. 目标

按 `docs/frontend-alignment-roadmap.md` 第5节V0出口与7.2字段, 交付书架/浏览/详情/历史/阅读器
五类基线manifest与可复现记录。缺原版运行证据项登记为待验收, 不宣布通过。
复用已有令牌 (`DesignTokens`)、组件 (`Cover`/`BrowseRow`/`ListGroupHeader`/`SearchField`/`ReaderChrome`/
`ComicViewer`)、服务 (`BrowseService`/`VeneraBrowseService`/`HistoryService`/`ReaderService`) 与
状态契约 (session/state holder + 不可变快照), 同一无状态Content服务生产预览截图。

## 2. HEAD摘要

- 项目HEAD: `4f82b71ac4e95df00461f8d83060a5e977f5b96a` (short `4f82b71`;
  修复ask实测 `git rev-parse HEAD` 输出即此值, 与v0-05 manifest/verification一致)。
- 参考: `reference/komikku` HEAD `290267d` (与白皮书同版, clean);
  `reference/Venera-Next` HEAD `c0c4597`; `reference/` 本次无修改。
- 工作树脏 (V0-01 ask历史记录, 非现值): 当时 `git status --porcelain | wc -l` 共100行
  (其中98行为任务前已存在改动, 另2行为V0-01新增 `?? docs/alignment/execution-state.md` 与
  `?? docs/alignment/v0-01/` 目录项)。现值见各轮verification (V0-04归档105行;
  V0-05生成前105行→生成时106行→归档时107行, 修复ask实测现为107行); 行数口径统一见v0-05 verification §4。
  既有改动含 `shared/app` (浏览/历史/书架/阅读器表现层与jvmTest),
  `shared/features` (library/reader会话与屏), `shared/data` (LibrarySchema/Store/ExtensionDb.sq[+6.sqm]),
  `shared/domain` (library/reader模型), `shared/design-system` (`Covers`/`Lists`重命名、`DesignTokens`收敛、
  新增`ActionIcon`/`SearchField`/`DesignTokens`资源), `docs/` (architecture/komikku-alignment/
  technology-stack + 新增frontend-roadmap/visual-parity-plan/prompts/reviews)。
  全部未提交, 不作为已提交基线; V0要求将已提交/未提交/历史截图分开标注 (已在v0-01 manifest标注)。
- 约束重申: Koin/MVIKotlin/ViewModel/Settings/Kermit/Turbine首轮阅读器验收前不引入;
  不引入Voyager/Decompose/Room/Retrofit/Paging 3/mvikotlin keepers; DI为手动构造器+CompositionLocal。

## 3. 当前任务

- V0-01 统一页面状态与证据manifest — 已落盘 (`docs/alignment/v0-01/manifest.json` +
  `docs/alignment/v0-01/verification.md`), 待脚本统一跑门 (roadmap 7.5) 后审阅。
- V0-02 c-chrome 证据归档入库 — 本ask执行完毕, 允许范围仅 `docs/alignment`。
  交付: `docs/alignment/c-chrome/current/` (29张, 与`build/komikku-c/` SHA逐个一致) +
  `docs/alignment/c-chrome/manifest.json` (文件名/SHA/画布/主题/来源宿主/生成代码状态) +
  `docs/alignment/c-chrome/verification.md` (JVM离屏/截图序列代录屏/脏基线待重跑, 不宣称最终通过)。
  规则遵守: 未改业务与测试, 未删用例降断言, 未自跑全量门。
- V0-06 原版构建可行性探查与三端构建运行 — 本ask执行完毕,
  允许范围仅 `docs/alignment` (新建 `docs/alignment/v0-06/build-probe.md` + 本文件更新)。
  交付: 原版构建要求清单 (AGP 8.13.2 / Gradle 9.3.1 / Kotlin 2.4.20 /
  compileSdk36-targetSdk36-minSdk26-Java17, 均引文件行号) +
  本地环境现状 (JDK Corretto 21.0.12.1 / SDK build-tools 36.0.0+37.0.0 /
  platforms android-37系 / NDK 28.2.13676358 / system-image android-36, `ls` 实测;
  `adb devices` 零设备) +
  三编译目标通过, 脏基线 (修正口径: 非可发布产物): `:apps:android:assembleDebug` EXIT 0
  (APK 19,943,478 bytes, 本轮新鲜, SHA 见 `v0-06/artifacts-sha256.txt`) /
  `:apps:desktop:classes` EXIT 0 (疑为 UP-TO-DATE, 无新鲜产物, 是否重编待确认) /
  `:shared:app:linkDebugFrameworkIosSimulatorArm64` EXIT 0 (仅 warnings,
  framework 本轮新鲜 link)。控制台日志未留存 (`-q`), EXIT/warnings 为文本记录;
  附件新增 `v0-06/env-probe.log` + `v0-06/artifacts-sha256.txt`。
  单命令超时 600000ms, 均一次通过无重跑。
  规则遵守: 只读探查 + 构建命令 + `docs/alignment`, 未改业务与测试,
  原版全量构建未跑 (范围限定), 未伪造 APK/录屏/性能数据。
- 状态: 归档完成; V0出口仍未达成 (见§6)。
- V0-03 逐页入口与状态索引复核 — 已落盘 (`docs/alignment/v0-03/pages-index.md`,
  12页×入口/导航显隐/状态集/宽窄行为 + 旧“占位/未接线”10条判定 + 待复核T1–T5)。
  结论要点: O1部分过时 (书架已是真实页, `komikku-alignment.md:9` 该半句待修订);
  O6部分过时 (`architecture.md:22-31` 目录树与 `features/` 实际内容不符);
  更新/更多仍为 `TabRootScreen` 占位 (O2/O3/O9有效); 历史无多选是现状事实 (T1)。
  规则遵守: 只读源码复核, 未改业务与测试, 未删用例降断言, 未自跑全量门 (not run)。
- V0-04 统一数据夹具定义与盘点 — 已落盘 (`docs/alignment/v0-04/unified-fixture.md` +
  `docs/alignment/v0-04/fixture-manifest.json`, 49条目=48项实测+1项pending;
  HEAD `4f82b71`, 当时 `git status` 105行)。规则遵守: 只读盘点, 未改业务与测试, 未自跑全量门。
- V0-05 目标画布基线重拍 — 本ask执行完毕, 允许范围 `docs/alignment` + `shared/app/src/jvmTest`(仅加法)。
  交付: 新测 `V005BaselineCanvasesTest.kt` (5 `@Test`×窄/宽×浅/深=20张; 画布既有写死420/1100/1280故加法新增,
  输出经 `-PreaderScreenshotDir="$PWD/build/v0-05"` 指向新建目录, 未覆盖既有输出) +
  `docs/alignment/v0-05/current/` (20张, `cmp -s` 逐个与 `build/v0-05/` 一致) +
  `docs/alignment/v0-05/manifest.json` (20条目文件名/SHA/画布/density/主题/宿主/代码状态) +
  `docs/alignment/v0-05/verification.md` (命令与输出、PNG头尺寸实测360×720×10/1280×800×10、
  3张抽看记录、脏基线声明)。单次 gradle 实跑 `BUILD SUCCESSFUL in 26s`
  (仅新增类, 非全量门)。脏基线 (HEAD `4f82b71`; 生成前105行→生成时106行
  (含新增 `?? V005BaselineCanvasesTest.kt`)→归档时107行 (再含 `?? docs/alignment/v0-05/`);
  修复ask实测现仍为107行) 故待干净基线重跑确认。
  规则遵守 (仅限定本ask增量: 新文件 `V005BaselineCanvasesTest.kt` + `docs/alignment/v0-05/`):
  本ask增量未改业务与既有断言, 未删用例, 未自跑全量门 (not run);
  工作树级另有任务前已存在的删除/修改 (如 `D KomikkuShellScreenshotsTest.kt` 等, 见v0-05 verification §4)。

## 4. 队列 (路线图第6节V0→V1→V2链 + 第8节顺序; V0完成前V1-01等不得提前)

1. V0-01 统一页面状态与证据manifest (本任务; 前置无)。
2. V1-01 统一形态及导航栏可见性 (前置V0; 解G01/G02)。
3. V1-02 校准转场与重选 (前置V0)。
4. V2-01 共享弹层和空态 (前置V1)。
5. V3-01 自适应网格与卡片 (前置V1+V2)。
6. V3-02 Coil管线POC与接入 (前置V1+V2)。
7. V4-01 详情双栏/滚动/FAB (前置V1+V2+V3封面管线)。
8. V4-02 封面主题/blur POC (前置同上)。
9. V6-01 Chrome校准 (前置V1+V2; 封面局部主题依赖V4)。
10. V6-02 ZoomImage POC (Viewer/页资源管线; 需三端实测后估算)。

V8可提前做状态清单与复用组件测试预览, 生产接入与框架迁移按V7窗口推进。

## 5. 证据

- 本次: `docs/alignment/v0-01/manifest.json` (五类基线manifest) +
  `docs/alignment/v0-01/verification.md` (可复现记录+空缺清单)。
- 历史引用 (非本次产生): `docs/alignment/a-shell/` (27张JVM离屏+README),
  `docs/alignment/p1-shell/screenshots/` (12张), `docs/alignment/b-viewer/` (shots 17/compare 12组/rec 3mp4+frames),
  `docs/alignment/c-chrome/README.md` (分支文字登记; 现已有`current/`归档见下),
  `docs/komikku-alignment.md:60-70` (P1记录), `docs/reviews/2026-11-18-functional-gap.md` (13域差距)。
- V0-02新增: `docs/alignment/c-chrome/current/` (29张, `build/komikku-c/`逐字节拷贝,
  SHA逐个一致) + `docs/alignment/c-chrome/manifest.json` (文件名/SHA/画布/主题/来源宿主/生成代码状态) +
  `docs/alignment/c-chrome/verification.md` (离屏/代录屏/脏基线待重跑声明)。
  生成器`ChromeScreenshots.kt`/`ChromeTestHost.kt`已提交且工作树干净, 源PNG mtime 2026-11-17 11:55
  (PIL全开验证有效); 脏基线 (HEAD `4f82b71` + `git status` 100行) 故标记待重跑确认, 不作最终通过。
- 白皮书/原版: `reference/komikku-design-whitepaper-md/` + `reference/komikku@290267d`;
  `reference/komikku实机截图/` 29张仅氛围参照, 不作配对证据。
- V0-03新增: `docs/alignment/v0-03/pages-index.md` (12页索引 + O1–O10旧说法判定 + T1–T5待复核;
  修订建议: `komikku-alignment.md:9` 书架半句、`architecture.md:22-31` 目录树待主agent修订,
  本任务不改业务外文档)。
- V0-04新增: `docs/alignment/v0-04/unified-fixture.md` (F1–F5 口径 + 10宿主盘点 +
  5项双适配器不一致清单 + unifiedHash口径) + `docs/alignment/v0-04/fixture-manifest.json`
  (49条目 = 48项实测 + 1项pending; python3 JSON解析校验通过; 统一封面/页图文件与unifiedHash首值记pending)。
- V0-05新增: `docs/alignment/v0-05/current/` (20张, `build/v0-05/`逐字节拷贝,
  `cmp -s` identical=20) + `docs/alignment/v0-05/manifest.json`
  (20条目文件名/SHA/画布/density/主题/宿主/代码状态; PNG头实测360×720×10/1280×800×10) +
  `docs/alignment/v0-05/verification.md` (单次gradle命令与输出、3张抽看、脏基线待重跑声明)。
  生成器 `shared/app/src/jvmTest/kotlin/V005BaselineCanvasesTest.kt` 为本轮加法新增
  (既有420/1100/1280画布写死未动, 其中ShellScreenshotsTest.kt本身为??未跟踪);
  脏基线 (HEAD `4f82b71`; 生成前105行→生成时106行→归档时107行, 构成2D+44M+2RM+59??且删改均为任务前已存)
  故标记待干净基线重跑确认, 不作最终通过。
- V0-06新增: `docs/alignment/v0-06/build-probe.md` (§1原版构建要求清单 +
  §2本地环境实测 + §3三编译目标命令/EXIT/产物 + §4阻塞B1–B5 + §5出口自评) +
  `docs/alignment/v0-06/env-probe.log` (环境只读快照) +
  `docs/alignment/v0-06/artifacts-sha256.txt` (APK/Framework SHA-256+尺寸+mtime+完整性声明)。
  三编译目标脏基线通过 (HEAD `4f82b71`; 构建运行时 `git status` 107行, 与V0-05归档后一致;
  本轮新增 `?? docs/alignment/v0-06/` 一项后实测为108行),
  口径为“通过非可发布” (仅 Android 得可安装 APK; desktop 疑 UP-TO-DATE 待确认),
  待干净基线重跑确认, 不作最终通过。

## 6. 待解决 (V0出口未达成项, 全部pending)

- G01–G13仍stillOpen (G13即证据口径, 本任务补manifest/verification后原版运行项仍pending)。
- 原版可复现构建/安装路径、APK SHA、设备/API/density/字体/语言/主题/系统栏: pending。
  V0-06进展: 原版构建要求清单与本地SDK现状已落地 (`docs/alignment/v0-06/build-probe.md` §1–§2);
  安装路径阻塞于零adb设备 (B1), 原版全量构建未跑 (B4)。
- 目标画布360×720/1280×800成对图: 我方重拍完成 (`docs/alignment/v0-05/`, 20张脏基线,
  待干净基线重跑确认); 历史420/1280仍仅回归扩展; 原版侧同画布配对: pending。
- 统一数据夹具+SHA-256: 口径与盘点完成 (`docs/alignment/v0-04/`; 49条目 = 48项文件SHA实测 + 1项pending,
  venera双份脚本同值 `41ff3cdc…8459d` 与既有断言一致); 剩余pending:
  统一可解码封面文件、统一页图集(编号/横跨/长图/损坏/两章)、统一日期值、
  5项双适配器不一致消差、unifiedHash首值计算与并跑验证 (需跑gradle门, 本ask not run)。
- 每页入口/导航显隐/状态集合/宽窄索引逐项复核 (旧"占位/未接线"是否过时): V0-03完成
  (`docs/alignment/v0-03/pages-index.md`; O1/O6部分过时, 其余有效, T1–T5待复核)。
  遗留修订动作 (改 `komikku-alignment.md`/`architecture.md` 旧说法, 非业务代码): pending,
  留给主agent (本任务允许范围仅 `docs/alignment`)。
- c-chrome 29张已归档入库 (`c-chrome/current/`+manifest+verification); 剩余:
  干净基线重跑确认 (SHA更新)、Viewer原版侧真机配对、真机录屏、性能基线: pending。
  (三编译目标V0-06已首轮通过, 脏基线, 口径为通过非可发布, 待干净基线重跑确认。)

## 7. 恢复指令 (给下一个ask/脚本)

1. 读本文件 + `docs/alignment/v0-01/verification.md` + `docs/alignment/v0-01/manifest.json`,
   再读 `docs/alignment/c-chrome/verification.md` + `docs/alignment/c-chrome/manifest.json`,
   再读 `docs/alignment/v0-03/pages-index.md` (逐页索引与O1–O10/T1–T5),
   再读 `docs/alignment/v0-04/unified-fixture.md` (§1缺口表 + §4不一致清单 + §5 unifiedHash口径)
   与 `docs/alignment/v0-04/fixture-manifest.json` (49条目 = 48项实测 + 1项pending, unifiedHash首值pending),
   再读 `docs/alignment/v0-05/verification.md` (§2加法依据 + §3命令输出 + §4脏基线 + §5 pending)
   与 `docs/alignment/v0-05/manifest.json` (20条目, 脏基线待重跑),
   再读 `docs/alignment/v0-06/build-probe.md` (§1原版构建要求 + §2环境实测 +
   §3三编译目标命令/EXIT/产物 + §4阻塞B1–B5 + §5出口自评) 及同目录
   `env-probe.log` + `artifacts-sha256.txt` (复核附件),
   再读 `docs/frontend-alignment-roadmap.md` §5 (V0) 与§7.2 (manifest字段)。
2. 核 `git rev-parse HEAD` 与 `git status --porcelain` 是否仍为上述HEAD/脏集; 核
   `git -C reference/komikku rev-parse HEAD` 是否仍为`290267d`。
3. 不自改业务实现; 需要的门由脚本按路线图7.5统一跑
   (`:shared:design-system:jvmTest :shared:features:jvmTest :shared:app:jvmTest` 起,
   里程碑再加domain/data/source-runtime + android/desktop/ios链接, 均带
   `-PveneraReferenceDir="$PWD/reference/Venera-Next"`, JDK 21 `~/devtools/jdk-21`)。
4. 下一最早可做任务仍为V0剩余pending (原版安装/配对·B1设备就绪后统一夹具/v0-05干净基线重跑确认/c-chrome干净基线重跑确认/三端干净基线重跑确认),
   V1-01 (G01/G02) 须等V0出口达成后进入。
