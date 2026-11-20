package app.koyura.app.browse.venera

import androidx.compose.runtime.staticCompositionLocalOf
import app.koyura.data.browse.SourceDiscoveryService
import app.koyura.data.browse.SourceFeedService
import app.koyura.data.extension.SourceBrowseService
import app.koyura.data.extension.SourceExplorePage
import app.koyura.domain.browse.SourceFeedId
import kotlinx.serialization.json.JsonElement

internal val LocalProductionSources = staticCompositionLocalOf<ProductionSourceRepository?> { null }

class ProductionSourceRepository(
    val discovery: SourceDiscoveryService,
    val feed: SourceFeedService,
    private val browse: SourceBrowseService,
) {
    /** Resolve by stable title each time because upgrades may reorder explore declarations. */
    suspend fun loadFeedPage(id: SourceFeedId, position: Int? = null, next: String? = null): JsonElement {
        val item = requireNotNull(feed.feedItems().firstOrNull { it.id == id }) { "Feed item is unavailable" }
        val page = SourceExplorePage(item.index, item.title, item.type, item.numbered, item.cursor)
        return browse.explore(id.sourceKey, page, position, next)
    }
}
