package app.koyura.app

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
import androidx.compose.runtime.CompositionLocalProvider
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
import app.koyura.app.browse.BrowseScreen
import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.LocalBrowseService
import app.koyura.app.browse.BrowseSession
import app.koyura.app.browse.ExtensionDetailsScreen
import app.koyura.app.browse.ExtensionStoresScreen
import app.koyura.app.browse.LocalBrowseSession
import app.koyura.app.browse.SourceDetailScreen
import app.koyura.app.browse.SourceSearchScreen
import app.koyura.app.history.HistoryScreen
import app.koyura.app.history.HistoryService
import app.koyura.app.history.HistorySession
import app.koyura.app.history.LocalHistoryService
import app.koyura.app.history.rememberHistorySession
import app.koyura.domain.reader.ReaderService
import app.koyura.features.reader.comic.ComicReaderScreen
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.reader.comic.rememberReaderSession
import app.koyura.app.browse.rememberBrowseSession
import app.koyura.app.navigation.AppNavigationState
import app.koyura.app.navigation.DetailRoute
import app.koyura.app.navigation.ExtensionDetailsRoute
import app.koyura.app.navigation.ComicReaderRoute
import app.koyura.app.navigation.SourceDetailRoute
import app.koyura.app.navigation.SourceSearchRoute
import app.koyura.app.navigation.ExtensionStoresRoute
import app.koyura.app.navigation.TabRootRoute
import app.koyura.app.ui.DetailScreen
import app.koyura.app.ui.TabRootScreen
import app.koyura.designsystem.navigation.NavTab
import app.koyura.designsystem.navigation.ReaderNavigationBar
import app.koyura.designsystem.navigation.ReaderNavigationRail

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
    browseService: BrowseService? = LocalBrowseService.current,
    browseSession: BrowseSession? = null,
    readerService: ReaderService? = LocalReaderService.current,
    readerSession: ReaderSession? = null,
    historyService: HistoryService? = LocalHistoryService.current,
    historySession: HistorySession? = null,
) {
    BackToLibraryHandler(navigation)

    val activeBrowseSession = browseSession ?: rememberBrowseSession(browseService)
    val activeReaderSession = readerSession ?: rememberReaderSession(readerService)
    val activeHistorySession = historySession ?: rememberHistorySession(historyService, browseService)

    // The bar and rail branches have different layout shapes, so recomposing one into the other
    // would dispose the content subtree and rebuild it from saved state. Moving the subtree with
    // movable content keeps its remember state, including scroll position, across the switch.
    val saveableStateHolder = rememberSaveableStateHolder()
    val appContent = remember {
        movableContentOf<AppNavigationState, SaveableStateHolder, BrowseSession> { state, holder, browse ->
            CompositionLocalProvider(LocalReaderService provides readerService) {
                AppNavigationContent(state, holder, browse, activeReaderSession, activeHistorySession)
            }
        }
    }

    // The reader is fullscreen: it draws behind the system bars with its own controls and
    // hides the tab bar/rail while a chapter is open.
    val isReaderOpen = navigation.currentStack.lastOrNull() is ComicReaderRoute
    if (isReaderOpen) {
        Box(modifier = Modifier.fillMaxSize()) {
            appContent(navigation, saveableStateHolder, activeBrowseSession)
        }
    } else if (useRail) {
        val contentInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
        )
        Row(modifier = Modifier.fillMaxSize()) {
            ReaderNavigationRail(
                selectedTab = navigation.selectedTab,
                onTabClick = navigation::select,
                modifier = Modifier.fillMaxHeight().testTag(NavigationRailTestTag),
                browseBadgeCount = activeBrowseSession.state.updateCount,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(contentInsets),
            ) {
                appContent(navigation, saveableStateHolder, activeBrowseSession)
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
                appContent(navigation, saveableStateHolder, activeBrowseSession)
            }
            ReaderNavigationBar(
                selectedTab = navigation.selectedTab,
                onTabClick = navigation::select,
                modifier = Modifier.testTag(NavigationBarTestTag),
                browseBadgeCount = activeBrowseSession.state.updateCount,
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
    browseSession: BrowseSession,
    readerSession: ReaderSession,
    historySession: app.koyura.app.history.HistorySession,
) {
    val entryProvider = remember(navigation, browseSession, readerSession, historySession) {
        entryProvider<NavKey> {
            entry<TabRootRoute> { route ->
                if (route.tab == NavTab.Browse) {
                    CompositionLocalProvider(LocalBrowseSession provides browseSession) {
                        BrowseScreen(
                            session = browseSession,
                            onOpenExtension = { key -> navigation.push(ExtensionDetailsRoute(key)) },
                            onManageStores = { storeId -> navigation.push(ExtensionStoresRoute(storeId)) },
                            onOpenSource = { sourceKey, sourceName ->
                                navigation.push(SourceSearchRoute(sourceKey))
                            },
                        )
                    }
                } else if (route.tab == NavTab.History) {
                    HistoryScreen(
                        session = historySession,
                        onOpenWork = { sourceKey, workId, title ->
                            navigation.push(SourceDetailRoute(sourceKey, workId, title))
                        },
                        onContinue = { entry, chapterIds, chapterTitles ->
                            navigation.push(
                                ComicReaderRoute(
                                    sourceKey = entry.sourceKey,
                                    workId = entry.workId,
                                    chapterId = entry.chapterId,
                                    title = entry.displayTitle,
                                    chapterIds = chapterIds,
                                    chapterTitles = chapterTitles,
                                ),
                            )
                        },
                    )
                } else {
                    TabRootScreen(
                        tab = route.tab,
                        onOpenDetail = {
                            navigation.push(DetailRoute(tab = route.tab, id = navigation.currentStack.size))
                        },
                    )
                }
            }
            entry<DetailRoute> { route ->
                DetailScreen(origin = route.tab, id = route.id)
            }
            entry<ExtensionDetailsRoute> { route ->
                CompositionLocalProvider(LocalBrowseSession provides browseSession) {
                    ExtensionDetailsScreen(
                        session = browseSession,
                        extensionKey = route.extensionKey,
                        onBack = { navigation.popCurrentChild() },
                    )
                }
            }
            entry<SourceSearchRoute> { route ->
                CompositionLocalProvider(LocalBrowseSession provides browseSession) {
                    val sourceName = browseSession.state.sources
                        .firstOrNull { it.id == route.sourceKey }?.name
                        ?: route.sourceKey
                    SourceSearchScreen(
                        session = browseSession,
                        sourceKey = route.sourceKey,
                        sourceName = sourceName,
                        onOpenWork = { workId, title ->
                            navigation.push(SourceDetailRoute(route.sourceKey, workId, title))
                        },
                    )
                }
            }
            entry<SourceDetailRoute> { route ->
                CompositionLocalProvider(LocalBrowseSession provides browseSession) {
                    SourceDetailScreen(
                        session = browseSession,
                        sourceKey = route.sourceKey,
                        workId = route.workId,
                        fallbackTitle = route.title,
                        onBack = { navigation.popCurrentChild() },
                        onOpenChapter = { chapterId, chapterIds, chapterTitles ->
                            navigation.push(
                                ComicReaderRoute(
                                    sourceKey = route.sourceKey,
                                    workId = route.workId,
                                    chapterId = chapterId,
                                    title = route.title,
                                    chapterIds = chapterIds,
                                    chapterTitles = chapterTitles,
                                ),
                            )
                        },
                    )
                }
            }
            entry<ComicReaderRoute> { route ->
                CompositionLocalProvider(LocalBrowseSession provides browseSession) {
                    ComicReaderScreen(
                        session = readerSession,
                        sourceKey = route.sourceKey,
                        workId = route.workId,
                        chapterId = route.chapterId,
                        title = route.title,
                        chapterIds = route.chapterIds,
                        chapterTitles = route.chapterTitles,
                        onBack = { navigation.popCurrentChild() },
                    )
                }
            }
            entry<ExtensionStoresRoute> { route ->
                CompositionLocalProvider(LocalBrowseSession provides browseSession) {
                    ExtensionStoresScreen(
                        session = browseSession,
                        initialStoreId = route.focusStoreId,
                        onBack = { navigation.popCurrentChild() },
                    )
                }
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
