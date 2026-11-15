package dev.reader.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.resources.Res
import dev.reader.app.resources.nav_screen_browse_body
import dev.reader.app.resources.nav_screen_browse_title
import dev.reader.app.resources.nav_screen_detail_body
import dev.reader.app.resources.nav_screen_detail_title
import dev.reader.app.resources.nav_screen_history_body
import dev.reader.app.resources.nav_screen_history_title
import dev.reader.app.resources.nav_screen_library_body
import dev.reader.app.resources.nav_screen_library_title
import dev.reader.app.resources.nav_screen_more_body
import dev.reader.app.resources.nav_screen_more_title
import dev.reader.app.resources.nav_screen_open_detail
import dev.reader.app.resources.nav_screen_updates_body
import dev.reader.app.resources.nav_screen_updates_title
import dev.reader.designsystem.navigation.NavTab
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal data class TabPageContent(
    val title: StringResource,
    val body: StringResource,
)

internal fun tabPageContent(tab: NavTab): TabPageContent = when (tab) {
    NavTab.Library -> TabPageContent(Res.string.nav_screen_library_title, Res.string.nav_screen_library_body)
    NavTab.Updates -> TabPageContent(Res.string.nav_screen_updates_title, Res.string.nav_screen_updates_body)
    NavTab.History -> TabPageContent(Res.string.nav_screen_history_title, Res.string.nav_screen_history_body)
    NavTab.Browse -> TabPageContent(Res.string.nav_screen_browse_title, Res.string.nav_screen_browse_body)
    NavTab.More -> TabPageContent(Res.string.nav_screen_more_title, Res.string.nav_screen_more_body)
}

/** Root page of a top-level tab. The resource title doubles as the screen heading. */
@Composable
fun TabRootScreen(
    tab: NavTab,
    modifier: Modifier = Modifier,
    onOpenDetail: () -> Unit = {},
) {
    val content = tabPageContent(tab)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(content.title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("tab-title-${tab.name}"),
        )
        Text(
            text = stringResource(content.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onOpenDetail,
            modifier = Modifier.testTag("open-detail-${tab.name}"),
        ) {
            Text(stringResource(Res.string.nav_screen_open_detail))
        }
    }
}

/** Placeholder child page used to exercise per-tab stacks until real detail screens exist. */
@Composable
fun DetailScreen(origin: NavTab, id: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.nav_screen_detail_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("detail-title"),
        )
        Text(
            text = stringResource(Res.string.nav_screen_detail_body, stringResource(tabPageContent(origin).title)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "#$id",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
