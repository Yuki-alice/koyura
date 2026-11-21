# 验收基线

2026-11-19 · 本页是唯一验收口径，替代已移除的阶段性对齐规划与证据归档。验收只回答两个问题：改动后代码是否仍然全绿，界面行为是否仍然正确。不设阶段门禁，不维护 SHA 清单、manifest 或执行状态档案。

## 参考基线

- 架构重构以项目既有行为和数据兼容性为回归基线。涉及产品行为变化时，按本次确认的交互要求验收。
- `reference/komikku` 的 `290267d` 可用于查阅交互与实现。旧白皮书已退出验收基线。
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

2026-11-21 实测基线：722 用例全绿（domain 13 / features 107 / data 136 / source-runtime 65 / app 401）。此数字只用于察觉测试意外丢失，不另设达标线。

## 三端构建（触及入口或平台代码时）

```bash
./gradlew :apps:android:assembleDebug
./gradlew :apps:desktop:classes
./gradlew :shared:app:linkDebugFrameworkIosSimulatorArm64
```

## 源运行时回归（硬性）

改动 `:shared:source-runtime`、`:shared:network` 会话桥或宿主回调后，必跑 Venera 原版脚本回归（命令见 README「Venera 脚本验证」），核对脚本 SHA-256 与全回调链。

改动 HTML 解析、会话或图片路径时，另跑未修改社区源脚本的回归；需先下载夹具并传 `-PveneraCommunityFixtureDir`。**清单以当前回归测试实际消费的脚本为准**（新增社区源后同步更新这里，否则传参会因缺文件假红）：

```bash
community_dir=build/community-scripts && mkdir -p "$community_dir"
for script in baozi mxs manhuagui komga comic_walker manga_dex; do
  curl -fsSL "https://raw.githubusercontent.com/venera-app/venera-configs/a04e05591f7832f7166d6660546613c114d6738a/$script.js" \
    -o "$community_dir/$script.js"
done
```

未传该参数时社区源用例自动跳过，标准门保持轻量；传入参数但夹具缺失会如实失败。

## 视觉核对（按需，不留档案）

改动哪个页面就核对哪个页面，不整轮铺开：

- 按本次改动覆盖的主题和窗口宽度人工核对。需要比对时，用相同窗口、主题和测试数据截取项目改动前后的界面，截图可暂存 `build/`。
- 滚动、手势、菜单类改动在桌面或模拟器上手工走一遍操作路径，不产出录屏归档。
- 仅界面重大定稿需要入库截图时，直接提交 PNG 并在提交信息说明，不附清单或 manifest。

## 阅读行为验收

沿用测试内的阅读夹具（编号页、横跨页、长图、损坏页、两章）断言当前章页、翻页方向、失败重试、模式切换保位与跨进程续读；夹具即测试代码，不单建文档。
