package app.koyura.data.browse

import app.koyura.data.extension.SourceBrowseService
import app.koyura.domain.browse.SourceFeedId
import app.koyura.domain.browse.SourceFeedItem
import app.koyura.domain.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SourceFeedService internal constructor(
    private val library: LibraryRepository,
    private val browse: SourceBrowseService,
    private val discovery: SourceDiscoveryService,
) {
    private val mutex = Mutex()

    suspend fun feedItems(): List<SourceFeedItem> {
        val enabled = feedSettings().filter { it.enabled }
        val visible = discovery.visibleKeys(enabled.mapTo(hashSetOf()) { it.sourceKey })
        return enabled.filter { it.sourceKey in visible }
    }

    suspend fun feedSettings(): List<SourceFeedItem> = update { preferences, _ -> preferences }

    suspend fun setEnabled(id: SourceFeedId, enabled: Boolean): Boolean {
        var found = false
        update { preferences, available ->
            found = id in available
            if (!found) preferences else preferences.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
        return found
    }

    /** Reorder the supplied visible items within their slots. Omitted items keep their places. */
    suspend fun reorder(order: List<SourceFeedId>) {
        require(order.distinct().size == order.size) { "Duplicate feed item" }
        update { preferences, available ->
            require(order.all { it in available }) { "Unknown feed item" }
            val requested = order.toSet()
            val byId = preferences.associateBy { it.id }
            val replacement = order.iterator()
            preferences.map { if (it.id in requested) byId.getValue(replacement.next()) else it }
        }
    }

    private suspend fun update(
        change: (List<Preference>, Map<SourceFeedId, SourceFeedItem>) -> List<Preference>,
    ): List<SourceFeedItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val declarations = browse.feedDeclarations()
            val available = declarations.values.flatten().associateBy { it.id }
            val saved = library.getPref(PREFERENCE)?.let { json.decodeFromString<List<Preference>>(it) }.orEmpty()
            // Keep preferences for unavailable sources. A successful empty declaration removes its old entries.
            val surviving = saved.filter { it.id.sourceKey !in declarations || it.id in available }
            val known = surviving.mapTo(hashSetOf()) { it.id }
            val reconciled = surviving + available.keys.filterNot { it in known }.map { Preference(it) }
            val updated = change(reconciled, available)
            if (updated != saved) library.setPref(PREFERENCE, json.encodeToString(updated))
            updated.mapNotNull { pref -> available[pref.id]?.copy(enabled = pref.enabled) }
        }
    }

    @Serializable
    private data class Preference(val id: SourceFeedId, val enabled: Boolean = true)

    private companion object {
        const val PREFERENCE = "browse.feed"
        val json = Json { ignoreUnknownKeys = true }
    }
}
