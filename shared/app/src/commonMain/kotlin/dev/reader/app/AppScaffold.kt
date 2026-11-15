package dev.reader.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.reader.app.navigation.AppNavigationState
import dev.reader.app.navigation.DetailRoute
import dev.reader.app.navigation.TabRootRoute
import dev.reader.app.ui.DetailScreen
import dev.reader.app.ui.TabRootScreen
import dev.reader.designsystem.navigation.ReaderNavigationBar
import dev.reader.designsystem.navigation.ReaderNavigationRail

internal const val NavigationBarTestTag = "app-navigation-bar"
internal const val NavigationRailTestTag = "app-navigation-rail"

/**
 * Lays out the selected tab's content next to the navigation rail on wide windows, or above the
 * bottom bar on narrow ones.
 *
 * Insets are split by owner: the bar consumes horizontal and bottom insets, the rail consumes
 * vertical and start insets, and the content area pads only the edges no navigation surface covers.
 * That keeps content clear of system bars without stacking the same inset twice.
 */
@Composable
internal fun AppScaffold(
    navigation: AppNavigationState,
    useRail: Boolean,
) {
    BackToLibraryHandler(navigation)

    // The bar and rail branches have different layout shapes, so recomposing one into the other
    // would dispose the content subtree and rebuild it from saved state. Moving the subtree with
    // movable content keeps its remember state, including scroll position, across the switch.
    val saveableStateHolder = rememberSaveableStateHolder()
    val appContent = remember {
        movableContentOf<AppNavigationState, SaveableStateHolder> { state, holder ->
            AppNavigationContent(state, holder)
        }
    }

    if (useRail) {
        val contentInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
        )
        Row(modifier = Modifier.fillMaxSize()) {
            ReaderNavigationRail(
                selectedTab = navigation.selectedTab,
                onTabClick = navigation::select,
                modifier = Modifier.fillMaxHeight().testTag(NavigationRailTestTag),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(contentInsets),
            ) {
                appContent(navigation, saveableStateHolder)
            }
        }
    } else {
        val contentInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(contentInsets),
            ) {
                appContent(navigation, saveableStateHolder)
            }
            ReaderNavigationBar(
                selectedTab = navigation.selectedTab,
                onTabClick = navigation::select,
                modifier = Modifier.testTag(NavigationBarTestTag),
            )
        }
    }
}

/**
 * Handles back only when the visible tab sits at its root and is not Library.
 *
 * Child pages are left to [NavDisplay], whose handler already supports popping and predictive back.
 * Keeping the two enabled states disjoint avoids competing handlers for one gesture.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BackToLibraryHandler(navigation: AppNavigationState) {
    BackHandler(enabled = navigation.returnsToLibraryOnBack()) {
        navigation.handleBack()
    }
}

/**
 * Renders the selected tab's Navigation 3 stack.
 *
 * Each tab owns its stack, so switching tabs only swaps which stack NavDisplay reads. The shared
 * [saveableStateHolder] outlives both layouts and keys by tab, keeping screen-level state across tab
 * switches and across bottom bar / navigation rail changes.
 */
@Composable
private fun AppNavigationContent(
    navigation: AppNavigationState,
    saveableStateHolder: SaveableStateHolder,
) {
    val entryProvider = remember(navigation) {
        entryProvider<NavKey> {
            entry<TabRootRoute> { route ->
                TabRootScreen(
                    tab = route.tab,
                    onOpenDetail = {
                        navigation.push(DetailRoute(tab = route.tab, id = navigation.currentStack.size))
                    },
                )
            }
            entry<DetailRoute> { route ->
                DetailScreen(origin = route.tab, id = route.id)
            }
        }
    }

    saveableStateHolder.SaveableStateProvider(navigation.selectedTab.name) {
        NavDisplay(
            backStack = navigation.currentStack,
            onBack = { navigation.handleBack() },
            modifier = Modifier.fillMaxSize(),
            entryProvider = entryProvider,
        )
    }
}
