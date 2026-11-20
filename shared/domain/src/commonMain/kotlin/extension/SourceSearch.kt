package app.koyura.domain.extension

import kotlinx.serialization.Serializable

@Serializable
enum class SearchOptionType { SELECT, MULTI_SELECT, DROPDOWN }

@Serializable
data class SearchOptionChoice(val value: String, val label: String)

@Serializable
sealed interface SearchOptionValue {
    @Serializable
    data class Single(val value: String?) : SearchOptionValue

    @Serializable
    data class Multiple(val values: List<String>) : SearchOptionValue
}

@Serializable
data class SearchOption(
    val label: String,
    val type: SearchOptionType,
    val choices: List<SearchOptionChoice>,
    val default: SearchOptionValue,
)

@Serializable
data class SearchOptionsSnapshot(val values: List<SearchOptionValue> = emptyList())

data class SourceSearchOptions(val options: List<SearchOption>, val current: SearchOptionsSnapshot)

data class SavedSearch(
    val id: Long,
    val sourceKey: String,
    val name: String,
    val query: String,
    val options: SearchOptionsSnapshot,
)

interface SavedSearches {
    suspend fun list(sourceKey: String): List<SavedSearch>
    suspend fun get(id: Long): SavedSearch?
    suspend fun create(sourceKey: String, name: String, query: String, options: SearchOptionsSnapshot): SavedSearch
    suspend fun update(id: Long, name: String, query: String, options: SearchOptionsSnapshot): SavedSearch?
    suspend fun delete(id: Long): Boolean
}
