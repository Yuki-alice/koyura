package app.koyura.app.navigation

import app.koyura.designsystem.navigation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationStateTest {
    private fun state(
        initialTab: NavTab = NavTab.Library,
        onReselect: (NavTab) -> Unit = {},
    ) = createAppNavigationState(initialTab = initialTab, onTabReselect = onReselect)

    private fun readerRoute(workId: String = "w1") = ComicReaderRoute(
        sourceKey = "src",
        workId = workId,
        chapterId = "c1",
        title = workId,
    )

    @Test
    fun startsOnLibraryWithRootOfEveryTab() {
        val nav = state()

        assertEquals(NavTab.Library, nav.selectedTab)
        NavTab.entries.forEach { tab ->
            val stack = nav.stackOf(tab)
            assertEquals(1, stack.size)
            assertEquals(TabRootRoute(tab), stack.first())
        }
        assertTrue(nav.sharedStack.isEmpty())
        assertTrue(nav.readerStack.isEmpty())
        assertEquals(TabRootRoute(NavTab.Library), nav.topKey)
    }

    @Test
    fun selectSwitchesTabWithoutTouchingStacks() {
        val nav = state()

        nav.select(NavTab.Browse)

        assertEquals(NavTab.Browse, nav.selectedTab)
        assertEquals(listOf(TabRootRoute(NavTab.Library)), nav.stackOf(NavTab.Library).toList())
    }

    @Test
    fun reselectOnCurrentTabEmitsOnceAndDoesNotPush() {
        val events = mutableListOf<NavTab>()
        val nav = state(NavTab.History) { events += it }

        nav.select(NavTab.History)
        nav.select(NavTab.History)

        assertEquals(listOf(NavTab.History, NavTab.History), events)
        assertEquals(1, nav.stackOf(NavTab.History).size)
        assertEquals(NavTab.History, nav.selectedTab)
    }

    @Test
    fun reselectKeepsSharedStackAndRunsAction() {
        val events = mutableListOf<NavTab>()
        val nav = state(NavTab.Library) { events += it }

        nav.push(DetailRoute(NavTab.Library, 0))
        nav.select(NavTab.Library)

        // G1：重复点击不再 popToRoot，只跑动作，共享栈原样保留。
        assertEquals(
            listOf(DetailRoute(NavTab.Library, 0)),
            nav.sharedStack.toList(),
        )
        assertEquals(listOf(TabRootRoute(NavTab.Library)), nav.stackOf(NavTab.Library).toList())
        assertEquals(listOf(NavTab.Library), events)
        assertEquals(NavTab.Library, nav.selectedTab)
    }

    @Test
    fun reselectActionsArePerTab() {
        val calls = mutableListOf<String>()
        val nav = createAppNavigationState(
            reselectActions = mapOf(
                NavTab.Library to { calls += "library" },
                NavTab.Browse to { calls += "browse" },
            ),
        )

        nav.select(NavTab.Library)
        nav.select(NavTab.Browse)
        nav.select(NavTab.Browse)
        nav.select(NavTab.Updates)

        // 切 tab 只换栈不跑动作；同 tab 再点才跑各自注册的动作；
        // Updates 未注册动作，保持无操作不断言崩溃。
        assertEquals(listOf("library", "browse"), calls)
        assertEquals(NavTab.Updates, nav.selectedTab)
    }

    @Test
    fun reselectActionCanPushWithoutPopping() {
        val nav = state(NavTab.Updates)
        nav.reselectActions = mapOf(
            NavTab.Updates to { nav.push(DetailRoute(NavTab.Updates, 7)) },
        )
        nav.select(NavTab.Updates)

        // 动作 push 进共享栈（不再先 pop），tab 栈只剩根。
        assertEquals(
            listOf(DetailRoute(NavTab.Updates, 7)),
            nav.sharedStack.toList(),
        )
        assertEquals(listOf(TabRootRoute(NavTab.Updates)), nav.stackOf(NavTab.Updates).toList())
    }

    @Test
    fun sharedPagesSurviveTabSwitches() {
        val nav = state()

        nav.push(DetailRoute(NavTab.Library, 0))
        nav.select(NavTab.History)
        nav.push(DetailRoute(NavTab.History, 1))
        nav.select(NavTab.Library)

        // G2：二级页在跨 tab 共享栈，切换 tab 不丢失、不错位。
        assertEquals(
            listOf(DetailRoute(NavTab.Library, 0), DetailRoute(NavTab.History, 1)),
            nav.sharedStack.toList(),
        )
        assertEquals(listOf(TabRootRoute(NavTab.Library)), nav.stackOf(NavTab.Library).toList())
        assertEquals(listOf(TabRootRoute(NavTab.History)), nav.stackOf(NavTab.History).toList())
        assertEquals(1, nav.stackOf(NavTab.Updates).size)
    }

    @Test
    fun readerPushOpensIndependentLayerWithClearTop() {
        val nav = state()

        nav.push(SourceDetailRoute("src", "w1"))
        nav.push(readerRoute("w1"))

        assertEquals(listOf(readerRoute("w1")), nav.readerStack.toList())
        assertTrue(nav.isReaderOpen)
        assertEquals(readerRoute("w1"), nav.topKey)

        // 同层再开阅读器替换当前页（CLEAR_TOP），不叠加。
        nav.push(readerRoute("w2"))
        assertEquals(listOf(readerRoute("w2")), nav.readerStack.toList())
        // 下层共享页不受影响。
        assertEquals(listOf(SourceDetailRoute("src", "w1")), nav.sharedStack.toList())
    }

    @Test
    fun backPopsSharedPageFirst() {
        val nav = state(NavTab.History)
        nav.push(DetailRoute(NavTab.History, 0))

        assertEquals(BackOutcome.PoppedChild, nav.handleBack())
        assertEquals(NavTab.History, nav.selectedTab)
        assertTrue(nav.sharedStack.isEmpty())
        assertEquals(listOf(TabRootRoute(NavTab.History)), nav.stackOf(NavTab.History).toList())
    }

    @Test
    fun backFromReaderClosesReaderBeforeShared() {
        val nav = state()
        nav.push(SourceDetailRoute("src", "w1"))
        nav.push(readerRoute())

        assertEquals(BackOutcome.PoppedChild, nav.handleBack())
        assertFalse(nav.isReaderOpen)
        assertEquals(SourceDetailRoute("src", "w1"), nav.topKey)

        assertEquals(BackOutcome.PoppedChild, nav.handleBack())
        assertEquals(TabRootRoute(NavTab.Library), nav.topKey)
    }

    @Test
    fun backOnNonLibraryRootReturnsToLibrary() {
        val nav = state(NavTab.More)

        assertTrue(nav.returnsToLibraryOnBack())
        assertEquals(BackOutcome.ReturnedToLibrary, nav.handleBack())
        assertEquals(NavTab.Library, nav.selectedTab)
    }

    @Test
    fun backOnSharedPageDoesNotReturnToLibrary() {
        val nav = state(NavTab.More)
        nav.push(SettingsRoute)

        assertFalse(nav.returnsToLibraryOnBack())
        assertTrue(nav.canHandleBack())
        assertEquals(BackOutcome.PoppedChild, nav.handleBack())
        // 共享页弹完才回书架。
        assertEquals(BackOutcome.ReturnedToLibrary, nav.handleBack())
        assertEquals(NavTab.Library, nav.selectedTab)
    }

    @Test
    fun backOnLibraryRootIsUnhandled() {
        val nav = state()

        assertFalse(nav.canHandleBack())
        assertEquals(BackOutcome.Unhandled, nav.handleBack())
        assertEquals(NavTab.Library, nav.selectedTab)
    }

    @Test
    fun backOnSharedChildDoesNotChangeSelectedTab() {
        val nav = state(NavTab.Updates)
        nav.push(DetailRoute(NavTab.Updates, 0))

        assertTrue(nav.canHandleBack())
        assertEquals(BackOutcome.PoppedChild, nav.handleBack())
        assertEquals(NavTab.Updates, nav.selectedTab)
    }

    @Test
    fun topKeyPrefersReaderOverSharedOverTab() {
        val nav = state()

        assertEquals(TabRootRoute(NavTab.Library), nav.topKey)
        nav.push(GlobalSearchRoute)
        assertEquals(GlobalSearchRoute, nav.topKey)
        nav.push(readerRoute())
        assertEquals(readerRoute(), nav.topKey)
    }
}
