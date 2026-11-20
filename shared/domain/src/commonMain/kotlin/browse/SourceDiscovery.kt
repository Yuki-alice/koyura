package app.koyura.domain.browse

import kotlinx.serialization.Serializable

@Serializable
data class SourceGroup(val id: Long, val name: String)

data class DiscoverySource(
    val key: String,
    val name: String,
    val canSearch: Boolean,
    val pinned: Boolean,
    val hidden: Boolean,
    val nsfw: Boolean,
    val groupIds: Set<Long>,
)

data class SourceDiscovery(
    val sources: List<DiscoverySource>,
    val groups: List<SourceGroup>,
    val showNsfw: Boolean,
    val preferredSourceKey: String?,
    val defaultSourceKey: String?,
)
