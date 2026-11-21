package app.koyura.app

import app.koyura.app.resources.global_banner_downloaded_only
import app.koyura.app.resources.global_banner_incognito
import app.koyura.app.resources.utility_about
import app.koyura.app.resources.utility_batch_add
import app.koyura.app.resources.utility_data_storage
import app.koyura.app.resources.utility_help
import app.koyura.app.resources.utility_message
import app.koyura.app.resources.utility_title
import app.koyura.app.resources.utility_update_errors
import app.koyura.app.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import app.koyura.app.library.LocalProductionSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
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
import app.koyura.features.onboarding.OnboardingScreen
import app.koyura.features.more.MoreSession
import app.koyura.features.more.rememberMoreSession
import app.koyura.features.statistics.StatisticsScreen
import app.koyura.features.statistics.rememberStatisticsSession
import app.koyura.features.updates.UpdatesScreen
import app.koyura.features.updates.rememberUpdatesSession
import app.koyura.app.browse.ExtensionDetailsScreen
import app.koyura.app.browse.globalsearch.GlobalSearchScreen
import app.koyura.app.browse.globalsearch.GlobalSearchService
import app.koyura.app.browse.globalsearch.GlobalSearchSession
import app.koyura.app.browse.globalsearch.LocalSearchSourceFilter
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
import app.koyura.domain.reader.preferences.ReaderBackground
import app.koyura.features.reader.comic.ComicReaderScreen
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.reader.comic.rememberReaderSession
import app.koyura.app.browse.rememberBrowseSession
import app.koyura.app.navigation.AppNavigationState
import app.koyura.app.navigation.CategoriesRoute
import app.koyura.app.navigation.DetailRoute
import app.koyura.app.navigation.DownloadQueueRoute
import app.koyura.app.navigation.ExtensionDetailsRoute
import app.koyura.app.navigation.GlobalSearchRoute
import app.koyura.app.navigation.ComicReaderRoute
import app.koyura.app.navigation.SourceDetailRoute
import app.koyura.app.navigation.StatisticsRoute
import app.koyura.app.navigation.OnboardingRoute
import app.koyura.app.navigation.SettingsRoute
import app.koyura.app.navigation.SettingsSubpageRoute
import app.koyura.app.navigation.UtilityPageRoute
import app.koyura.app.navigation.SourceSearchRoute
import app.koyura.app.navigation.ExtensionStoresRoute
import app.koyura.app.navigation.TabRootRoute
import app.koyura.app.ui.DetailScreen
import app.koyura.app.ui.TabRootScreen
import app.koyura.app.ui.UtilityPageScreen
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.BannerKind
import app.koyura.designsystem.components.ProvideUiContentColor
import app.koyura.designsystem.components.StatusBanner
import app.koyura.designsystem.components.UiTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.designsystem.navigation.ReaderNavigationBar
import app.koyura.designsystem.navigation.ReaderNavigationRail
import app.koyura.features.library.LibraryLoadState
import app.koyura.features.library.LibrarySession
import app.koyura.features.library.LibraryTriState
import app.koyura.features.library.rememberLibrarySession
import app.koyura.features.settings.SettingsScreen
import app.koyura.features.settings.SettingsSubpage
import app.koyura.features.settings.SettingsSubpageScreen
import app.koyura.features.settings.SettingsSession
import app.koyura.features.settings.rememberSettingsSession

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
    moreSession: MoreSession? = null,
    indexing: Boolean = false,
    /**
     * 首启自动进入引导（生产入口显式打开）。
     *
     * 默认关闭：测试 / 预览挂载外壳时不打扰路由；`ExtensionApp` 把它置 true。
     */
    enableOnboardingAutoEnter: Boolean = false,
) {
    val activeBrowseSession = browseSession ?: rememberBrowseSession(browseService)
    val activeReaderSession = readerSession ?: rememberReaderSession(readerService)
    val activeHistorySession = historySession ?: rememberHistorySession(historyService, browseService)
    val activeUpdatesSession = rememberUpdatesSession()
    val statisticsSession = rememberStatisticsSession()
    val activeMoreSession = moreSession ?: rememberMoreSession()
    val productionSettings = LocalProductionSettings.current
    val indexingState = rememberUpdatedState(indexing)
    val activeDownloadsSession = rememberDownloadsSession()
    val settingsSession = LocalAppSettingsSession.current ?: rememberSettingsSession()
    val librarySession = rememberLibrarySession()

    // G1：5 个 tab 的重复点击快捷入口（Komikku 各 Tab onReselect 对齐）。
    // Library 经 filterSignal 打开筛选 Sheet（页内状态，不进栈）；其余推已有路由键。
    // 注意内容区是 movableContentOf（参数不变时跳过重组），所以信号必须用稳定的
    // State 对象、内部订阅读值，不能传 Int 快照（传了也流不进去）。
    // 此处覆盖 navigation 自带的 reselectActions：外壳挂载即生产行为，
    // 单测如需自定义动作请直接操作 navigation.reselectActions。
    val libraryFilterSignal = remember { mutableIntStateOf(0) }
    val reselectScope = rememberCoroutineScope()
    LaunchedEffect(navigation, activeHistorySession) {
        navigation.reselectActions = mapOf(
            NavTab.Library to { libraryFilterSignal.intValue++ },
            NavTab.Updates to { navigation.push(DownloadQueueRoute) },
            NavTab.History to {
                reselectScope.launch {
                    val entry = activeHistorySession.visibleEntries().firstOrNull() ?: return@launch
                    try {
                        val (ids, titles) = activeHistorySession.resolveChapters(entry)
                        val target = entry.chapterId?.takeIf { it in ids } ?: ids.firstOrNull()
                        navigation.push(
                            ComicReaderRoute(
                                sourceKey = entry.sourceKey,
                                workId = entry.workId,
                                chapterId = target,
                                title = entry.displayTitle,
                                chapterIds = ids,
                                chapterTitles = titles,
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        activeHistorySession.reportError(
                            failure.message?.takeIf { it.isNotBlank() } ?: "Cannot continue reading",
                        )
                    }
                }
            },
            NavTab.Browse to { navigation.push(GlobalSearchRoute) },
            NavTab.More to { navigation.push(SettingsRoute) },
        )
    }

    // 更多开关的持久化与生效：开关本身只改页内态，这里落盘并同步到真实行为。
    LaunchedEffect(productionSettings) {
        val settings = productionSettings ?: return@LaunchedEffect
        try {
            if (settings.isIncognito()) activeMoreSession.updateIncognito(true)
            if (settings.isDownloadedOnly()) activeMoreSession.updateDownloadedOnly(true)
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(activeMoreSession.downloadedOnly, librarySession.loadState) {
        val settings = productionSettings
        try {
            settings?.setDownloadedOnly(activeMoreSession.downloadedOnly)
        } catch (_: Exception) {
        }
        // 只在书架已加载且开关与筛选不一致时回写：启动未加载前不碰已持久化的偏好，
        // 筛选弹层的手动修改也不被开关覆盖（开关是快捷入口，不是唯一真相源）。
        val target = if (activeMoreSession.downloadedOnly) {
            LibraryTriState.INCLUDE
        } else {
            LibraryTriState.DISABLED
        }
        if (librarySession.loadState is LibraryLoadState.Ready &&
            librarySession.snapshot.prefs.filters.downloaded != target
        ) {
            librarySession.setDownloadedOnlyFilter(activeMoreSession.downloadedOnly)
        }
    }
    LaunchedEffect(activeMoreSession.incognito) {
        try {
            productionSettings?.setIncognito(activeMoreSession.incognito)
        } catch (_: Exception) {
        }
    }
    // 首启自动进入引导：生产入口显式打开时，标记缺失推一次。
    LaunchedEffect(productionSettings, enableOnboardingAutoEnter) {
        val settings = productionSettings ?: return@LaunchedEffect
        if (!enableOnboardingAutoEnter) return@LaunchedEffect
        try {
            if (!settings.isOnboardingCompleted()) navigation.push(OnboardingRoute)
        } catch (_: Exception) {
        }
    }

    BackToLibraryHandler(navigation, librarySession, activeHistorySession)

    // The bar and rail branches have different layout shapes, so recomposing one into the other
    // would dispose the content subtree and rebuild it from saved state. Moving the subtree with
    // movable content keeps its remember state, including scroll position, across the switch.
    val saveableStateHolder = rememberSaveableStateHolder()
    val appContent = remember(activeMoreSession, settingsSession) {
        movableContentOf<AppNavigationState, SaveableStateHolder, BrowseSession> { state, holder, browse ->
            CompositionLocalProvider(LocalReaderService provides readerService) {
                ProvideUiContentColor(UiTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxSize()) {
                        if (!state.isReaderOpen) {
                            AppStatusBanners(
                                indexing = indexingState.value,
                                downloadedOnly = activeMoreSession.downloadedOnly,
                                incognito = activeMoreSession.incognito,
                            )
                        }
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
                            settingsSession,
                            browseService,
                            libraryFilterSignal,
                        )
                    }
                }
            }
        }
    }

    // The reader is fullscreen: it draws behind the system bars with its own controls and
    // hides the tab bar/rail while a chapter is open. Library multi-select hides them the same
    // way (Komikku parity: selection mode owns the chrome).
    val isReaderOpen = navigation.isReaderOpen
    ReaderSystemBars(hidden = isReaderOpen)
    // 引导页是一次性流程：与阅读器一样占满全屏，不显示底栏 / 导航轨。
    val isOnboardingOpen = navigation.topKey is OnboardingRoute
    val hideChrome = isOnboardingOpen ||
        (navigation.selectedTab == NavTab.Library && librarySession.selection.isNotEmpty()) ||
        (navigation.selectedTab == NavTab.History && activeHistorySession.selection.isNotEmpty())
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
                GlobalModeBanner(
                    downloadedOnly = activeMoreSession.downloadedOnly,
                    incognito = activeMoreSession.incognito,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
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
    historySession: app.koyura.app.history.HistorySession?,
) {
    BackHandler(enabled = navigation.returnsToLibraryOnBack()) {
        navigation.handleBack()
    }
    // 书架根上返回键先清选择态/搜索，不直接交平台；与“回书架”分支互斥。
    // 共享层/阅读器层打开时不抢返回键，交给各 NavDisplay 逐层 pop。
    val layersClear = navigation.sharedStack.isEmpty() && !navigation.isReaderOpen
    val consumeLibrary = layersClear && navigation.selectedTab == NavTab.Library &&
        navigation.currentStack.size <= 1 && librarySession?.wantsBack == true
    BackHandler(enabled = consumeLibrary) {
        librarySession?.consumeBack()
    }
    // 历史页多选：返回键先清选择态，不直接交平台（与书架同序）。
    val consumeHistory = layersClear && navigation.selectedTab == NavTab.History &&
        navigation.currentStack.size <= 1 && historySession?.wantsBack == true
    BackHandler(enabled = consumeHistory) {
        historySession?.clearSelection()
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
    settingsSession: SettingsSession,
    browseService: BrowseService?,
    libraryFilterSignal: androidx.compose.runtime.State<Int>,
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
        settingsSession,
        browseService,
        libraryFilterSignal.value,
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
                            onGlobalSearch = { navigation.push(GlobalSearchRoute) },
                            onOpenWork = { sourceKey, workId, title ->
                                navigation.push(SourceDetailRoute(sourceKey, workId, title))
                            },
                        )
                    }
                } else if (route.tab == NavTab.Library) {
                    LibraryTabContent(
                        navigation = navigation,
                        session = librarySession,
                        browseSession = browseSession,
                        onGlobalSearch = { navigation.push(GlobalSearchRoute) },
                        filterRequest = libraryFilterSignal.value,
                    )
                } else if (route.tab == NavTab.History) {
                    HistoryScreen(
                        session = historySession,
                        hasUnread = { entry ->
                            librarySession.snapshot.entries.any {
                                it.sourceKey == entry.sourceKey && it.workId == entry.workId && it.unreadCount > 0
                            }
                        },
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
                            scheduleState = LocalProductionSettings.current?.updateScheduleState?.collectAsState()?.value,
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
                        onOpenSettings = { navigation.push(SettingsRoute) },
                        onOpenUtility = { key -> navigation.push(UtilityPageRoute(key)) },
                    )
                } else {
                    TabRootScreen(
                        tab = route.tab,
                        onOpenDetail = {
                            navigation.push(DetailRoute(tab = route.tab, id = navigation.sharedStack.size))
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
            entry<GlobalSearchRoute> {
                // 每次进入重新开一次会话：结果页是独立子路由，返回即弃。
                // 过滤走生产装配（Local NSFW/隐藏偏好），失败回退全量扇出（见服务注释）。
                val searchSourceFilter = LocalSearchSourceFilter.current
                val globalSearchSession = remember(browseService, searchSourceFilter) {
                    GlobalSearchSession(
                        browseService,
                        GlobalSearchService(browseService, searchSourceFilter),
                    )
                }
                CompositionLocalProvider(LocalBrowseSession provides browseSession) {
                    GlobalSearchScreen(
                        session = globalSearchSession,
                        onBack = { navigation.popCurrentChild() },
                        onOpenWork = { sourceKey, workId, title ->
                            navigation.push(SourceDetailRoute(sourceKey, workId, title))
                        },
                    )
                }
            }
            entry<StatisticsRoute> {
                StatisticsScreen(
                    session = statisticsSession,
                    onBack = { navigation.popCurrentChild() },
                )
            }
            entry<OnboardingRoute> {
                val onboardingScope = rememberCoroutineScope()
                val onboardingSettings = LocalProductionSettings.current
                val completeOnboarding = {
                    onboardingScope.launch {
                        try {
                            onboardingSettings?.setOnboardingCompleted()
                        } catch (_: Exception) {
                        }
                    }
                    navigation.popCurrentChild()
                }
                OnboardingScreen(
                    onComplete = { completeOnboarding() },
                    onSkip = { completeOnboarding() },
                )
            }
            entry<SettingsRoute> {
                SettingsScreen(
                    session = settingsSession,
                    onOpenSubpage = { subpage ->
                        navigation.push(SettingsSubpageRoute(subpage.name))
                    },
                    onBack = { navigation.handleBack() },
                    onOpenOnboarding = { navigation.push(OnboardingRoute) },
                )
            }
            entry<SettingsSubpageRoute> { route ->
                val subpage = SettingsSubpage.entries.firstOrNull { it.name == route.subpageName }
                    ?: SettingsSubpage.APPEARANCE
                SettingsSubpageScreen(
                    session = settingsSession,
                    subpage = subpage,
                    onOpenOnboarding = { navigation.push(OnboardingRoute) },
                    onBack = { navigation.popCurrentChild() },
                )
            }
            entry<UtilityPageRoute> { route ->
                UtilityPageScreen(
                    key = route.key,
                    title = utilityPageTitle(route.key),
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
                        // G2：搜索页在共享栈顶时必须有返回出口（切 tab 不再关掉它）。
                        onBack = { navigation.popCurrentChild() },
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
                        fallbackBackground = ReaderBackground.BLACK,
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

    // tab 内容与两层叠加共用一格：Box 重叠（Column 会把后来的 fillMaxSize 孩子压到零高度）。
    Box(modifier = Modifier.fillMaxSize()) {
        saveableStateHolder.SaveableStateProvider(navigation.selectedTab.name) {        AnimatedContent(
            targetState = navigation.selectedTab,
            transitionSpec = {
                val half = DesignTokens.Motion.TabFadeThroughMs / 2
                fadeIn(animationSpec = tween(durationMillis = half, delayMillis = half)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = half))
            },
            label = "tabSwitch",
        ) { tab ->
            NavDisplay(
                backStack = navigation.stackOf(tab),
                onBack = { navigation.handleBack() },
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { childPageTransition(forward = true) },
                popTransitionSpec = { childPageTransition(forward = false) },
                predictivePopTransitionSpec = { childPageTransition(forward = false) },
                entryProvider = entryProvider,
            )
        }
        }

        // 共享层：跨 tab 二级页（详情/源内列表/全局搜索/设置/下载队列/分类/统计/工具/引导），
        // 从书架打开后切到其他 tab 依然保留（Komikku 全局 Navigator 对齐）。覆盖 tab 内容。
        if (navigation.isSharedOpen) {
            NavDisplay(
                backStack = navigation.sharedStack,
                onBack = { navigation.handleBack() },
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { childPageTransition(forward = true) },
                popTransitionSpec = { childPageTransition(forward = false) },
                predictivePopTransitionSpec = { childPageTransition(forward = false) },
                entryProvider = entryProvider,
            )
        }

        // 阅读器层：独立全屏栈（CLEAR_TOP，对齐 ReaderActivity），覆盖一切。
        if (navigation.isReaderOpen) {
            NavDisplay(
                backStack = navigation.readerStack,
                onBack = { navigation.handleBack() },
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { childPageTransition(forward = true) },
                popTransitionSpec = { childPageTransition(forward = false) },
                predictivePopTransitionSpec = { childPageTransition(forward = false) },
                entryProvider = entryProvider,
            )
        }
    }
}

/**
 * 页面 push/pop 转场：shared-axis-X（位移 1/3 宽 + 分阶段淡入淡出），300ms。
 *
 * CMP 的 material3 没有 `materialFadeThrough*` / `materialSharedAxisX*`（只有 MotionScheme 令牌），
 * 这里按 M3 同名转场的参数手写：退场元素先淡出（1/3 时长），进场元素延后淡入，位移取视口 1/3。
 * 白皮书 05 §5.1 #2。
 */
private fun childPageTransition(forward: Boolean): androidx.compose.animation.ContentTransform {
    val direction = if (forward) 1 else -1
    val total = DesignTokens.Motion.PageSharedAxisMs
    val outMs = total / 3
    val slide = tween<androidx.compose.ui.unit.IntOffset>(total, easing = FastOutSlowInEasing)
    return (
        slideInHorizontally(slide) { direction * it / 3 } +
            fadeIn(tween(total - outMs, delayMillis = outMs))
        ) togetherWith (
        slideOutHorizontally(tween(total, easing = LinearOutSlowInEasing)) { -direction * it / 3 } +
            fadeOut(tween(outMs))
        )
}

/** More 页工具占位的标题；key 与 `MoreScreen` 的导航行 key 一致。 */
@Composable
private fun utilityPageTitle(key: String): String = stringResource(when (key) {
    "update-errors" -> Res.string.utility_update_errors
    "data-storage" -> Res.string.utility_data_storage
    "batch-add" -> Res.string.utility_batch_add
    "about" -> Res.string.utility_about
    "help" -> Res.string.utility_help
    else -> Res.string.utility_title
})

/**
 * 全局模式横幅（白皮书 06 §6.16 / T3.3）：Downloaded only 与 Incognito 互斥，只显一个。
 *
 * 以 overlay 贴在内容顶部而不是插进 Column：插进布局会改变每个页面的可用高度，
 * 让既有截图/尺寸断言全部偏移；overlay 只在开启模式时出现，且不参与页面测量。
 * 状态栏 inset 由外层 `contentInsets` 负责，这里不再叠加，避免多任务时重复计算。
 */
@Composable
private fun GlobalModeBanner(
    downloadedOnly: Boolean,
    incognito: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        incognito -> StatusBanner(
            kind = BannerKind.INCOGNITO,
            text = stringResource(Res.string.global_banner_incognito),
            modifier = modifier.testTag(GlobalBannerTag),
        )

        downloadedOnly -> StatusBanner(
            kind = BannerKind.DOWNLOADED_ONLY,
            text = stringResource(Res.string.global_banner_downloaded_only),
            modifier = modifier.testTag(GlobalBannerTag),
        )
    }
}

const val GlobalBannerTag = "global-mode-banner"
