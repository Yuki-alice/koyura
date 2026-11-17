package app.koyura.designsystem.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Center-aligned navigation rail for wide windows.
 *
 * Mirrors Komikku's custom `tachiyomi.presentation.core.components.material.NavigationRail`
 * (used by `HomeScreen`): a Material 3 [Surface] with a full-height [Column] whose items are
 * packed with [Alignment.CenterVertically], so the entry group stays at the rail's vertical
 * center instead of sticking to the top. Items themselves are official Material 3
 * [NavigationRailItem]s with the same five tabs, order, selection indicator and Updates/Browse
 * badges as the bottom bar.
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
    val containerColor = NavigationRailDefaults.ContainerColor
    Surface(
        color = containerColor,
        contentColor = contentColorFor(containerColor),
        tonalElevation = NavigationRailElevation,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(windowInsets)
                .widthIn(min = NavigationRailMinWidth)
                .padding(vertical = NavigationRailVerticalPadding)
                .selectableGroup()
                .testTag(NavigationRailEntriesTag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                NavigationRailVerticalPadding,
                alignment = Alignment.CenterVertically,
            ),
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
}

const val NavigationRailEntriesTag = "navigation-rail-entries"

internal val NavigationRailMinWidth = 80.dp
internal val NavigationRailVerticalPadding = 4.dp
internal val NavigationRailElevation = 3.dp
