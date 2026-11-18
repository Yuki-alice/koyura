# V0-06 · 原版构建可行性探查与三端构建运行

日期: 2026-11-18。HEAD `4f82b71ac4e95df00461f8d83060a5e977f5b96a`
(`git rev-parse HEAD` 实测); `reference/komikku` HEAD `290267d`
(`git -C reference/komikku rev-parse HEAD` 实测);
`reference/Venera-Next` HEAD `c0c4597`
(`git -C reference/Venera-Next rev-parse HEAD` 实测)。
工作树 `git status --porcelain | wc -l` = 107 行 (本次实测; 与 V0-05 归档后一致, 构成见 v0-05 verification §4)。
允许范围: 只读探查 + 构建命令 + `docs/alignment`。业务代码与测试未动。
原版全量构建未跑 (本任务范围为只读探查, 见 §4 B4)。

## 1. 原版构建要求 (只读探查, `reference/komikku`)

- AGP `8.13.2`: `reference/komikku/gradle/androidx.versions.toml:2`
  (`agp_version = "8.13.2"`, 同文件 `gradle = { module = "com.android.tools.build:gradle", ... }`)。
- Gradle Wrapper `9.3.1`:
  `reference/komikku/gradle/wrapper/gradle-wrapper.properties`
  (`distributionUrl=...gradle-9.3.1-bin.zip`)。
- Kotlin `2.4.20` / coroutines `1.11.0` / serialization `1.11.0`:
  `reference/komikku/gradle/kotlinx.versions.toml` (`kotlin_version`/`coroutines_version`/`serialization_version`)。
- SDK/JDK 级别: `COMPILE_SDK = 36`、`TARGET_SDK = 36`、`MIN_SDK = 26`、
  `JavaVersion.VERSION_17` / `JvmTarget.JVM_17`:
  `reference/komikku/buildSrc/src/main/kotlin/mihon/buildlogic/AndroidConfig.kt:7-13`。
  引用链: `ProjectExtensions.kt` (`compileSdk`/`minSdk`/`sourceCompatibility`/`jvmTarget`)
  与 `mihon.android.application.gradle.kts` (`targetSdk`) 经 grep 实测。
- 应用标识: `applicationId = "app.komikku"`, `versionCode = 82`,
  `versionName = "1.14.1"`: `reference/komikku/app/build.gradle.kts`
  (`android { namespace = "eu.kanade.tachiyomi"; defaultConfig { ... } }`)。
- 构建类型: `debug` (`.dev` 后缀) / `release` (minify 按 `Config.enableCodeShrink`) /
  `releaseTest` / `foss` / `preview` (复用 `debug.signingConfig`) /
  `benchmark` (复用 `debug.signingConfig`): 同文件 `buildTypes { ... }`。
  release 正式签名配置未在仓库内声明 → release 签名密钥在仓库外 (pending, 见 §4 B2)。
  debug/preview/benchmark 走标准 debug 签名, 无额外密钥文件要求。
- ABI 分包: `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` + 通用 APK
  (`splits { abi { ... } }`, 同文件)。
- NDK/CMake: `buildSrc` 与 `app/build.gradle.kts` 内 grep 未发现 `ndkVersion` pin
  (`grep -r -E "ndkVersion|..."` 实测仅命中两处 `signingConfig = debug.signingConfig`);
  `packaging.jniLibs.keepDebugSymbols` 含 `libarchive-jni` 等 (同文件, 截断前可见)。
  本地 NDK `28.2.13676358` 已安装, 可用性未经编译验证 (原版构建未跑)。

## 2. 本地环境现状 (只读命令实测, 2026-11-18)

- JDK: `~/devtools/jdk-21`, `java -version` = Corretto `21.0.12.1`
  (`OpenJDK Runtime Environment Corretto-21.0.12.9.1`); `JAVA_HOME=/Users/kurisu/devtools/jdk-21`
  (环境变量实测)。三端构建均以 `JAVA_HOME="$HOME/devtools/jdk-21"` 运行。
- Android SDK (`$HOME/Library/Android/sdk`, `ls` 实测):
  `build-tools` (`36.0.0`, `37.0.0`) / `platforms` (`android-37`, `android-37.0`, `android-37.1`) /
  `ndk` (`28.2.13676358`) / `cmake` / `emulator` / `cmdline-tools` /
  `system-images` (`android-36/google_apis_ps16k`) / `licenses` / `platform-tools`。
  注意: `platforms` 无 `android-36` 目录 (仅 `android-37` 系列), 系统镜像为 `android-36`。
- adb: `adb devices` 实测输出 `List of devices attached` 后无设备
  (daemon 启动成功, 零设备)。`sdkmanager --list_installed` 未跑
  (目录 `ls` 已给出组件清单, 不重复安装/下载大件, 按任务要求)。
- 结论 (修正, 复核后降格): 构建侧部分齐备/待验证 (JDK 21 + SDK 目录齐,
  但原版 `COMPILE_SDK=36` 所需 `platforms/android-36` 缺失, 见 §4 B5;
  NDK 存在但未经编译验证, 见 §4 B5);
  安装侧阻塞 —— 无连接设备且无运行中模拟器 (见 §4 B1)。
  环境只读快照见附件 `docs/alignment/v0-06/env-probe.log` (本轮命令原文输出)。

## 3. 本项目三端构建 (实跑, JDK 21, `-PveneraReferenceDir` 带入)

