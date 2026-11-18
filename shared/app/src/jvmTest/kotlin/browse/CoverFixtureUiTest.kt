package app.koyura.app.browse

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.CoverImageLoader
import app.koyura.designsystem.CoverMemoryCache
import app.koyura.designsystem.Cover
import app.koyura.designsystem.CoverImageTag
import app.koyura.designsystem.CoverPlaceholderTag
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import app.koyura.designsystem.LocalCoverMemoryCache
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 封面三态夹具（与阅读器固定编号页同等对待）：
 *
 * - 正常 URL → 真实解码图；
 * - 损坏字节 → 首字母占位（单次尝试，不重试）；
 * - 空 URL → 占位且不调加载器。
 */
class CoverFixtureUiTest {
    private val okPng: ByteArray =
        java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
    private val broken: ByteArray = byteArrayOf(0, 1, 2, 3)

    private class CountingLoader(
        private val bytes: Map<Pair<String, String>, ByteArray?>,
    ) : CoverImageLoader {
        val calls = mutableListOf<Pair<String, String>>()
        override suspend fun load(sourceKey: String, coverUrl: String): ByteArray? {
            calls += sourceKey to coverUrl
            return bytes[sourceKey to coverUrl]
        }
    }

    @Test
    fun normalUrlRendersDecodedArtOnce() = runComposeUiTest {
        val loader = CountingLoader(mapOf("src" to "https://example.invalid/ok.png" to okPng))
        var tick by mutableStateOf(0)
        setContent {
            CompositionLocalProvider(
                LocalCoverImageLoader provides loader,
                LocalCoverDecoder provides AppCoverDecoder,
                LocalCoverMemoryCache provides CoverMemoryCache(),
            ) {
                @Suppress("UNUSED_EXPRESSION")
                tick
                Cover(
                    sourceKey = "src",
                    coverUrl = "https://example.invalid/ok.png",
                    label = "星图",
                )
            }
        }
        waitForIdle()
        onNodeWithTag(CoverImageTag).assertIsDisplayed()
        onAllNodesWithTag(CoverPlaceholderTag).assertCountEquals(0)

        // 重组不得二次加载：缓存命中。
        tick = 1
        waitForIdle()
        onNodeWithTag(CoverImageTag).assertIsDisplayed()
        assertEquals(1, loader.calls.size)
    }

    @Test
    fun brokenBytesFallBackToPlaceholderWithoutRetry() = runComposeUiTest {
        val loader = CountingLoader(mapOf("src" to "https://example.invalid/broken.png" to broken))
        var tick by mutableStateOf(0)
        setContent {
            CompositionLocalProvider(
                LocalCoverImageLoader provides loader,
                LocalCoverDecoder provides AppCoverDecoder,
                LocalCoverMemoryCache provides CoverMemoryCache(),
            ) {
                @Suppress("UNUSED_EXPRESSION")
                tick
                Cover(
                    sourceKey = "src",
                    coverUrl = "https://example.invalid/broken.png",
                    label = "星图",
                )
            }
        }
        waitForIdle()
        onNodeWithTag(CoverPlaceholderTag).assertIsDisplayed()
        onAllNodesWithTag(CoverImageTag).assertCountEquals(0)

        tick = 1
        waitForIdle()
        assertEquals(1, loader.calls.size)
    }

    @Test
    fun emptyUrlShowsPlaceholderAndNeverLoads() = runComposeUiTest {
        val loader = CountingLoader(emptyMap())
        setContent {
            CompositionLocalProvider(
                LocalCoverImageLoader provides loader,
                LocalCoverDecoder provides AppCoverDecoder,
                LocalCoverMemoryCache provides CoverMemoryCache(),
            ) {
                Cover(sourceKey = "src", coverUrl = "", label = "星图")
            }
        }
        waitForIdle()
        onNodeWithTag(CoverPlaceholderTag).assertIsDisplayed()
        assertEquals(0, loader.calls.size)
    }

    @Test
    fun sameUrlAcrossSourcesLoadsSeparately() = runComposeUiTest {
        val url = "https://example.invalid/shared.png"
        val loader = CountingLoader(mapOf("a" to url to okPng, "b" to url to broken))
        setContent {
            CompositionLocalProvider(
                LocalCoverImageLoader provides loader,
                LocalCoverDecoder provides AppCoverDecoder,
                LocalCoverMemoryCache provides CoverMemoryCache(),
            ) {
                Cover(sourceKey = "a", coverUrl = url, label = "甲")
                Cover(sourceKey = "b", coverUrl = url, label = "乙")
            }
        }
        waitForIdle()
        // a 解码成功出一图，b 损坏回占位：按源隔离无串扰。
        onAllNodesWithTag(CoverImageTag).assertCountEquals(1)
        onAllNodesWithTag(CoverPlaceholderTag).assertCountEquals(1)
        assertEquals(2, loader.calls.size)
    }
}
