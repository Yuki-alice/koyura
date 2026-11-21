package app.koyura.app.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.reflect.KClass

/**
 * 多模块聚合点（G3）。
 *
 * 路由键按层归属声明在各文件（[TabRoutes]、[SharedRoutes]、[ReaderRoutes]，
 * 阶段 2 按 feature 模块拆分时逐文件整体搬迁）；本文件是唯一聚合点。
 *
 * 注册全部手写显式条目（不靠反射，iOS 也能恢复）；
 * [registeredKeys] 与下方的 `subclass(...)` 块必须同步新增路由，
 * 漏登由 `RouteRegistryTest`（JVM 反射遍历密封层级）在 CI 拦下。
 */
object RouteRegistry {
    /** 已注册的全部路由键（与下方多态块一一对应）。 */
    val registeredKeys: List<KClass<out AppKey>> = listOf(
        TabRootRoute::class,
        DetailRoute::class,
        ExtensionDetailsRoute::class,
        ExtensionStoresRoute::class,
        SourceSearchRoute::class,
        SourceDetailRoute::class,
        ComicReaderRoute::class,
        DownloadQueueRoute::class,
        CategoriesRoute::class,
        StatisticsRoute::class,
        GlobalSearchRoute::class,
        SettingsRoute::class,
        SettingsSubpageRoute::class,
        OnboardingRoute::class,
        UtilityPageRoute::class,
    )
}

/**
 * Saved-state configuration registering every [AppKey] subtype for open polymorphism.
 *
 * Navigation 3 requires the registration to restore each stack after process death.
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
            subclass(OnboardingRoute::class, OnboardingRoute.serializer())
            subclass(UtilityPageRoute::class, UtilityPageRoute.serializer())
        }
    }
}