三条命令均以 `JAVA_HOME="$HOME/devtools/jdk-21"`、仓库 `./gradlew`、
`"-PveneraReferenceDir=$PWD/reference/Venera-Next"` 运行, `--console=plain -q`,
单命令超时 600000ms (10 分钟, 均一次通过, 无重跑)。
证据口径 (修正, 复核后降格): 此为“三编译目标通过 (脏基线), 非可发布产物”——
仅第1条产出可安装 APK; 第2条仅编译类、无可交付产物 (且本轮疑为 UP-TO-DATE,
见下表); 第3条仅为模拟器 link 产物、非可安装应用。
控制台日志未留存 (`-q` quiet 运行, 无日志文件); `EXIT:0` 与 warnings 清单为当轮文本记录。
可复核附件: `docs/alignment/v0-06/artifacts-sha256.txt`
(APK/Framework SHA-256 + 尺寸 + mtime + desktop 非新鲜声明 + 完整性声明)。

| # | 命令 | 结果 |
|---|------|------|
| 1 | `./gradlew :apps:android:assembleDebug "-PveneraReferenceDir=$PWD/reference/Venera-Next"` | `EXIT:0` (quiet, 无输出; 文本记录, 无日志附件)。产物 `apps/android/build/outputs/apk/debug/android-debug.apk` (19,943,478 bytes, mtime Nov 18 23:39 本地时区 = 本轮新鲜产物; SHA 见附件) + `output-metadata.json`。模块名 `:apps:android` 经 `settings.gradle.kts` (`include(":apps:android")` 等) 核实。 |
| 2 | `./gradlew :apps:desktop:classes "-PveneraReferenceDir=$PWD/reference/Venera-Next"` | `EXIT:0` (quiet, 无输出; 文本记录, 无日志附件)。但 `apps/desktop/build/` 全树 (含 `classes/kotlin/main`、`libs/desktop.jar`) mtime 均为 Nov 17 00:45, 早于本轮运行 → 极可能为 UP-TO-DATE 跳过, 是否重编待确认; 无新鲜产物, 无可交付产物。desktop 可运行产物 (assemble/package) 未跑。 |
| 3 | `./gradlew :shared:app:linkDebugFrameworkIosSimulatorArm64 "-PveneraReferenceDir=$PWD/reference/Venera-Next"` | `EXIT:0` (文本记录, 无日志附件); 输出仅 Kotlin 废弃/冗余告警 (`w: ... ExtensionService.kt:383`、`LibraryScreen.kt`、`LibrarySheets.kt`、`ChapterListDialog.kt`、`ReaderChrome.kt`、`ComicViewer.kt`、`AppScaffold.kt`、`SourceScreens.kt`、`Instant` 别名, 均为 warning, 无 error; warnings 清单为文本记录, 无日志附件)。产物 `shared/app/build/bin/iosSimulatorArm64/debugFramework/ReaderShared.framework` (`ReaderShared` 二进制 288,250,528 bytes, mtime Nov 18 23:41 = 本轮新鲜 link; SHA 见附件)。 |

脏基线声明: 三条构建均在 HEAD `4f82b71` + 工作树 107 行 (脏) 下运行,
故记为脏基线通过, 待干净基线重跑确认, 不作最终通过。

## 4. 阻塞清单

- B1 (设备/安装路径阻塞): `adb devices` 零设备 → 原版 APK 安装、
  真机/模拟器配对截图与录屏均 pending。下一步 (确定步骤, 待执行):
  `emulator -list-avds` → 若无 AVD 则以 `system-images/android-36/google_apis_ps16k` 建 AVD
  (需写盘, 本任务未做) → 启动模拟器 → `adb devices` 确认 → `adb install` 原版/自研 APK
  → 记录设备/API/density/字体/语言/主题/系统栏 + APK SHA-256。
- B2 (原版 release 签名 pending): release 正式签名密钥不在仓库内;
  debug/preview/benchmark 路径不受影响 (复用 debug 签名)。
- B3 (脏基线待重跑): 本轮三端构建均为脏基线 (107 行) 记录,
  待干净基线重跑确认后方可作为最终证据。
- B4 (原版全量构建未验证): 本任务按范围仅只读探查, 未在 `reference/komikku`
  内运行任何构建; “原版可复现构建步骤”待实际跑通后 (含网络依赖与签名) 方可定稿。
- B5 (新增, 复核后登记: 本机原版构建环境待确认): `platforms` 缺 `android-36`
  (仅 `android-37` 系列, `ls` 实测, 快照见 `env-probe.log`), 而原版要求
  `COMPILE_SDK=36`; `android-37` 平台是否可向下兼容编译未经验证。
  NDK `28.2.13676358` 存在但未经编译验证 (原版构建未跑)。
  待原版构建实际跑通后确认, 在此之前“构建侧齐备”不成立 (见 §2 修正结论)。

## 5. V0 出口自评 (本轮增量)

- 达成: 原版构建要求清单落地 (§1, 全部引文件行号);
  本地 SDK/adb 现状落地 (§2 + 附件 `env-probe.log`, 全部为本轮实测命令输出);
  本项目三编译目标通过, 脏基线 (§3 + 附件 `artifacts-sha256.txt`;
  修正口径: 非“全绿可交付”, 仅 Android 得可安装 APK 新鲜产物,
  iOS 得模拟器 framework link 产物, desktop 疑为 UP-TO-DATE 且无可交付产物)。
  自研 APK SHA-256 已落附件 (`b48cf738…9d070`); 原版 APK SHA 仍 pending。
  V0-05 目标画布 20 张归档状态不变 (门禁退出码 0, 背景既定)。
- pending (V0 出口仍未达成): 原版侧同画布配对图、真机录屏、APK SHA、
  设备矩阵 (API/density/字体/语言/主题/系统栏)、统一夹具剩余项
  (见 `execution-state.md` §6)、c-chrome/目标画布干净基线重跑确认。
