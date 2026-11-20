package app.koyura.app.ui

import app.koyura.designsystem.DesignTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.koyura.app.resources.Res
import app.koyura.app.resources.nav_screen_browse_body
import app.koyura.app.resources.nav_screen_browse_title
import app.koyura.app.resources.nav_screen_detail_body
import app.koyura.app.resources.nav_screen_detail_title
import app.koyura.app.resources.nav_screen_history_body
import app.koyura.app.resources.nav_screen_history_title
import app.koyura.app.resources.nav_screen_library_body
import app.koyura.app.resources.nav_screen_library_title
import app.koyura.app.resources.nav_screen_more_body
import app.koyura.app.resources.nav_screen_more_title
import app.koyura.app.resources.nav_screen_open_detail
import app.koyura.app.resources.nav_screen_updates_body
import app.koyura.app.resources.nav_screen_updates_title
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme
import app.koyura.designsystem.navigation.NavTab
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
            .padding(DesignTokens.Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
    ) {
        AppText(
            text = stringResource(content.title),
            style = UiTheme.typography.headlineMedium,
            modifier = Modifier.testTag("tab-title-${tab.name}"),
        )
        AppText(
            text = stringResource(content.body),
            style = UiTheme.typography.bodyLarge,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
        AppButton(
            onClick = onOpenDetail,
            modifier = Modifier.testTag("open-detail-${tab.name}"),
        ) {
            AppText(stringResource(Res.string.nav_screen_open_detail))
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
            .padding(DesignTokens.Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
    ) {
        AppText(
            text = stringResource(Res.string.nav_screen_detail_title),
            style = UiTheme.typography.headlineMedium,
            modifier = Modifier.testTag("detail-title"),
        )
        AppText(
            text = stringResource(Res.string.nav_screen_detail_body, stringResource(tabPageContent(origin).title)),
            style = UiTheme.typography.bodyLarge,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
        AppText(
            text = "#$id",
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
    }
}
