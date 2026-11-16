package app.koyura.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import app.koyura.designsystem.navigation.NavTab

/** Result of an application-level back request. */
enum class BackOutcome {
    /** A child page was removed from the current tab. */
    PoppedChild,

    /** The current tab was already at its root, so the shell switched to the Library tab. */
    ReturnedToLibrary,

    /** The Library tab is at its root; the platform owns the gesture. */
    Unhandled,
}

/**
 * Owns the selected tab and one Navigation 3 back stack per tab.
 *
 * The shell only renders this state and forwards taps and back requests. Keeping the state free of
 * navigation UI concerns makes the rules below directly testable.
 */
class AppNavigationState internal constructor(
    private val stacks: Map<NavTab, NavBackStack<NavKey>>,
    private val selectedTabState: MutableState<NavTab>,
    private val onTabReselect: (NavTab) -> Unit,
) {
    val selectedTab: NavTab
        get() = selectedTabState.value

    /** Back stack of the visible tab. */
    val currentStack: NavBackStack<NavKey>
        get() = stacks.getValue(selectedTab)

    fun stackOf(tab: NavTab): NavBackStack<NavKey> = stacks.getValue(tab)

    /**
     * Selects [tab]. Tapping the already selected tab emits a single reselect event and leaves the
     * stack untouched, so callers can distinguish switching from reselect without duplicate routes.
     */
    fun select(tab: NavTab) {
        if (tab == selectedTab) {
            onTabReselect(tab)
            return
        }
        selectedTabState.value = tab
    }

    fun push(route: AppRoute) {
        currentStack.add(route)
    }

    /** Pops one child page of the visible tab. Returns false when the tab is already at its root. */
    fun popCurrentChild(): Boolean {
        if (currentStack.size <= 1) return false
        currentStack.removeAt(currentStack.lastIndex)
        return true
    }

    /** True while the shell itself can handle back without leaving the app. */
    fun canHandleBack(): Boolean = currentStack.size > 1 || selectedTab != NavTab.Library

    /** True when back would leave the current tab for the Library tab. */
    fun returnsToLibraryOnBack(): Boolean = currentStack.size <= 1 && selectedTab != NavTab.Library

    /**
     * Applies the back rules: child pages pop first, other tabs return to Library, and the Library
     * root is left to the platform.
     */
    fun handleBack(): BackOutcome {
        if (popCurrentChild()) return BackOutcome.PoppedChild
        if (selectedTab != NavTab.Library) {
            selectedTabState.value = NavTab.Library
            return BackOutcome.ReturnedToLibrary
        }
        return BackOutcome.Unhandled
    }
}

/** Builds a state with fresh per-tab stacks; used by previews and tests. */
fun createAppNavigationState(
    initialTab: NavTab = NavTab.Library,
    onTabReselect: (NavTab) -> Unit = {},
): AppNavigationState = AppNavigationState(
    stacks = NavTab.entries.associateWith { tab -> NavBackStack<NavKey>(TabRootRoute(tab)) },
    selectedTabState = mutableStateOf(initialTab),
    onTabReselect = onTabReselect,
)

/**
 * Remembers the shell state across recomposition and configuration changes.
 *
 * Every stack is restored through [rememberNavBackStack] using the serializable [AppRoute] keys, and
 * the selected tab is persisted separately.
 */
@Composable
fun rememberAppNavigationState(
    onTabReselect: (NavTab) -> Unit = {},
): AppNavigationState {
    val reselect by rememberUpdatedState(onTabReselect)
    val selectedTab = rememberSaveable(stateSaver = NavTabSaver) { mutableStateOf(NavTab.Library) }
    val configuration = remember { ReaderSavedStateConfiguration }

    val libraryStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.Library))
    val updatesStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.Updates))
    val historyStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.History))
    val browseStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.Browse))
    val moreStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.More))

    return remember(libraryStack, updatesStack, historyStack, browseStack, moreStack, selectedTab) {
        AppNavigationState(
            stacks = mapOf(
                NavTab.Library to libraryStack,
                NavTab.Updates to updatesStack,
                NavTab.History to historyStack,
                NavTab.Browse to browseStack,
                NavTab.More to moreStack,
            ),
            selectedTabState = selectedTab,
            onTabReselect = { tab -> reselect(tab) },
        )
    }
}

private val NavTabSaver: Saver<NavTab, String> = Saver(
    save = { it.name },
    restore = { name -> NavTab.entries.firstOrNull { it.name == name } ?: NavTab.Library },
)
