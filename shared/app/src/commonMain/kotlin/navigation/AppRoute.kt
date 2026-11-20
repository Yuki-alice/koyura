package app.koyura.app.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import app.koyura.designsystem.navigation.NavTab
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/** A serializable destination key understood by Navigation 3. */
@Serializable
sealed interface AppRoute : NavKey

/** Root destination of one top-level tab. */
@Serializable
data class TabRootRoute(val tab: NavTab) : AppRoute

/** Placeholder child destination used to exercise per-tab stacks until real detail pages exist. */
@Serializable
data class DetailRoute(val tab: NavTab, val id: Int) : AppRoute

/** Installed extension details, pushed onto the Browse tab stack. */
@Serializable
data class ExtensionDetailsRoute(val extensionKey: String) : AppRoute

/** Extension store management, pushed onto the Browse tab stack. */
@Serializable
data class ExtensionStoresRoute(val focusStoreId: String? = null) : AppRoute

/** Installed source search page, pushed from the Sources pane. */
@Serializable
data class SourceSearchRoute(val sourceKey: String) : AppRoute

/** Work detail page opened from a search result. */
@Serializable
data class SourceDetailRoute(
    val sourceKey: String,
    val workId: String,
    val title: String = "",
) : AppRoute

/** Fullscreen comic reader for one chapter ([chapterId] null for works without chapters). */
@Serializable
data class ComicReaderRoute(
    val sourceKey: String,
    val workId: String,
    val chapterId: String? = null,
    val title: String = "",
    val chapterIds: List<String> = emptyList(),
    val chapterTitles: List<String> = emptyList(),
) : AppRoute

/** Download queue page, pushed from the More tab. Fake-driven until the engine lands. */
@Serializable
data object DownloadQueueRoute : AppRoute

/** Category management page, pushed from the More tab; reads the real library repository. */
@Serializable
data object CategoriesRoute : AppRoute

/** Global search across every installed source, pushed from the Library empty state or Browse bar. */
@Serializable
data object GlobalSearchRoute : AppRoute

/** Settings master page (phone list / tablet two-pane), pushed from the More tab. */
@Serializable
data object SettingsRoute : AppRoute

/** One settings subpage, pushed on narrow layouts; the name maps to SettingsSubpage. */
@Serializable
data class SettingsSubpageRoute(val subpageName: String) : AppRoute

/** Utility entry from More whose full page is still a placeholder (update errors, about, help…). */
@Serializable
data class UtilityPageRoute(val key: String) : AppRoute

/** Statistics page, pushed from the More tab. Fake-driven until the data layer aggregates it. */
@Serializable
data object StatisticsRoute : AppRoute

/**
 * Saved-state configuration registering every [AppRoute] subtype for open polymorphism.
 *
 * Navigation 3 requires the registration to restore each tab's stack after process death.
 */
val ReaderSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(TabRootRoute::class, TabRootRoute.serializer())
            subclass(DetailRoute::class, DetailRoute.serializer())
            subclass(ExtensionDetailsRoute::class, ExtensionDetailsRoute.serializer())
            subclass(ExtensionStoresRoute::class, ExtensionStoresRoute.serializer())
            subclass(SourceSearchRoute::class, SourceSearchRoute.serializer())
            subclass(SourceDetailRoute::class, SourceDetailRoute.serializer())
            subclass(ComicReaderRoute::class, ComicReaderRoute.serializer())
            subclass(DownloadQueueRoute::class, DownloadQueueRoute.serializer())
            subclass(CategoriesRoute::class, CategoriesRoute.serializer())
            subclass(StatisticsRoute::class, StatisticsRoute.serializer())
            subclass(GlobalSearchRoute::class, GlobalSearchRoute.serializer())
            subclass(SettingsRoute::class, SettingsRoute.serializer())
            subclass(SettingsSubpageRoute::class, SettingsSubpageRoute.serializer())
            subclass(UtilityPageRoute::class, UtilityPageRoute.serializer())
        }
    }
}
