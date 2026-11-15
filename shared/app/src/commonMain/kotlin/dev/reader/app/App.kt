package dev.reader.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reader.app.browse.BrowseService
import dev.reader.app.browse.LocalBrowseService
import dev.reader.app.navigation.rememberAppNavigationState
import dev.reader.designsystem.ReaderTheme
import dev.reader.designsystem.navigation.NavTab

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
    App(browseService = null)
}

/**
 * Entry point with an injectable Browse runtime.
 *
 * Production callers pass `null` until the real source/extension runtime is wired; the Browse tab
 * then renders a clear empty state while the rest of the shell keeps working. Tests and future
 * assembly code pass a [BrowseService] implementation.
 */
@Composable
fun App(browseService: BrowseService?) {
    ReaderTheme {
        CompositionLocalProvider(LocalBrowseService provides browseService) {
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
