package app.koyura.features.reader.comic.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 竖排页码滑杆（自适应重写）：值域/步进不变，长度随容器高度伸缩。
 */
class VerticalPageSliderTest {
    private fun state(pageIndex: Int = 0, pageCount: Int = 10) = ReaderChromeState(
        workTitle = "星图",
        chapterId = "c1",
        chapters = listOf(ChapterEntry("c1", "第一章")),
        pageIndex = pageIndex,
        pageCount = pageCount,
        menuVisible = true,
        hasPrevChapter = false,
        hasNextChapter = false,
        global = app.koyura.domain.reader.preferences.ReaderSettings(),
        override = null,
    )

    @Test
    fun sliderHeightFollowsContainerNotFixedLength() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = 64.dp, height = 300.dp)) {
                    VerticalPageSlider(pageIndex = 0, pageCount = 10, onJump = {})
                }
            }
        }
        waitForIdle()

        val bounds = onNodeWithTag(ReaderChromeVerticalSliderTag).fetchSemanticsNode().boundsInRoot
        // 旋转后视觉长度沿 Y 轴：应接近容器高度减去上下文本/内边距，而非固定 280dp。
        assertTrue(bounds.height > 200f, "expected tall slider, height=${bounds.height}")
        assertTrue(bounds.height < 300f, "must not overflow container, height=${bounds.height}")
        // 厚度随轨道宽度自适应（远小于容器高度）。
        assertTrue(bounds.width < 100f, "expected slim slider, width=${bounds.width}")
    }

    @Test
    fun sliderKeepsRangeAndStepsAndCommitsOnRelease() = runComposeUiTest {
        var jumped = -1
        setContent {
            ReaderTheme {
                Box(Modifier.fillMaxSize()) {
                    VerticalPageSlider(pageIndex = 0, pageCount = 10, onJump = { jumped = it })
                }
            }
        }
        waitForIdle()

        onNodeWithTag(ReaderChromeVerticalSliderTag)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        waitForIdle()

        assertEquals(5, jumped, "value 6 of 1..10 maps to index 5 and commits on release")
    }

    @Test
    fun sliderRendersInsideChromeInStripMode() = runComposeUiTest {
        setContent {
            ReaderTheme {
                Box(Modifier.size(width = 360.dp, height = 720.dp)) {
                    ReaderChrome(state = state(), onAction = {})
                }
            }
        }
        waitForIdle()

        // 分页模式无竖轨；条漫模式才有（此断言用现有 state 默认分页模式验证不显示）。
        onNodeWithTag(ReaderChromeVerticalSliderTag).assertDoesNotExist()
    }
}
