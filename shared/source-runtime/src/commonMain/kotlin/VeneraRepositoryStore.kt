package app.koyura.source.runtime

import io.ktor.client.HttpClient
import kotlin.uuid.Uuid

/**
 * Milestone 1: in-memory repository registry.
 *
 * Upstream source: `lib/features/comic_source/source_repositories.dart`.
 * - CRUD with [normalizeVeneraUrl] validation, blank-name and duplicate-URL guards.
 * - `enabled` is a LOCAL-ONLY extension (no upstream field) kept out of sync payloads.
 * - Origins track install source (`file`/`url`/`repository`) for same-key disambiguation.
 * - Removing a repository snapshots its name onto linked origins (upstream behavior).
 */
class VeneraRepositoryStore {
    private val repositories = linkedMapOf<String, VeneraRepository>()
    private val origins = mutableMapOf<String, VeneraOrigin>()
    private var revision = 0

    fun revision(): Int = revision

    fun all(): List<VeneraRepository> = repositories.values.toList()

    fun find(id: String?): VeneraRepository? = id?.let { repositories[it] }

    fun add(name: String, url: String): VeneraRepository {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) throw VeneraCatalogError.InvalidCatalog(IllegalArgumentException("missing repository name"))
        val normalized = normalizeVeneraUrl(url)
        if (repositories.values.any { it.url == normalized }) {
            throw IllegalStateException("duplicate repository url: $normalized")
        }
        val repo = VeneraRepository(
            id = Uuid.random().toString(),
            name = trimmedName,
            url = normalized,
        )
        repositories[repo.id] = repo
        revision++
        return repo
    }

    fun update(id: String, name: String, url: String): VeneraRepository {
        val current = repositories[id] ?: throw NoSuchElementException("missing repository: $id")
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) throw IllegalArgumentException("missing repository name")
        val normalized = normalizeVeneraUrl(url)
        if (repositories.values.any { it.id != id && it.url == normalized }) {
            throw IllegalStateException("duplicate repository url: $normalized")
        }
        val updated = current.copy(name = trimmedName, url = normalized)
        repositories[id] = updated
        revision++
        return updated
    }

    fun remove(id: String): VeneraRepository {
        val current = repositories.remove(id) ?: throw NoSuchElementException("missing repository: $id")
        // Snapshot live name onto linked origins (upstream remove behavior).
        for ((key, origin) in origins.toMap()) {
            if (origin.repositoryId == id) {
                origins[key] = origin.copy(repositoryName = current.name)
            }
        }
        revision++
        return current
    }

    fun setEnabled(id: String, enabled: Boolean): VeneraRepository {
        val current = repositories[id] ?: throw NoSuchElementException("missing repository: $id")
        if (current.enabled == enabled) return current
        val updated = current.copy(enabled = enabled)
        repositories[id] = updated
        revision++
        return updated
    }

    fun originFor(key: String): VeneraOrigin? = origins[key]

    fun setOrigin(key: String, origin: VeneraOrigin?) {
        if (origin == null) origins.remove(key) else origins[key] = origin
        revision++
    }

    /** Same-key disambiguation: exact previous URL wins, else single, else throw. */
    fun entryFor(key: String, entries: List<VeneraCatalogEntry>): VeneraCatalogEntry {
        val previousUrl = originFor(key)?.url
        return selectCatalogEntry(key, entries, previousUrl)
    }

    suspend fun preview(client: HttpClient, repositoryId: String): VeneraCatalog {
        val repo = find(repositoryId) ?: throw NoSuchElementException("missing repository: $repositoryId")
        return loadVeneraCatalog(client, repo.url)
    }

    suspend fun previewUrl(client: HttpClient, url: String): VeneraCatalog {
        return loadVeneraCatalog(client, normalizeVeneraUrl(url))
    }
}
