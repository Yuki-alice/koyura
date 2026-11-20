package app.koyura.features.statistics

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.statistics.FakeStatisticsRepository
import app.koyura.features.statistics.StatisticsErrorTag
import app.koyura.features.statistics.StatisticsRetryTag
import app.koyura.features.statistics.StatisticsRootTag
import app.koyura.features.statistics.StatisticsScreen
import app.koyura.features.statistics.StatisticsSession
import app.koyura.features.statistics.statisticsMetricTag
import app.koyura.features.statistics.statisticsSectionTag
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 统计页结构回归：四张卡片、三列数值、格式串与失败重试。
 */
class StatisticsUiTest {
    @Test
    fun fourSectionsRenderWhitepaperMetrics() = runComposeUiTest {
        val session = StatisticsSession(FakeStatisticsRepository())
        setContent {
            ReaderTheme {
                StatisticsScreen(session = session, onBack = {})
            }
        }
        waitForIdle()

        onNodeWithTag(StatisticsRootTag).assertIsDisplayed()
        listOf("overview", "entries", "chapters", "trackers").forEach { section ->
            onNodeWithTag(statisticsSectionTag(section)).performScrollTo().assertIsDisplayed()
        }
        // 数值列与格式串：阅读时长按 4m 13s 呈现，平均分缺省值非空（精确格式由
        // commonTest 按语言无关默认值断言，此处只断渲染与非空）。
        onNodeWithTag(statisticsMetricTag("overview", "readDuration")).assertIsDisplayed()
        onNodeWithText("4m 13s").assertIsDisplayed()
        val meanScoreTexts = onNodeWithTag(statisticsMetricTag("trackers", "meanScore"))
            .performScrollTo()
            .fetchSemanticsNode()
            .allTexts()
        assertTrue(meanScoreTexts.any { it.isNotBlank() })
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() = runComposeUiTest {
        val repository = FakeStatisticsRepository()
        repository.failure = "network down"
        val session = StatisticsSession(repository)
        setContent {
            ReaderTheme {
                StatisticsScreen(session = session, onBack = {})
            }
        }
        waitForIdle()

        onNodeWithTag(StatisticsErrorTag).assertIsDisplayed()
        onNodeWithTag(StatisticsRetryTag).performClick()
        waitForIdle()

        onNodeWithTag(statisticsSectionTag("overview")).assertIsDisplayed()
    }
}

private fun SemanticsNode.allTexts(): List<String> {
    val own = config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    return own + children.flatMap { it.allTexts() }
}
