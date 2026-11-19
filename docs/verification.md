# 验收基线

2026-11-19 · 本页是唯一验收口径，替代已移除的阶段性对齐规划与证据归档。验收只回答两个问题：改动后代码是否仍然全绿，界面行为是否仍然正确。不设阶段门禁，不维护 SHA 清单、manifest 或执行状态档案。

## 参考基线

- 界面与交互对照：`reference/komikku` 的 `290267d`（与设计白皮书同版），能力以源码为准。
- JS 源协议兼容：`reference/Venera-Next` 的 `c0c4597`，原版脚本与源仓库应原样导入。
- 开发顺序与范围经讨论确定，不预先固化在文档里。

## 快速回归（每次交付）

只跑受影响模块：

```bash
./gradlew :shared:<module>:jvmTest
```

## 全量门（交付前必跑）

```bash
JAVA_HOME="$HOME/devtools/jdk-21" ./gradlew \
  :shared:domain:jvmTest :shared:features:jvmTest :shared:data:jvmTest \
  :shared:source-runtime:jvmTest :shared:app:jvmTest \
  -PveneraReferenceDir="$PWD/reference/Venera-Next"
```

2026-11-19 实测基线：499 用例全绿（domain 11 / features 69 / data 105 / source-runtime 48 / app 266）。此数字只用于察觉测试意外丢失，不另设达标线。

## 三端构建（触及入口或平台代码时）

```bash
./gradlew :apps:android:assembleDebug
./gradlew :apps:desktop:classes
./gradlew :shared:app:linkDebugFrameworkIosSimulatorArm64
```

## 源运行时回归（硬性）

改动 `:shared:source-runtime`、`:shared:network` 会话桥或宿主回调后，必跑 Venera 原版脚本回归（命令见 README「Venera 脚本验证」），核对脚本 SHA-256 与全回调链。

## 视觉核对（按需，不留档案）

改动哪个页面就核对哪个页面，不整轮铺开：

- 窄/宽 × 浅/深四态截图，与 `reference/komikku` 的同页面对照；截图存 `build/` 临时目录，人工比对后即弃。
- 滚动、手势、菜单类改动在桌面或模拟器上手工走一遍操作路径，不产出录屏归档。
- 仅界面重大定稿需要入库截图时，直接提交 PNG 并在提交信息说明，不附清单或 manifest。

## 阅读行为验收

沿用测试内的阅读夹具（编号页、横跨页、长图、损坏页、两章）断言当前章页、翻页方向、失败重试、模式切换保位与跨进程续读；夹具即测试代码，不单建文档。
