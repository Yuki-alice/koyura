package app.koyura.app.browse.venera

import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.WorkSummary

/** A numbered page has a maximum; a cursor page supplies the token to send with the next request. */
data class SourceSearchPage(
    val works: List<WorkSummary>,
    val page: Int,
    val maxPage: Int?,
    val next: String? = null,
) {
    val hasMore: Boolean get() = next != null || (maxPage != null && page < maxPage)
}

/** Extends the numbered Browse contract with cursor support for production sources. */
suspend fun BrowseService.searchWorks(
    sourceKey: String,
    query: String,
    page: Int,
    next: String?,
): SourceSearchPage {
    require(page > 0)
    if (this is VeneraBrowseService) return searchWorksPage(sourceKey, query, page, next)
    require(next == null) { "This browse service does not support cursor pagination" }
    val result = searchWorks(sourceKey, query, page)
    return SourceSearchPage(result.works, page, result.maxPage)
}
