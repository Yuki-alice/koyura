package app.koyura.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.LocalBrowseService
import app.koyura.app.sourceui.LocalSourceUiHost
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.app.sourceui.SourceUiOverlay
import app.koyura.app.history.HistoryService
import app.koyura.app.history.LocalHistoryService
import app.koyura.domain.reader.ReaderService
import app.koyura.domain.reader.preferences.ReaderPreferences
import app.koyura.app.navigation.rememberAppNavigationState
import app.koyura.app.navigation.TabletUiMode
import app.koyura.app.navigation.usesNavigationRailForWindow
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.features.library.LocalLibraryRepository
import app.koyura.features.library.LibraryRepository as FeaturesLibraryRepository
import app.koyura.features.updates.LocalUpdatesRepository
import app.koyura.features.updates.UpdatesRepository

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
    sourceUiHost: SourceUiHost? = null,
) {
    ReaderTheme {
        CompositionLocalProvider(
            LocalBrowseService provides browseService,
            LocalReaderService provides readerService,
            LocalReaderPreferences provides readerPreferences,
            LocalHistoryService provides historyService,
            LocalLibraryRepository provides libraryRepository,
            LocalUpdatesRepository provides updatesRepository,
            LocalSourceUiHost provides sourceUiHost,
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppShell()
                val host = sourceUiHost
                if (host != null) {
                    SourceUiOverlay(host = host)
                }
            }
        }
    }
}

@Composable
internal fun AppShell(
    onTabReselect: (NavTab) -> Unit = {},
    tabletMode: TabletUiMode = TabletUiMode.AUTOMATIC,
) {
    val navigation = rememberAppNavigationState(onTabReselect)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            navigation = navigation,
            useRail = usesNavigationRailForWindow(maxWidth, maxHeight, tabletMode),
        )
    }
}

/** Base width rule kept as a pure function so the 719/720 dp boundary is directly testable. */
internal fun usesNavigationRail(width: Dp): Boolean = width >= RailWidthThreshold
