package app.koyura.app.navigation

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 注册表兜底（G3）。
 *
 * JVM 反射遍历密封层级与 [RouteRegistry.registeredKeys] 对账：
 * 新增路由键必须同步登记，否则恢复（进程死亡/配置变更）时反序列化失败。
 * 生产代码保持显式手写（iOS 无反射）；反射只出现在本 JVM 单测。
 */
class RouteRegistryTest {
    private fun collectKeys(root: KClass<out AppKey>): Set<KClass<out AppKey>> {
        val subs = root.sealedSubclasses
        if (subs.isEmpty()) return setOf(root)
        return subs.flatMapTo(mutableSetOf()) { collectKeys(it) }
    }

    @Test
    fun registryCoversEveryRouteKey() {
        val expected = collectKeys(AppKey::class)
        assertEquals(
            expected,
            RouteRegistry.registeredKeys.toSet(),
            "新增路由键必须同步登记到 RouteRegistry.registeredKeys 与多态块",
        )
    }

    @Test
    fun registryHasNoDuplicates() {
        val keys = RouteRegistry.registeredKeys
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun routesSitOnTheirLayers() {
        val keys = RouteRegistry.registeredKeys.toSet()
        assertTrue(keys.contains(TabRootRoute::class))
        assertTrue(keys.contains(ComicReaderRoute::class))
        // 阅读器键只能进阅读器层，tab 根只能进 tab 栈（类型即契约）。
        assertTrue(ComicReaderRoute::class.sealedSubclasses.isEmpty())
        assertTrue(ReaderKey::class.sealedSubclasses.contains(ComicReaderRoute::class))
        assertTrue(TabKey::class.sealedSubclasses.contains(TabRootRoute::class))
        assertTrue(SharedKey::class.sealedSubclasses.size >= 10)
    }
}
