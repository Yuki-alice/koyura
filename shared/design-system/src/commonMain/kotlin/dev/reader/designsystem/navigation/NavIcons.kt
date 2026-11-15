package dev.reader.designsystem.navigation

import androidx.compose.runtime.Composable
import dev.reader.designsystem.resources.Res
import dev.reader.designsystem.resources.nav_browse
import dev.reader.designsystem.resources.nav_browse_outline
import dev.reader.designsystem.resources.nav_browse_selected
import dev.reader.designsystem.resources.nav_history
import dev.reader.designsystem.resources.nav_library
import dev.reader.designsystem.resources.nav_library_outline
import dev.reader.designsystem.resources.nav_library_selected
import dev.reader.designsystem.resources.nav_more
import dev.reader.designsystem.resources.nav_updates
import dev.reader.designsystem.resources.nav_updates_outline
import dev.reader.designsystem.resources.nav_updates_selected
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NavTab.title(): String = when (this) {
    NavTab.Library -> stringResource(Res.string.nav_library)
    NavTab.Updates -> stringResource(Res.string.nav_updates)
    NavTab.History -> stringResource(Res.string.nav_history)
    NavTab.Browse -> stringResource(Res.string.nav_browse)
    NavTab.More -> stringResource(Res.string.nav_more)
}

/**
 * Outline icon for unselected destinations and filled counterpart for the selected one,
 * mirroring Komikku's animated vector end states.
 */
internal fun NavTab.icon(selected: Boolean): DrawableResource = when (this) {
    NavTab.Library -> if (selected) Res.drawable.nav_library_selected else Res.drawable.nav_library_outline
    NavTab.Updates -> if (selected) Res.drawable.nav_updates_selected else Res.drawable.nav_updates_outline
    NavTab.History -> Res.drawable.nav_history
    NavTab.Browse -> if (selected) Res.drawable.nav_browse_selected else Res.drawable.nav_browse_outline
    NavTab.More -> Res.drawable.nav_more
}
