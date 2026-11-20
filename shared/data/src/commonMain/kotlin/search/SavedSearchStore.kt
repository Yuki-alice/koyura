package app.koyura.data.search

import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.extension.Saved_search
import app.koyura.domain.extension.SavedSearch
import app.koyura.domain.extension.SavedSearches
import app.koyura.domain.extension.SearchOption
import app.koyura.domain.extension.SearchOptionsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class SavedSearchStore(driver: SqlDriver) : SavedSearches {
    private val db = ExtensionDb(driver)
    private val queries = db.sourceSearchQueries

    override suspend fun list(sourceKey: String): List<SavedSearch> = withContext(Dispatchers.IO) {
        queries.selectSavedSearches(sourceKey).executeAsList().map { it.toDomain() }
    }

    override suspend fun get(id: Long): SavedSearch? = withContext(Dispatchers.IO) {
        queries.selectSavedSearch(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun create(sourceKey: String, name: String, query: String, options: SearchOptionsSnapshot): SavedSearch =
        withContext(Dispatchers.IO) {
            require(sourceKey.isNotBlank() && name.isNotBlank())
            db.transactionWithResult {
                queries.insertSavedSearch(sourceKey, name.trim(), query, Json.encodeToString(options))
                queries.selectSavedSearch(queries.lastSavedSearchId().executeAsOne()).executeAsOne().toDomain()
            }
        }

    override suspend fun update(id: Long, name: String, query: String, options: SearchOptionsSnapshot): SavedSearch? =
        withContext(Dispatchers.IO) {
            require(name.isNotBlank())
            db.transactionWithResult {
                queries.updateSavedSearch(name.trim(), query, Json.encodeToString(options), id)
                queries.selectSavedSearch(id).executeAsOneOrNull()?.toDomain()
            }
        }

    override suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        db.transactionWithResult {
            if (queries.selectSavedSearch(id).executeAsOneOrNull() == null) return@transactionWithResult false
            queries.deleteSavedSearch(id)
            true
        }
    }

    suspend fun current(sourceKey: String, options: List<SearchOption>): SearchOptionsSnapshot? = withContext(Dispatchers.IO) {
        val stored = queries.selectCurrentOptions(sourceKey).executeAsOneOrNull()
            ?.let { Json.decodeFromString<CurrentOptions>(it) }
        stored?.snapshot?.takeIf { stored.schema == options }
    }

    suspend fun setCurrent(sourceKey: String, options: List<SearchOption>, snapshot: SearchOptionsSnapshot) =
        withContext(Dispatchers.IO) {
            queries.saveCurrentOptions(sourceKey, Json.encodeToString(CurrentOptions(options, snapshot)))
        }

    private fun Saved_search.toDomain() =
        SavedSearch(id, source_key, name, query_text, Json.decodeFromString<SearchOptionsSnapshot>(option_snapshot))

    @Serializable
    private data class CurrentOptions(val schema: List<SearchOption>, val snapshot: SearchOptionsSnapshot)
}
