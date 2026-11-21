package app.koyura.app.navigation

import kotlinx.serialization.Serializable

/** Fullscreen comic reader for one chapter ([chapterId] null for works without chapters). */
@Serializable
data class ComicReaderRoute(
    val sourceKey: String,
    val workId: String,
    val chapterId: String? = null,
    val title: String = "",
    val chapterIds: List<String> = emptyList(),
    val chapterTitles: List<String> = emptyList(),
) : ReaderKey
