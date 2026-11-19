package app.koyura.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import app.koyura.app.browse.ProvideCoverPipeline
import app.koyura.features.downloads.DownloadsScreen
import app.koyura.features.downloads.rememberDownloadsSession
import app.koyura.categories.CategoriesScreen
import app.koyura.features.more.MoreScreen
import app.koyura.features.more.rememberMoreSession
import app.koyura.features.statistics.StatisticsScreen
import app.koyura.features.statistics.rememberStatisticsSession
import app.koyura.features.updates.UpdatesScreen
import app.koyura.features.updates.rememberUpdatesSession
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
import app.koyura.app.library.LibraryTabContent
import app.koyura.domain.reader.ReaderService
import app.koyura.features.reader.comic.ComicReaderScreen
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.reader.comic.rememberReaderSession
import app.koyura.app.browse.rememberBrowseSession
import app.koyura.app.navigation.AppNavigationState
import app.koyura.app.navigation.CategoriesRoute
import app.koyura.app.navigation.DetailRoute
import app.koyura.app.navigation.DownloadQueueRoute
import app.koyura.app.navigation.ExtensionDetailsRoute
import app.koyura.app.navigation.ComicReaderRoute
import app.koyura.app.navigation.SourceDetailRoute
import app.koyura.app.navigation.StatisticsRoute
import app.koyura.app.navigation.SourceSearchRoute
import app.koyura.app.navigation.ExtensionStoresRoute
import app.koyura.app.navigation.TabRootRoute
import app.koyura.app.ui.DetailScreen
import app.koyura.app.ui.TabRootScreen
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.navigation.NavTab
import app.koyura.designsystem.navigation.ReaderNavigationBar
import app.koyura.designsystem.navigation.ReaderNavigationRail
import app.koyura.features.library.LibrarySession
import app.koyura.features.library.rememberLibrarySession

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
    val activeBrowseSession = browseSession ?: rememberBrowseSession(browseService)
    val activeReaderSession = readerSession ?: rememberReaderSession(readerService)
    val activeHistorySession = historySession ?: rememberHistorySession(historyService, browseService)
    val activeUpdatesSession = rememberUpdatesSession()
    val statisticsSession = rememberStatisticsSession()
    val activeMoreSession = rememberMoreSession()
    val activeDownloadsSession = rememberDownloadsSession()
    val librarySession = rememberLibrarySession()

    BackToLibraryHandler(navigation, librarySession)

    // The bar and rail branches have different layout shapes, so recomposing one into the other
    // would dispose the content subtree and rebuild it from saved state. Moving the subtree with
    // movable content keeps its remember state, including scroll position, across the switch.
    val saveableStateHolder = rememberSaveableStateHolder()
    val appContent = remember {
        movableContentOf<AppNavigationState, SaveableStateHolder, BrowseSession> { state, holder, browse ->
            CompositionLocalProvider(LocalReaderService provides readerService) {
                AppNavigationContent(
                    state,
                    holder,
                    browse,
                    activeReaderSession,
                    activeHistorySession,
                    librarySession,
                    activeUpdatesSession,
                    activeMoreSession,
                    activeDownloadsSession,
                    statisticsSession,
                )
            }
        }
    }

    // The reader is fullscreen: it draws behind the system bars with its own controls and
    // hides the tab bar/rail while a chapter is open. Library multi-select hides them the same
    // way (Komikku parity: selection mode owns the chrome).
    val isReaderOpen = navigation.currentStack.lastOrNull() is ComicReaderRoute
    val hideChrome = navigation.selectedTab == NavTab.Library && librarySession.selection.isNotEmpty()
    if (isReaderOpen || hideChrome) {
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
                updatesBadgeCount = activeUpdatesSession.unreadCount,
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
                updatesBadgeCount = activeUpdatesSession.unreadCount,
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
private fun BackToLibraryHandler(
    navigation: AppNavigationState,
    librarySession: LibrarySession?,
) {
    BackHandler(enabled = navigation.returnsToLibraryOnBack()) {
        navigation.handleBack()
    }
    // 书架根上返回键先清选择态/搜索，不直接交平台；与“回书架”分支互斥。
    val consumeLibrary = navigation.selectedTab == NavTab.Library &&
        navigation.currentStack.size <= 1 && librarySession?.wantsBack == true
    BackHandler(enabled = consumeLibrary) {
        librarySession?.consumeBack()
    }
}

/**
 * Renders the selected tab's Navigation 3 stack.
 *
 * Each tab owns its stack, so switching tabs only swaps which stack NavDisplay reads. The shared
 * [saveableStateHolder] outlives both layouts and keys by tab, keeping screen-level state across tab
 * switches and across bottom bar / navigation rail changes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppNavigationContent(
    navigation: AppNavigationState,
    saveableStateHolder: SaveableStateHolder,
    browseSession: BrowseSession,
    readerSession: ReaderSession,
    historySession: app.koyura.app.history.HistorySession,
    librarySession: LibrarySession,
    updatesSession: app.koyura.features.updates.UpdatesSession,
    moreSession: app.koyura.features.more.MoreSession,
    downloadsSession: app.koyura.features.downloads.DownloadsSession,
    statisticsSession: app.koyura.features.statistics.StatisticsSession,
) {
    val entryProvider = remember(
        navigation,
        browseSession,
        readerSession,
        historySession,
        librarySession,
        updatesSession,
        moreSession,
        downloadsSession,
        statisticsSession,
    ) {
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
                } else if (route.tab == NavTab.Library) {
                    LibraryTabContent(
                        navigation = navigation,
                        session = librarySession,
                        browseSession = browseSession,
                    )
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
                } else if (route.tab == NavTab.Updates) {
                    ProvideCoverPipeline(service = LocalBrowseService.current) {
                        UpdatesScreen(
                            session = updatesSession,
                            onOpenWork = { sourceKey, workId, title ->
                                navigation.push(SourceDetailRoute(sourceKey, workId, title))
                            },
                        )
                    }
                } else if (route.tab == NavTab.More) {
                    MoreScreen(
                        session = moreSession,
                        onOpenDownloadQueue = {
                            navigation.push(DownloadQueueRoute)
                        },
                        onOpenCategories = {
                            navigation.push(CategoriesRoute)
                        },
                        onOpenStatistics = {
                            navigation.push(StatisticsRoute)
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
            entry<DownloadQueueRoute> {
                DownloadsScreen(
                    session = downloadsSession,
                    onBack = { navigation.popCurrentChild() },
                )
            }
            entry<CategoriesRoute> {
                CategoriesScreen(onBack = { navigation.popCurrentChild() })
            }
            entry<StatisticsRoute> {
                StatisticsScreen(
                    session = statisticsSession,
                    onBack = { navigation.popCurrentChild() },
                )
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
                        preferences = LocalReaderPreferences.current,
                        installBackHandler = { BackHandler(onBack = it) },
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

    saveableStateHolder.SaveableStateProvider(navigation.selectedTab.name) {        AnimatedContent(
            targetState = navigation.selectedTab,
            transitionSpec = {
                // Komikku fadeThrough (HomeScreen.TabFadeDuration): the outgoing tab fades out
                // first, the incoming tab fades in after — never a crossfade. The delayed fade-in
                // keeps the two 200 ms halves sequential while reusing the single token.
                fadeIn(
                    animationSpec = tween(
                        durationMillis = DesignTokens.Motion.TabFadeThroughMs,
                        delayMillis = DesignTokens.Motion.TabFadeThroughMs,
                    ),
                ) togetherWith fadeOut(
                    animationSpec = tween(durationMillis = DesignTokens.Motion.TabFadeThroughMs),
                )
            },
            label = "tabSwitch",
        ) { tab ->
            NavDisplay(
                backStack = navigation.stackOf(tab),
                onBack = { navigation.handleBack() },
                modifier = Modifier.fillMaxSize(),
                entryProvider = entryProvider,
            )
        }
    }
}
