package app.koyura.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.LocalBrowseService
import app.koyura.app.browse.globalsearch.LocalSearchSourceFilter
import app.koyura.app.history.HistoryService
import app.koyura.app.history.LocalHistoryService
import app.koyura.app.navigation.DownloadQueueRoute
import app.koyura.app.navigation.TabletUiMode
import app.koyura.app.navigation.rememberAppNavigationState
import app.koyura.app.navigation.usesNavigationRailForWindow
import app.koyura.app.reader.VeneraReaderService
import app.koyura.app.sourceui.LocalSourceUiHost
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.app.sourceui.SourceUiOverlay
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.components.AppSurface
import app.koyura.designsystem.navigation.NavTab
import app.koyura.domain.reader.ReaderService
import app.koyura.domain.reader.preferences.ReaderPreferences
import app.koyura.features.account.AccountRepository
import app.koyura.features.account.LocalAccountRepository
import app.koyura.features.downloads.LocalDownloadQueueVisible
import app.koyura.features.library.LibraryRepository as FeaturesLibraryRepository
import app.koyura.features.library.LocalLibraryRepository
import app.koyura.features.updates.LocalUpdatePolicyRepository
import app.koyura.features.updates.LocalUpdatesRepository
import app.koyura.features.updates.UpdatePolicyRepository
import app.koyura.features.updates.UpdatesRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** Base tablet rule kept as a pure function so the 719/720 dp boundary is directly testable. */
internal val RailWidthThreshold = DesignTokens.Breakpoints.TabletMinSmallestWidthDp.dp

/**
 * Base width rule (`isTabletUi`): smallest width at or above 720 dp uses the rail.
 * Automatic portrait/landscape thresholds live in [usesNavigationRailForWindow].
 */

/**
 * Shared application shell used by Android, desktop JVM and iOS.
 *
 * The shell picks the layout from the live content width, keeps one Navigation 3 back stack per
 * tab, and translates back gestures into a tab-local pop, a fallback to the Library tab, or a
 * platform handoff.
 */
@Composable
fun App() {
    val injectedService = LocalBrowseService.current
    if (injectedService == null) {
        PlatformExtensionApp()
    } else {
        App(injectedService)
    }
}

/**
 * The shared shell with Browse and reader services supplied by startup, a preview, or a test.
 */
@Composable
fun App(
    browseService: BrowseService? = null,
    readerService: ReaderService? = null,
    historyService: HistoryService? = null,
    readerPreferences: ReaderPreferences? = null,
    libraryRepository: FeaturesLibraryRepository? = null,
    updatesRepository: UpdatesRepository? = null,
    updatePolicyRepository: UpdatePolicyRepository? = null,
    accountRepository: AccountRepository? = null,
    searchSourceFilter: (suspend () -> Set<String>)? = null,
    sourceUiHost: SourceUiHost? = null,
    /** 首启自动进入引导：生产装配（ExtensionApp）置 true，测试/预览保持默认 false。 */
    enableOnboardingAutoEnter: Boolean = false,
) {
    ReaderTheme {
        AppAppearance {
            CompositionLocalProvider(
                LocalBrowseService provides browseService,
                LocalReaderService provides readerService,
                LocalReaderPreferences provides readerPreferences,
                LocalHistoryService provides historyService,
                LocalLibraryRepository provides libraryRepository,
                LocalUpdatesRepository provides updatesRepository,
                LocalUpdatePolicyRepository provides updatePolicyRepository,
                LocalAccountRepository provides accountRepository,
                LocalSearchSourceFilter provides searchSourceFilter,
                LocalSourceUiHost provides sourceUiHost,
            ) {
                AppSurface(modifier = Modifier.fillMaxSize()) {
                    AppShell(enableOnboardingAutoEnter = enableOnboardingAutoEnter)
                    val host = sourceUiHost
                    if (host != null) {
                        SourceUiOverlay(host = host)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppShell(
    onTabReselect: (NavTab) -> Unit = {},
    reselectActions: Map<NavTab, () -> Unit>? = null,
    tabletMode: TabletUiMode = TabletUiMode.AUTOMATIC,
    enableOnboardingAutoEnter: Boolean = false,
) {
    val navigation = rememberAppNavigationState(
        onTabReselect = onTabReselect,
        reselectActions = reselectActions,
    )
    val duration = (LocalReaderService.current as? VeneraReaderService)?.duration
    if (duration != null) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(navigation, duration, lifecycle) {
            try {
                combine(
                    snapshotFlow { navigation.isReaderOpen },
                    lifecycle.currentStateFlow,
                ) { visible, state -> visible to state.isAtLeast(Lifecycle.State.RESUMED) }
                    .collect { (visible, resumed) -> duration.setVisibility(visible, resumed) }
            } finally {
                withContext(NonCancellable) { duration.setVisibility(visible = false, resumed = false) }
            }
        }
    }
    CompositionLocalProvider(LocalDownloadQueueVisible provides (navigation.topKey is DownloadQueueRoute)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            AppScaffold(
                navigation = navigation,
                useRail = usesNavigationRailForWindow(maxWidth, maxHeight, tabletMode),
                enableOnboardingAutoEnter = enableOnboardingAutoEnter,
            )
        }
    }
}

/** Base width rule kept as a pure function so the 719/720 dp boundary is directly testable. */
internal fun usesNavigationRail(width: Dp): Boolean = width >= RailWidthThreshold
