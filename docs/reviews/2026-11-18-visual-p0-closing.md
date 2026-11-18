# P0 设计令牌收尾完成记录

2026-11-18 · 对应 [visual-parity-plan.md](../visual-parity-plan.md) P0。

## 审计结论

- `ReaderTheme.kt` 浅/深/AMOLED 三套色板与白皮书 9.1 tokens.css 逐值一致；AMOLED 容器分层 `#0C0C0C/#131313/#1B1B1B` 正确。
- `KomikkuTokens.kt`（现 `DesignTokens.kt`）间距/圆角/透明度/高度/动效四档/断点/尺寸全量在位、带出处，无需补值。
- 白皮书 02.6 确认无自定义字体与 Typography；唯一自定义样式 `header` 已有等价实现。
- 组件层 hex 扫描零违规（9.5 条目 01 过）。

## 收尾三项（全部完成）

1. `ReaderTheme.kt` 头部 KDoc 修正：删除误导的 `#2979FF/#47A84A` 基线族描述，改为按 `TachiyomiColorScheme` 实值说明。
2. 重复常量收敛：`KomikkuDisabledAlpha/KomikkuSecondaryAlpha` 删除，全部改引 `DesignTokens.Alpha.Disabled/Secondary`。
3. 命名清理（按任务书映射执行）：`DesignTokens.kt` 嵌套结构（`DesignTokens.Spacing/Radius/Alpha/Elevation/Motion/Breakpoints/Sizes`）、`ActionIcon`、`Cover/CoverImageTag/CoverPlaceholder/CoverPlaceholderTag/CoverCornerRadius`、`ListGroupHeader`、`BrowseRow`、`IconPlaceholder`、`DetailBackdrop`、`SearchField`、`TabletRule/TabletBaseline`、`Typography.header`、文件 `Covers/Lists/ActionIcon/SearchField.kt`、测试类 `ShellScreenshotsTest`。全仓代码零 `Komikku[A-Z]` 残留；注释引用来源不受限。

## 验证

`./gradlew :shared:design-system:jvmTest :shared:app:jvmTest :shared:features:jvmTest` — BUILD SUCCESSFUL（2m26s），含 `ShellScreenshotsTest` 与 viewer/chrome 截图测试。过程中修复一处 sed 暴露的既有问题：`LibraryScreen.kt` 存在重复 import 行（awk 全仓去重）。

## 裁决

P0 关闭，P1（外壳与形态切换复核）放行。
