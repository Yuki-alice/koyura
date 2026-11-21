package app.koyura.app.navigation

import app.koyura.designsystem.navigation.NavTab
import kotlinx.serialization.Serializable

/** Placeholder child destination used to exercise stacks until real detail pages exist. */
@Serializable
data class DetailRoute(val tab: NavTab, val id: Int) : SharedKey

/** Installed extension details, pushed onto the shared stack from Browse. */
@Serializable
data class ExtensionDetailsRoute(val extensionKey: String) : SharedKey

/** Extension store management, pushed onto the shared stack from Browse. */
@Serializable
data class ExtensionStoresRoute(val focusStoreId: String? = null) : SharedKey

/** Installed source search page, pushed from the Sources pane. */
@Serializable
data class SourceSearchRoute(val sourceKey: String) : SharedKey

/** Work detail page opened from a search result. */
@Serializable
data class SourceDetailRoute(
    val sourceKey: String,
    val workId: String,
    val title: String = "",
) : SharedKey

/** Download queue page, pushed from Updates reselect or the More tab. */
@Serializable
data object DownloadQueueRoute : SharedKey

/** Category management page, pushed from the More tab; reads the real library repository. */
@Serializable
data object CategoriesRoute : SharedKey

/** Global search across every installed source, pushed from the Library empty state or Browse bar. */
@Serializable
data object GlobalSearchRoute : SharedKey

/** Settings master page (phone list / tablet two-pane), pushed from the More tab. */
@Serializable
data object SettingsRoute : SharedKey

/** One settings subpage, pushed on narrow layouts; the name maps to SettingsSubpage. */
@Serializable
data class SettingsSubpageRoute(val subpageName: String) : SharedKey

/** Utility entry from More whose full page is still a placeholder (update errors, about, help…). */
@Serializable
data class UtilityPageRoute(val key: String) : SharedKey

/** First-run guide; also reachable again from Settings → About. */
@Serializable
data object OnboardingRoute : SharedKey

/** Statistics page, pushed from the More tab. Fake-driven until the data layer aggregates it. */
@Serializable
data object StatisticsRoute : SharedKey
