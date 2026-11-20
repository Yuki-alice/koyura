package app.koyura.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Komikku 白皮书设计令牌（whitepaper 02 + `assets/design-tokens.json`）。
 *
 * Code 级出处见各常量 KDoc；未注明即直接来自 tokens.json 对应键。组件只消费这里的
 * 语义常量，不直接写 dp/alpha/ms 数值（白皮书 02 复刻检查清单第 1、6 条）。
 */
object DesignTokens {
    object ReaderColors {
        val BlackBackground = Color.Black
        val GrayBackground = Color(0xFF202125)
        val WhiteBackground = Color.White
    }


    // Spacing — whitepaper 02 §2.7 间距令牌 / tokens.json `spacing` (Code).
    object Spacing {
        val Hairline = 1.dp
        val Tight = 2.dp
        val Compact = 6.dp
        val Row = 10.dp
        val Regular = 12.dp
        val ExtraSmall = 4.dp
        val Small = 8.dp
        val Medium = 16.dp
        val Large = 24.dp
        val ExtraLarge = 32.dp
    }

    // Radius — whitepaper 02 §2.7 圆角 / tokens.json `radius` (Code).
    object Radius {
        val ExtraSmall = 4.dp
        val Small = 8.dp
        val Medium = 12.dp
        val Large = 16.dp
        val ExtraLarge = 28.dp

        // 特殊圆角 — tokens.json `radius`，原版引用见各 KDoc (Code).
        // SettingsMainScreen.kt:146 选中行
        val SettingsSelected = 24.dp

        // AppThemePreferenceWidget.kt:123 主题预览内外层
        val ThemePreviewOuter = 17.dp
        val ThemePreviewInner = 13.dp
    }

    // Alpha — whitepaper 02 §2.7 透明度常量 / tokens.json `alpha` (Code).
    // 原版定义：Constants.kt (DISABLED_ALPHA/SECONDARY_ALPHA)、Modifier.kt
    // (selectedBackground 0.22/0.16)、CommonMangaItem.kt:71 (GRID_SELECTED_COVER_ALPHA)、
    // LibraryToolbar.kt:97 / DownloadQueueScreen.kt:120 (pillAlpha)、
    // ChapterNavigator.kt:90 / ReaderAppBars.kt:106 (reader bars)、
    // MangaInfoHeader.kt:183 (backdrop)、CommonMangaItem.kt:517 (continueReading)。
    object Alpha {
        const val Disabled = 0.38f
        const val Secondary = 0.78f
        const val SelectedBackgroundLight = 0.22f
        const val SelectedBackgroundDark = 0.16f
        const val GridSelectedCover = 0.76f
        const val PillCountLight = 0.08f
        const val PillCountDark = 0.12f
        const val ReaderBarLight = 0.95f
        const val ReaderBarDark = 0.9f
        const val Backdrop = 0.2f
        // 详情封面背景的 surfaceTint 衬底 — MangaInfoHeader.kt:179
        // `surfaceTint.copy(alpha = 0.4f)`，白皮书 02 §2.1 封面取色层。
        const val SurfaceTintWash = 0.4f
        const val ContinueReading = 0.9f
    }

    // Elevation — whitepaper 02 §2.7 高度 / tokens.json `elevation` (Code).
    // AppBar 静息/滚动与导航轨 tonal elevation 见 NavigationBar.kt / NavigationRail.kt。
    object Elevation {
        val Flat = 0.dp
        val Raised = 2.dp
        val Dialog = 6.dp
        val AppBarResting = 0.dp
        val AppBarScrolled = 3.dp
        val Rail = 3.dp
    }

    // Motion — whitepaper 05 动效四档 / tokens.json `motion` (Code).
    // Tab fadeThrough 见 Navigator.kt materialSharedAxisX；reader bars 见 ReaderAppBars.kt:38-39；
    // sheet 350ms 见 AdaptiveSheet.kt:259；sheet 导航淡入淡出见 AdaptiveSheet.kt:36-37；
    // 设置高亮 3s 见 BasePreferenceWidget.kt:91-96 高亮循环；封面交叉淡入 300ms 见 02 §2.8。
    object Motion {
        const val TabFadeThroughMs = 200
        // 页面 push/pop 的 shared-axis-X，白皮书 05 §5.1 #2「~300ms（M3 内置）」。
        const val PageSharedAxisMs = 300
        const val ReaderBarsSlideMs = 200
        const val ReaderBarsFadeMs = 150
        const val SheetMs = 350
        const val SheetNavFadeInMs = 220
        const val SheetNavFadeInDelayMs = 90
        const val SheetNavFadeOutMs = 90
        const val PreferenceHighlightMs = 3000
        const val ImageCrossfadeMs = 300
        val HazeBlur = 10.dp
    }

