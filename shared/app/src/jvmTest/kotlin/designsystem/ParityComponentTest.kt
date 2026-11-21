package app.koyura.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.ReaderTheme
import kotlin.test.Test

/**
 * T1 组件配方的尺寸验收（白皮书 06 §6.6/§6.7/§6.11/§6.12/§6.15）。
 * 只断言尺寸与可见性，不做像素 golden——抗锯齿会让色值断言不稳定。
 */
class ParityComponentTest {

    @Test
    fun badgeGroupIsEighteenDpTall() = runComposeUiTest {
        setContent {
            ReaderTheme {
                BadgeGroup(Modifier.testTag("badge-group")) {
                    GroupBadge("3")
                    GroupBadge("12")
                }
            }
        }
        onNodeWithTag("badge-group").assertHeightIsEqualTo(DesignTokens.Sizes.BadgeHeight)
    }

    @Test
    fun tagChipIsThirtyTwoDpTall() = runComposeUiTest {
        setContent {
            ReaderTheme {
                TagChip(label = "Romance", onClick = {}, modifier = Modifier.testTag("tag-chip"))
            }
        }
        onNodeWithTag("tag-chip").assertHeightIsEqualTo(32.dp)
    }

    @Test
    fun downloadIndicatorIsTwentySixDp() = runComposeUiTest {
        setContent {
            ReaderTheme {
                ChapterDownloadIndicator(
                    state = ChapterDownloadState.DOWNLOADING,
                    progress = 0.5f,
                    modifier = Modifier.testTag("download"),
                )
            }
        }
        onNodeWithTag("download")
            .assertWidthIsEqualTo(DesignTokens.Sizes.DownloadIndicator)
            .assertHeightIsEqualTo(DesignTokens.Sizes.DownloadIndicator)
    }

    @Test
    fun downloadStateLayerSizesMatchTokens() = runComposeUiTest {
        setContent {
            ReaderTheme {
                ChapterDownloadIndicator(
                    state = ChapterDownloadState.DOWNLOADED,
                    modifier = Modifier.testTag("done").size(DesignTokens.Sizes.DownloadIndicator),
                )
            }
        }
        onNodeWithTag("done").assertHeightIsEqualTo(26.dp)
    }

    @Test
    fun bottomActionBarRevealsLabelsAfterOneSecondHold() = runComposeUiTest {
        setContent {
            ReaderTheme {
                BottomActionBar(
                    actions = listOf(
                        BottomAction(ActionIcon.Favorite, "加入书架", onClick = {}),
                        BottomAction(ActionIcon.Download, "下载", onClick = {}),
                    ),
                    modifier = Modifier.testTag("bottom-actions"),
                )
            }
        }
        // 未展开时标签只是无障碍名，不渲染文字节点。
        onNodeWithText("加入书架").assertDoesNotExist()
        onNodeWithTag("bottom-actions").performTouchInput {
            down(center)
            advanceEventTime(1200)
            up()
        }
        waitForIdle()
        onNodeWithText("加入书架").assertExists()
    }

    @Test
    fun emptyScreenKeepsKaomojiLeftToRight() = runComposeUiTest {
        setContent {
            ReaderTheme {
                EmptyScreen(emoji = "(╯°□°)╯", message = "书架还是空的")
            }
        }
        onNodeWithText("(╯°□°)╯").assertExists()
        onNodeWithText("书架还是空的").assertExists()
    }

    @Test
    fun bannerUsesSemanticContainer() = runComposeUiTest {
        setContent {
            ReaderTheme {
                StatusBanner(
                    kind = BannerKind.INCOGNITO,
                    text = "隐身模式",
                    modifier = Modifier.testTag("banner"),
                )
            }
        }
        onNodeWithTag("banner").assertExists()
        onNodeWithText("隐身模式").assertExists()
    }
}
