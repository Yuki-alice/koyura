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
 *
 * Reselect (tapping the active tab) runs the per-tab [reselectActions] entry and never touches
 * the stacks: Library opens its filter sheet (page-local state), Updates pushes the download
 * queue, History resumes the latest chapter, Browse pushes global search, More pushes settings.
 * This mirrors Komikku `HomeScreen` reselect (`LibraryTab`/`UpdatesTab`/`HistoryTab`/
 * `BrowseTab`/`MoreTab` `onReselect`); the previous "pop to root as scroll-to-top" behavior
 * is intentionally removed.
 *
 * Three-layer stacks (Komikku global-Navigator parity):
 * - 5 tab root stacks keep each tab's root (and any tab-local placeholder);
 * - [sharedStack] holds every second-level page (details, source lists, global search,
 *   settings, downloads, categories, statistics, utilities, onboarding) and is shared
 *   across tabs: opening a detail from Library keeps it visible after switching tabs;
 * - [readerStack] is the reader's independent fullscreen layer with CLEAR_TOP semantics
 *   (mirrors `ReaderActivity.FLAG_ACTIVITY_CLEAR_TOP`).
 */
class AppNavigationState internal constructor(
    private val stacks: Map<NavTab, NavBackStack<NavKey>>,
    private val selectedTabState: MutableState<NavTab>,
    var reselectActions: Map<NavTab, () -> Unit> = emptyMap(),
    val sharedStack: NavBackStack<NavKey> = NavBackStack(),
    val readerStack: NavBackStack<NavKey> = NavBackStack(),
) {
    val selectedTab: NavTab
        get() = selectedTabState.value

    /** Back stack of the visible tab (roots only once二级页 move to [sharedStack]). */
    val currentStack: NavBackStack<NavKey>
        get() = stacks.getValue(selectedTab)

    fun stackOf(tab: NavTab): NavBackStack<NavKey> = stacks.getValue(tab)

    /** Topmost visible key: reader fullscreen first, then shared pages, then the tab root. */
    val topKey: NavKey?
        get() = readerStack.lastOrNull() ?: sharedStack.lastOrNull() ?: currentStack.lastOrNull()

    val isReaderOpen: Boolean
        get() = readerStack.isNotEmpty()

    val isSharedOpen: Boolean
        get() = sharedStack.isNotEmpty()

    /**
     * Selects [tab]. Tapping the already selected destination runs its reselect action
     * without touching any stack, so callers can distinguish switching from reselect.
     */
    fun select(tab: NavTab) {
        if (tab == selectedTab) {
            reselectActions[tab]?.invoke()
            return
        }
        selectedTabState.value = tab
    }

    /** Pops [tab]'s stack back to its root route. No-op when already at the root. */
    fun popToRoot(tab: NavTab = selectedTab) {
        val stack = stacks.getValue(tab)
        while (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }

    /**
     * Pushes a destination: the reader opens its independent fullscreen layer with
     * CLEAR_TOP semantics; every other page goes to the cross-tab shared stack.
     * Tab stacks keep only their roots.
     */
    fun push(route: AppRoute) {
        if (route is ComicReaderRoute) openReader(route) else sharedStack.add(route)
    }

    /**
     * Opens the reader, replacing any current reader page (CLEAR_TOP).
     * Returning exits to the page underneath (shared or tab root).
     */
    fun openReader(route: ComicReaderRoute) {
        readerStack.clear()
        readerStack.add(route)
    }

    /** Closes the reader layer. Returns false when the reader is not open. */
    fun closeReader(): Boolean {
        if (readerStack.isEmpty()) return false
        readerStack.clear()
        return true
    }

    /** Pops one shared page. Returns false when the shared stack is empty. */
    fun popShared(): Boolean {
        if (sharedStack.isEmpty()) return false
        sharedStack.removeAt(sharedStack.lastIndex)
        return true
    }

    /**
     * Pops one visible child page: reader first, then shared, then the current tab.
     * Returns false when the visible tab is already at its root.
     */
    fun popCurrentChild(): Boolean {
        if (closeReader()) return true
        if (popShared()) return true
        if (currentStack.size <= 1) return false
        currentStack.removeAt(currentStack.lastIndex)
        return true
    }

    /** True while the shell itself can handle back without leaving the app. */
    fun canHandleBack(): Boolean =
        readerStack.isNotEmpty() || sharedStack.isNotEmpty() ||
            currentStack.size > 1 || selectedTab != NavTab.Library

    /** True when back would leave the current tab for the Library tab. */
    fun returnsToLibraryOnBack(): Boolean =
        readerStack.isEmpty() && sharedStack.isEmpty() &&
            currentStack.size <= 1 && selectedTab != NavTab.Library

    /**
     * Applies the back rules: reader first, then shared pages, then tab children,
     * then other tabs return to Library, and the Library root is left to the platform.
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
    reselectActions: Map<NavTab, () -> Unit> = NavTab.entries.associateWith { tab -> { onTabReselect(tab) } },
): AppNavigationState = AppNavigationState(
    stacks = NavTab.entries.associateWith { tab -> NavBackStack<NavKey>(TabRootRoute(tab)) },
    selectedTabState = mutableStateOf(initialTab),
    reselectActions = reselectActions,
    sharedStack = NavBackStack(),
    readerStack = NavBackStack(),
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
    reselectActions: Map<NavTab, () -> Unit>? = null,
): AppNavigationState {
    val reselect by rememberUpdatedState(onTabReselect)
    val selectedTab = rememberSaveable(stateSaver = NavTabSaver) { mutableStateOf(NavTab.Library) }
    val configuration = remember { ReaderSavedStateConfiguration }

    val libraryStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.Library))
    val updatesStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.Updates))
    val historyStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.History))
    val browseStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.Browse))
    val moreStack = rememberNavBackStack(configuration, TabRootRoute(NavTab.More))
    val sharedStack = rememberNavBackStack(configuration)
    val readerStack = rememberNavBackStack(configuration)

    return remember(
        libraryStack,
        updatesStack,
        historyStack,
        browseStack,
        moreStack,
        sharedStack,
        readerStack,
        selectedTab,
    ) {
        AppNavigationState(
            stacks = mapOf(
                NavTab.Library to libraryStack,
                NavTab.Updates to updatesStack,
                NavTab.History to historyStack,
                NavTab.Browse to browseStack,
                NavTab.More to moreStack,
            ),
            selectedTabState = selectedTab,
            reselectActions = reselectActions ?: NavTab.entries.associateWith { tab -> { reselect(tab) } },
            sharedStack = sharedStack,
            readerStack = readerStack,
        )
    }
}

private val NavTabSaver: Saver<NavTab, String> = Saver(
    save = { it.name },
    restore = { name -> NavTab.entries.firstOrNull { it.name == name } ?: NavTab.Library },
)