    // Breakpoints & layout caps — whitepaper 03 §3.1/§3.4/§3.7 / tokens.json `breakpoints` (Code).
    // 平板判定与自动模式阈值见 DisplayExtensions.kt:13-19；双栏左限见 TwoPanelBox.kt:31；
    // 弹层/对话框宽度见 AdaptiveSheet.kt:88 与 SettingsEhScreen.kt:1048。
    object Breakpoints {
        const val TabletMinSmallestWidthDp = 720
        const val TabletAutomaticPortraitDp = 700
        const val TabletAutomaticLandscapeDp = 600
        const val AdaptiveGridMinItemDp = 128
        val TwoPaneFirstMax: Dp = 450.dp
        val SheetMaxWidth: Dp = 460.dp
        val DialogMinWidth: Dp = 280.dp
        val DialogMaxWidth: Dp = 560.dp
        val SettingsMasterWidth = 320.dp
        val CatalogResultsMaxHeight = 320.dp
        val UpdatePolicyMaxHeight = 420.dp
        val DetailCoverMinWidth = 100.dp
        val DetailCoverMaxWidth = 240.dp
    }

    // Sizes — whitepaper 02 §2.7 高度/尺寸 / tokens.json `sizes` (Code).
    // 底栏 80dp 见 NavigationBar.kt:43；导航轨见 NavigationRail.kt:47；设置项见 PreferenceItem.kt；
    // 章节项内边距见白皮书 02 表格；历史/列表/徽标/滚动条/阅读器滑块/底部菜单见 tokens.json。
    object Sizes {
        val MinimumTouchTarget = 48.dp
        val DetailActionIcon = 20.dp
        val SelectionOutline = 2.dp
        val NavigationBarHeight = 80.dp
        val NavigationRailMinWidth = 80.dp
        val SettingsItemMinHeight = 56.dp
        val ChapterItemPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
        val HistoryItemHeight = 96.dp
        val HistoryCoverWidth = 63.dp
        val HistoryCoverHeight = 94.dp
        // 章节未读点 / 历史未完成点 — 白皮书 09 §9.1：MangaChapterListItem.kt:128、
        // HistoryItem.kt:178 都是 height(8.dp)。
        val UnreadDot = 8.dp
        // 下载指示器直径 — ChapterDownloadIndicator.kt:271 `IndicatorSize = 26.dp`。
        val DownloadIndicator = 26.dp
        // 扇形进度的描边 = 直径/2 — ChapterDownloadIndicator.kt:158。
        val DownloadProgressStroke = 13.dp
        // 下载箭头 = 直径 − 7dp — ChapterDownloadIndicator.kt:281。
        val DownloadArrow = 19.dp
        // 线性进度条描边（M3 LinearProgressIndicator 口径），与扇形描边区分开。
        val ProgressBarStroke = 2.dp
        val TopBarHeight = 64.dp
        val BrandLogo = 64.dp
        val BrandHeaderPadding = 56.dp
        val BrowseIcon = 40.dp
        val SelectionMarker = 40.dp
        val SearchCoverWidth = 48.dp
        val SearchCoverHeight = 72.dp
        // 详情封面宽度上限 — MangaInfoHeader.kt:622 `sizeIn(maxWidth = 100.dp)`，高度按 2:3 推导 150dp。
        val DetailCoverMaxWidthPhone = 100.dp
        val DetailCoverHeightPhone = 150.dp
        // 列表封面：行高 56 − 2×8 = 40dp 的方形（CommonMangaItem.kt:469 `MangaCover.Square`）。
        val LibraryListCoverSize = 40.dp
        val PanoramaCoverHeight = 160.dp
        // 主题预览卡 — AppThemePreferenceWidget.kt:95 `.width(114.dp)`；📐实测 228px ÷ 2 = 114dp。
        val ThemePreviewCard = 114.dp
        val ThemePreviewSwatch = 40.dp
        val CatalogInputMinHeight = 120.dp
        val ModeCardWidth = 104.dp
        val ModeIconWidth = 44.dp
        val ModeIconHeight = 32.dp
        val PagedStripGap = 15.dp
        val ListItemHeight = 56.dp
        val BadgeHeight = 18.dp
        // 徽标文字内边距 — Badges.kt:62,107 `padding(horizontal = 3.dp, vertical = 1.dp)`，
        // 白皮书 06 §6.4。
        val BadgeContentPadding = PaddingValues(horizontal = 3.dp, vertical = 1.dp)
        // 书架继续阅读按钮 28dp / 图标 16dp — CommonMangaItem.kt:62,65。
        val ContinueButtonSize = 28.dp
        val ContinueButtonIconSize = 16.dp
        // 更新组封面宽 48dp — UpdatesUiItem.kt:475 `UpdateItemWidth = 48.dp`；白皮书 07 §7.7。
        // 全景封面 108dp（UpdateItemPanoramaWidth）待真实封面比例检测后启用，本轮 Fake 固定 Book 比例。
        val UpdateCoverWidth = 48.dp
        val FastScrollerThumb = 12.dp
        // 详情封面背景模糊半径 — MangaInfoHeader.kt:180 `.blur(7.dp)`。
        val BackdropBlur = 7.dp
        val ReaderSliderRadius = 24.dp
        val ReaderSliderThumb = 16.dp
        val BottomActionMenuRadius = 16.dp
        // 手机底部弹层下滑关闭阈值 — AdaptiveSheet.kt `swipeThreshold = 56.dp`（白皮书 06 §6.13）。
        val SheetDismissThreshold = 56.dp
    }
}
