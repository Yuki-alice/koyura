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
import app.koyura.app.history.HistoryService
import app.koyura.app.history.LocalHistoryService
import app.koyura.app.reader.LocalReaderService
import app.koyura.app.reader.ReaderService
import app.koyura.app.navigation.rememberAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab

/** Width at or above which the shell switches from the bottom bar to the navigation rail. */
internal val RailWidthThreshold = 720.dp

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
) {
    ReaderTheme {
        CompositionLocalProvider(
            LocalBrowseService provides browseService,
            LocalReaderService provides readerService,
            LocalHistoryService provides historyService,
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppShell()
            }
        }
    }
}

@Composable
internal fun AppShell(onTabReselect: (NavTab) -> Unit = {}) {
    val navigation = rememberAppNavigationState(onTabReselect)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            navigation = navigation,
            useRail = usesNavigationRail(maxWidth),
        )
    }
}

/** Width rule kept as a pure function so the 719/720 dp boundary is directly testable. */
internal fun usesNavigationRail(width: Dp): Boolean = width >= RailWidthThreshold
