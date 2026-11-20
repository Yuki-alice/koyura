package app.koyura.data.browse

import app.koyura.data.extension.SourceBrowseService
import app.koyura.domain.browse.DiscoverySource
import app.koyura.domain.browse.SourceDiscovery
import app.koyura.domain.browse.SourceGroup
import app.koyura.domain.library.LibraryRepository
import app.koyura.source.runtime.VeneraCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SourceDiscoveryService internal constructor(
    private val library: LibraryRepository,
    private val browse: SourceBrowseService,
) {
    private val mutex = Mutex()

    suspend fun sources(groupId: Long? = null, includeFiltered: Boolean = false): SourceDiscovery =
        withContext(Dispatchers.IO) {
            val prefs = read()
            val installed = browse.registeredSources().map { source ->
                val entry = prefs.sources[source.key] ?: SourcePreference()
                DiscoverySource(
                    source.key, source.name,
                    VeneraCallback.Search in source.callbacks || VeneraCallback.SearchNext in source.callbacks,
                    entry.pinned, entry.hidden, entry.nsfw, entry.groupIds,
                )
            }
            val visible = installed.filter { prefs.isVisible(it.key) }
            val results = if (includeFiltered) installed else visible
            SourceDiscovery(
                results.filter { groupId == null || groupId in it.groupIds }
                    .sortedWith(compareByDescending<DiscoverySource> { it.pinned }.thenBy { it.name.lowercase() }.thenBy { it.key }),
                prefs.groups, prefs.showNsfw, prefs.preferredSource,
                prefs.preferredSource?.takeIf { key -> visible.any { it.key == key } },
            )
        }

    /** Source ids eligible for global fan-out. Resolve again on each search. */
    suspend fun searchSourceKeys(): Set<String> = sources().sources.filter { it.canSearch }.mapTo(linkedSetOf()) { it.key }

    internal suspend fun visibleKeys(keys: Set<String>): Set<String> = withContext(Dispatchers.IO) {
        val prefs = read()
        keys.filterTo(hashSetOf(), prefs::isVisible)
    }

    suspend fun setPinned(key: String, value: Boolean) = changeSource(key) { it.copy(pinned = value) }

    suspend fun setHidden(key: String, value: Boolean) = changeSource(key) { it.copy(hidden = value) }

    suspend fun setNsfw(key: String, value: Boolean) = changeSource(key) { it.copy(nsfw = value) }

    suspend fun setShowNsfw(value: Boolean) {
        change { it.copy(showNsfw = value) }
    }

    suspend fun setDefaultSource(key: String?) {
        if (key != null) require(key.isNotBlank())
        change { it.copy(preferredSource = key) }
    }

    suspend fun createGroup(name: String): SourceGroup {
        val title = name.trim()
        val updated = change { prefs ->
            prefs.requireAvailableName(title)
            prefs.copy(groups = prefs.groups + SourceGroup(prefs.nextGroupId, title), nextGroupId = prefs.nextGroupId + 1)
        }
        return updated.groups.last()
    }

    suspend fun renameGroup(id: Long, name: String) {
        val title = name.trim()
        change { prefs ->
            require(prefs.groups.any { it.id == id }) { "Unknown source group" }
            prefs.requireAvailableName(title, except = id)
            prefs.copy(groups = prefs.groups.map { if (it.id == id) it.copy(name = title) else it })
        }
    }

    suspend fun deleteGroup(id: Long) {
        change { prefs ->
            prefs.copy(
                groups = prefs.groups.filterNot { it.id == id },
                sources = prefs.sources.mapValues { (_, source) -> source.copy(groupIds = source.groupIds - id) },
            )
        }
    }

    suspend fun setSourceGroups(key: String, groupIds: Set<Long>) {
        require(key.isNotBlank())
        change { prefs ->
            require(groupIds.all { id -> prefs.groups.any { it.id == id } }) { "Unknown source group" }
            prefs.withSource(key, (prefs.sources[key] ?: SourcePreference()).copy(groupIds = groupIds.toSet()))
        }
    }

    private suspend fun changeSource(key: String, update: (SourcePreference) -> SourcePreference) {
        require(key.isNotBlank())
        change { it.withSource(key, update(it.sources[key] ?: SourcePreference())) }
    }

    private suspend fun change(update: (Preferences) -> Preferences): Preferences = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = read()
            val updated = update(current)
            if (updated != current) library.setPref(PREFERENCE, json.encodeToString(updated))
            updated
        }
    }

    private suspend fun read(): Preferences =
        library.getPref(PREFERENCE)?.let { json.decodeFromString<Preferences>(it) } ?: Preferences()

    @Serializable
    private data class SourcePreference(
        val pinned: Boolean = false,
        val hidden: Boolean = false,
        val nsfw: Boolean = false,
        val groupIds: Set<Long> = emptySet(),
    )

    @Serializable
    private data class Preferences(
        val showNsfw: Boolean = true,
        val preferredSource: String? = null,
        val nextGroupId: Long = 1,
        val groups: List<SourceGroup> = emptyList(),
        val sources: Map<String, SourcePreference> = emptyMap(),
    ) {
        fun isVisible(key: String): Boolean {
            val entry = sources[key]
            return entry?.hidden != true && (showNsfw || entry?.nsfw != true)
        }

        fun withSource(key: String, value: SourcePreference): Preferences =
            copy(sources = if (value == SourcePreference()) sources - key else sources + (key to value))

        fun requireAvailableName(name: String, except: Long? = null) {
            require(name.isNotEmpty() && groups.none { it.id != except && it.name.equals(name, ignoreCase = true) }) {
                "Blank or duplicate source group name"
            }
        }
    }

    private companion object {
        const val PREFERENCE = "browse.discovery"
        val json = Json { ignoreUnknownKeys = true }
    }
}
