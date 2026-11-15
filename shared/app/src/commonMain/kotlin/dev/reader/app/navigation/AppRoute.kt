package dev.reader.app.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.reader.designsystem.navigation.NavTab
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
        }
    }
}
