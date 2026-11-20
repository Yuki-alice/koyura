package app.koyura.domain.browse

import kotlinx.serialization.Serializable

/** Venera requires titles to be unique within a source. Indexes can change on upgrade. */
@Serializable
data class SourceFeedId(val sourceKey: String, val title: String)

data class SourceFeedItem(
    val id: SourceFeedId,
    val sourceName: String,
    val index: Int,
    val type: String,
    val numbered: Boolean,
    val cursor: Boolean,
    val enabled: Boolean,
) {
    val sourceKey: String get() = id.sourceKey
    val title: String get() = id.title
}
