package app.koyura.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.koyura.app.reader.VeneraReaderService
import app.koyura.app.browse.venera.LocalProductionSources
import app.koyura.app.browse.venera.ProductionSourceRepository
import app.koyura.app.browse.venera.VeneraBrowseService
import app.koyura.app.history.VeneraHistoryService
import app.koyura.app.library.ProductionLibraryRepository
import app.koyura.app.library.LocalProductionDownloads
import app.koyura.app.library.ProductionDownloadQueueRepository
import app.koyura.app.library.ProductionStatisticsRepository
import app.koyura.app.library.LocalProductionSettings
import app.koyura.app.library.ProductionSettingsRepository
import app.koyura.features.statistics.LocalStatisticsRepository
import app.koyura.features.downloads.LocalDownloadQueueRepository
import app.koyura.app.account.ProductionAccountRepository
import app.koyura.app.updates.BackgroundLibraryUpdates
import app.koyura.app.updates.ProductionUpdatesRepository
import app.koyura.domain.updates.LibraryUpdateScheduleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import app.koyura.app.updates.ProductionUpdatePolicyRepository
import app.koyura.app.resources.Res
import app.koyura.app.resources.extension_startup_failed
import app.koyura.app.resources.extension_startup_loading
import app.koyura.app.resources.extension_startup_retry
import app.koyura.app.sourceui.FakeClipboard
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.data.session.ExtensionSession
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppSurface
import app.koyura.designsystem.components.AppText
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal expect fun PlatformExtensionApp()

@Composable
internal fun extensionEnvironment(platform: String): VeneraEnvironment {
    val locale = LocalLocale.current
    val veneraLocale = if (locale.region.isBlank()) locale.language else "${locale.language}_${locale.region}"
    return VeneraEnvironment(
        compatibleAppVersion = "1.16.0",
        locale = veneraLocale,
        platform = platform,
    )
}

@Composable
internal fun ExtensionApp(
    uiHost: SourceUiHost = SourceUiHost(FakeClipboard()),
    backgroundUpdates: BackgroundLibraryUpdates? = null,
    networkChanges: Flow<Unit> = emptyFlow(),
    scheduleBackgroundUpdate: suspend (LibraryUpdateScheduleState) -> Unit = {},
    requestUpdateNotifications: () -> Unit = {},
    /** 首启自动进入引导：真实平台入口置 true；直挂的测试/预览保持默认 false。 */
    enableOnboardingAutoEnter: Boolean = false,
    openSession: suspend (SourceUiHost) -> ExtensionSession,
) {
    var openedSession by remember { mutableStateOf<ExtensionSession?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        var session: ExtensionSession? = null
        try {
            // Capture ownership on the opening dispatcher before a cancelled caller can lose it.
            withContext(Dispatchers.IO) {
                session = if (backgroundUpdates != null) {
                    backgroundUpdates.openForeground { openSession(uiHost) }
                } else openSession(uiHost)
            }
            openedSession = checkNotNull(session)
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                session?.let {
                    if (backgroundUpdates != null) backgroundUpdates.closeForeground(it) else it.close()
                }
            }
        }
    }

    val session = openedSession
    if (session != null) {
        // 调度随会话启动，恢复前台时重新判断逾期和网络约束。
        val sessionScope = remember(session) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        LaunchedEffect(session) {
            session.updateScheduler.refreshSchedule()
            session.updateScheduler.start(sessionScope)
            session.updateScheduler.state
                .distinctUntilChangedBy { it.preferences to it.nextUpdateMillis }
                .collect { scheduleBackgroundUpdate(it) }
        }
        LaunchedEffect(session, networkChanges) {
            networkChanges.collect { session.updateScheduler.onForeground() }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { session.updateScheduler.onForeground() }
        DisposableEffect(session) {
            onDispose { sessionScope.cancel() }
        }
        val library = remember(session) {
            ProductionLibraryRepository(
                store = session.library,
                history = session.history,
                thumbnails = { sourceKey, imageKey ->
                    session.comicReading.loadThumbnailBytes(sourceKey, imageKey)
                },
                downloadedCounts = { sourceKey, workId ->
                    session.downloads.countCompleted(sourceKey, workId).toLong()
                },
            )
        }
        val updates = remember(session) {
            ProductionUpdatesRepository(session.updates)
        }
        val updatePolicy = remember(session) {
            ProductionUpdatePolicyRepository(session.updates)
        }
        val accounts = remember(session) {
            ProductionAccountRepository(session.service)
        }
        val sources = remember(session) { ProductionSourceRepository(session.discovery, session.feed, session.service.browse) }
        val browse = remember(session) {
            VeneraBrowseService(
                extensions = session.service,
                thumbnails = { sourceKey, imageKey ->
                    session.comicReading.loadThumbnailBytes(sourceKey, imageKey)
                },
                onDetailLoaded = { sourceKey, workId, chapters ->
                    library.syncWorkDetail(
                        sourceKey = sourceKey,
                        kind = app.koyura.features.library.LibraryKind.COMIC,
                        workId = workId,
                        chapters = chapters,
                    )
                },
                search = session.service.browse,
                sources = sources,
            )
        }
        val settings = remember(session) {
            ProductionSettingsRepository(
                session.library, session.readerPreferences, session.downloadTriggers,
                session.updateScheduler, requestUpdateNotifications,
            )
        }
        val reader = remember(session) {
            VeneraReaderService(
                reading = session.comicReading,
                history = session.history,
                library = session.library,
                downloadTriggers = session.downloadTriggers,
                isIncognito = { settings.isIncognito() },
            )
        }
        val history = remember(session) { VeneraHistoryService(session.history, session.library) }
        val downloads = remember(session) { ProductionDownloadQueueRepository(session.downloadQueue) }
        val statistics = remember(session) { ProductionStatisticsRepository(session.statistics) }
        CompositionLocalProvider(
            LocalProductionSources provides sources,
            LocalProductionSettings provides settings,
            LocalProductionDownloads provides downloads,
            LocalDownloadQueueRepository provides downloads,
            LocalStatisticsRepository provides statistics,
        ) {
            App(
                browseService = browse,
                readerService = reader,
                historyService = history,
                readerPreferences = settings,
                libraryRepository = library,
                updatesRepository = updates,
                updatePolicyRepository = updatePolicy,
                accountRepository = accounts,
                searchSourceFilter = { sources.discovery.searchSourceKeys() },
                sourceUiHost = uiHost,
                enableOnboardingAutoEnter = enableOnboardingAutoEnter,
            )
        }
    } else {
        ReaderTheme {
            AppSurface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Medium, Alignment.CenterVertically),
                ) {
                    if (failed) {
                        AppText(stringResource(Res.string.extension_startup_failed))
                        AppButton(onClick = {
                            failed = false
                            attempt += 1
                        }) { AppText(stringResource(Res.string.extension_startup_retry)) }
                    } else {
                        AppCircularProgress()
                        AppText(stringResource(Res.string.extension_startup_loading))
                    }
                }
            }
        }
    }
}
