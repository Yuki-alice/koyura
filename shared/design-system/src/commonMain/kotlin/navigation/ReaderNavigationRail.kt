package app.koyura.designsystem.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Center-aligned navigation rail for wide windows.
 *
 * Behaves like [ReaderNavigationBar]: [visibleTabs] keep their canonical order, every tap is
 * forwarded once through [onTabClick] (including taps on the current destination), and the rail
 * only renders. Width selection and navigation state belong to the application layer.
 *
 * @param selectedTab destination that currently owns the selection indicator.
 * @param onTabClick invoked exactly once for every tap on a visible destination.
 * @param visibleTabs destinations to render; hidden destinations leave the remaining order intact.
 * @param labelMode whether every destination shows its label or only the selected one.
 * @param updatesBadgeCount count for [NavTab.Updates]; the badge is hidden unless greater than zero.
 * @param browseBadgeCount count for [NavTab.Browse]; the badge is hidden unless greater than zero.
 */
@Composable
fun ReaderNavigationRail(
    selectedTab: NavTab,
    onTabClick: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    visibleTabs: Set<NavTab> = NavTab.entries.toSet(),
    labelMode: NavLabelMode = NavLabelMode.AlwaysShow,
    updatesBadgeCount: Int = 0,
    browseBadgeCount: Int = 0,
    windowInsets: WindowInsets = NavigationRailDefaults.windowInsets,
) {
    // Mirrors Komikku HomeScreen navigation rail: Material 3 NavigationRail with the same five
    // tabs, order, selection indicator and Updates/Browse badges as the bottom bar.
    NavigationRail(
        modifier = modifier,
        windowInsets = windowInsets,
    ) {
        visibleTabs.ordered().forEach { tab ->
            val selected = tab == selectedTab
            NavigationRailItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = {
                    NavigationTabIcon(
                        tab = tab,
                        selected = selected,
                        updatesBadgeCount = updatesBadgeCount,
                        browseBadgeCount = browseBadgeCount,
                    )
                },
                label = { NavigationTabLabel(tab) },
                alwaysShowLabel = labelMode == NavLabelMode.AlwaysShow,
            )
        }
    }
}
