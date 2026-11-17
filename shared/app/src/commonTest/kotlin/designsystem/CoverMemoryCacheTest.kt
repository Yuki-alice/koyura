package app.koyura.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 共用封面内存缓存：按源隔离、LRU 上限、磁盘键命名。
 *
 * 纯内存断言，不依赖平台图形栈；加载单次语义与占位回退由 UI 测试覆盖。
 */
class CoverMemoryCacheTest {
    @Test
    fun putAndGetRoundTrip() {
        val cache = CoverMemoryCache()
        val bytes = byteArrayOf(1, 2, 3)
        cache.put("src", "https://example.invalid/a.png", bytes)
        assertTrue(cache.get("src", "https://example.invalid/a.png") === bytes)
    }

    @Test
    fun sameUrlAcrossSourcesIsIsolated() {
        val cache = CoverMemoryCache()
        val url = "https://example.invalid/shared.png"
        cache.put("src-a", url, byteArrayOf(1))
        cache.put("src-b", url, byteArrayOf(2))

        assertEquals(listOf(1.toByte()), cache.get("src-a", url)?.toList())
        assertEquals(listOf(2.toByte()), cache.get("src-b", url)?.toList())

        // 卸载一源只清该源，另一源不受影响。
        cache.evictSource("src-a")
        assertNull(cache.get("src-a", url))
        assertNotNull(cache.get("src-b", url))
    }

    @Test
    fun evictsEldestBeyondCap() {
        val cache = CoverMemoryCache(maxPerSource = 2)
        cache.put("src", "u1", byteArrayOf(1))
        cache.put("src", "u2", byteArrayOf(2))
        cache.put("src", "u3", byteArrayOf(3))

        assertNull(cache.get("src", "u1"))
        assertEquals(listOf(2.toByte()), cache.get("src", "u2")?.toList())
        assertEquals(listOf(3.toByte()), cache.get("src", "u3")?.toList())
        assertEquals(setOf("u2", "u3"), cache.cachedUrls("src"))
    }

    @Test
    fun clearDropsEverything() {
        val cache = CoverMemoryCache()
        cache.put("a", "u", byteArrayOf(1))
        cache.put("b", "u", byteArrayOf(2))
        cache.clear()
        assertTrue(cache.cachedUrls("a").isEmpty())
        assertTrue(cache.cachedUrls("b").isEmpty())
    }

    @Test
    fun diskKeyIsNamespacedBySource() {
        val url = "https://example.invalid/c.png"
        val a = coverDiskKey("src-a", url)
        val b = coverDiskKey("src-b", url)
        assertNotEquals(a, b)
        assertTrue(a.startsWith("cover/src-a/"))
        // 同一源同一 URL 稳定。
        assertEquals(a, coverDiskKey("src-a", url))
    }
}
