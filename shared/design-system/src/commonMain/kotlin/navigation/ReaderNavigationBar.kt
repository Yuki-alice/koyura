package app.koyura.designsystem.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.resources.Res
import app.koyura.designsystem.resources.nav_browse_badge_description
import app.koyura.designsystem.resources.nav_updates_badge_description
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Compact navigation bar for narrow windows.
 *
 * Renders [visibleTabs] in their canonical order (Library, Updates, History, Browse, More) and
 * forwards every tap to [onTabClick]. Taps on the already selected destination are forwarded too,
 * letting the caller distinguish page switches from reselect actions.
 *
 * The component only renders and forwards clicks: choosing between this bar and
 * [ReaderNavigationRail], back stack handling, preferences and badge data all stay in the app layer.
 *
 * @param selectedTab destination that currently owns the selection indicator.
 * @param onTabClick invoked exactly once for every tap on a visible destination.
 * @param visibleTabs destinations to render; hidden destinations leave the remaining order intact.
 * @param labelMode whether every destination shows its label or only the selected one.
 * @param updatesBadgeCount count for [NavTab.Updates]; the badge is hidden unless greater than zero.
 * @param browseBadgeCount count for [NavTab.Browse]; the badge is hidden unless greater than zero.
 */
@Composable
fun ReaderNavigationBar(
    selectedTab: NavTab,
    onTabClick: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    visibleTabs: Set<NavTab> = NavTab.entries.toSet(),
    labelMode: NavLabelMode = NavLabelMode.AlwaysShow,
    updatesBadgeCount: Int = 0,
    browseBadgeCount: Int = 0,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    // Mirrors Komikku HomeScreen bottom bar, but the row is drawn here instead of delegating to
    // `NavigationBar`: M3's default puts 8 dp spacers at both ends, while the reference fills the
    // full width so five items divide the 360 dp phone width evenly (whitepaper 06 §6.2).
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColorFor(containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .heightIn(min = DesignTokens.Sizes.NavigationBarHeight)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            visibleTabs.ordered().forEach { tab ->
                val selected = tab == selectedTab
                NavigationBarItem(
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
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

internal fun Set<NavTab>.ordered(): List<NavTab> = NavTab.entries.filter { it in this }

@Composable
internal fun NavigationTabLabel(tab: NavTab) {
    Text(
        text = tab.title(),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun NavigationTabIcon(
    tab: NavTab,
    selected: Boolean,
    updatesBadgeCount: Int,
    browseBadgeCount: Int,
) {
    BadgedBox(
        badge = {
            when (tab) {
                NavTab.Updates -> if (updatesBadgeCount > 0) {
                    NavigationBadge(
                        count = updatesBadgeCount,
                        description = Res.plurals.nav_updates_badge_description,
                    )
                }

                NavTab.Browse -> if (browseBadgeCount > 0) {
                    NavigationBadge(
                        count = browseBadgeCount,
                        description = Res.plurals.nav_browse_badge_description,
                    )
                }

                else -> Unit
            }
        },
    ) {
        Icon(
            painter = painterResource(tab.icon(selected)),
            contentDescription = tab.title(),
        )
    }
}

@Composable
private fun NavigationBadge(count: Int, description: PluralStringResource) {
    val accessibleText = pluralStringResource(description, count, count)
    Badge {
        Text(
            text = count.toString(),
            modifier = Modifier.semantics { contentDescription = accessibleText },
        )
    }
}
