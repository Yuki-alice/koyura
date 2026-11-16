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

    @Test
    fun startsOnLibraryWithRootOfEveryTab() {
        val nav = state()

        assertEquals(NavTab.Library, nav.selectedTab)
        NavTab.entries.forEach { tab ->
            val stack = nav.stackOf(tab)
            assertEquals(1, stack.size)
            assertEquals(TabRootRoute(tab), stack.first())
        }
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
    fun eachTabKeepsItsOwnStackAcrossSwitches() {
        val nav = state()

        nav.push(DetailRoute(NavTab.Library, 0))
        nav.select(NavTab.Browse)
        nav.push(DetailRoute(NavTab.Browse, 1))
        nav.select(NavTab.Library)

        assertEquals(
            listOf(TabRootRoute(NavTab.Library), DetailRoute(NavTab.Library, 0)),
            nav.stackOf(NavTab.Library).toList(),
        )
        assertEquals(
            listOf(TabRootRoute(NavTab.Browse), DetailRoute(NavTab.Browse, 1)),
            nav.stackOf(NavTab.Browse).toList(),
        )
        assertEquals(1, nav.stackOf(NavTab.Updates).size)
    }

    @Test
    fun backPopsCurrentTabChildFirst() {
        val nav = state(NavTab.History)
        nav.push(DetailRoute(NavTab.History, 0))

        assertEquals(BackOutcome.PoppedChild, nav.handleBack())
        assertEquals(NavTab.History, nav.selectedTab)
        assertEquals(listOf(TabRootRoute(NavTab.History)), nav.stackOf(NavTab.History).toList())
    }

    @Test
    fun backOnNonLibraryRootReturnsToLibrary() {
        val nav = state(NavTab.More)

        assertTrue(nav.returnsToLibraryOnBack())
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
    fun backOnChildOfOtherTabDoesNotChangeSelectedTab() {
        val nav = state(NavTab.Updates)
        nav.push(DetailRoute(NavTab.Updates, 0))

        assertTrue(nav.canHandleBack())
        assertEquals(BackOutcome.PoppedChild, nav.handleBack())
        assertEquals(NavTab.Updates, nav.selectedTab)
    }
}
