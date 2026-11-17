package app.koyura.features.library

import kotlin.random.Random

/**
 * 书架列表的纯视图逻辑：搜索 / 分类 / 筛选 / 排序 / 分组。
 *
 * 无状态、可单测；[LibrarySession] 与 UI 共用同一实现，保证“所见即所得”：
 * 书架搜索、筛选、排序作用于真实数据，同一函数既驱动界面也接受断言。
 */

private fun LibraryEntry.matchesQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return title.contains(needle, ignoreCase = true) ||
        workId.contains(needle, ignoreCase = true) ||
        sourceName.contains(needle, ignoreCase = true)
}

private fun LibraryTriState.keep(isMatch: Boolean): Boolean = when (this) {
    LibraryTriState.DISABLED -> true
    LibraryTriState.INCLUDE -> isMatch
    LibraryTriState.EXCLUDE -> !isMatch
}

private fun LibraryEntry.matchesFilters(filters: LibraryFilters): Boolean {
    if (!filters.downloaded.keep(downloadedCount > 0)) return false
    if (!filters.unread.keep(unreadCount > 0)) return false
    if (!filters.started.keep(hasStarted)) return false
    if (!filters.bookmarked.keep(bookmarked)) return false
    if (!filters.completed.keep(status == LibraryStatus.COMPLETED)) return false
    if (!filters.lewd.keep(isNsfw)) return false
    return true
}

/** 随机排序比较子：同一种子同输入必同输出（单测断言确定性用）。 */
internal fun randomTieKey(key: LibraryEntryKey, seed: Long): Long {
    var hash = seed xor 0x9E3779B97F4A7C15u.toLong()
    fun mix(part: String) {
        for (char in part) {
            hash = hash xor char.code.toLong()
            hash *= 0x100000001B3L
        }
    }
    mix(key.sourceKey)
    mix(key.kind.name)
    mix(key.workId)
    return hash
}

/**
 * 对条目应用搜索、分类归属与筛选，并按偏好排序。
 *
 * @param categoryId null = 全部；否则只留归属该分类的条目。隐藏分类由调用方
 *   按 [LibraryPrefs.showHiddenCategories] 决定是否提供该页签，这里不二次过滤。
 * @param randomJitter 当排序为 RANDOM 且种子相同时，用确定性哈希打散；
 *   上游随机排序每次重排，本实现以种子保证可复现（单测可断言）。
 */
fun applyLibraryView(
    entries: List<LibraryEntry>,
    links: Map<LibraryEntryKey, Set<Long>>,
    query: String,
    categoryId: Long?,
    filters: LibraryFilters,
    sortType: LibrarySortType,
    ascending: Boolean,
    randomSeed: Long = 0L,
): List<LibraryEntry> {
    val filtered = entries.filter { entry ->
        val inCategory = categoryId == null || (links[entry.key]?.contains(categoryId) == true)
        inCategory && entry.matchesQuery(query) && entry.matchesFilters(filters)
    }
    if (sortType == LibrarySortType.RANDOM) {
        val ordered = filtered.sortedBy { randomTieKey(it.key, randomSeed) }
        return if (ascending) ordered else ordered.reversed()
    }
    val comparator: Comparator<LibraryEntry> = when (sortType) {
        LibrarySortType.ALPHABETICAL ->
            compareBy({ it.displayTitle.lowercase() }, { it.sourceKey }, { it.workId })
        LibrarySortType.LAST_READ ->
            compareBy({ it.lastReadAtMillis ?: Long.MIN_VALUE }, { it.displayTitle.lowercase() })
        LibrarySortType.LAST_UPDATE_CHECK ->
            compareBy({ it.lastUpdateCheckMillis ?: Long.MIN_VALUE }, { it.displayTitle.lowercase() })
        LibrarySortType.UNREAD_COUNT ->
            compareBy({ it.unreadCount }, { it.displayTitle.lowercase() })
        LibrarySortType.TOTAL_CHAPTERS ->
            compareBy({ it.totalChapters }, { it.displayTitle.lowercase() })
        LibrarySortType.LATEST_CHAPTER ->
            compareBy({ it.latestChapterMillis ?: Long.MIN_VALUE }, { it.displayTitle.lowercase() })
        LibrarySortType.CHAPTER_FETCH_DATE ->
            compareBy({ it.chapterFetchMillis ?: Long.MIN_VALUE }, { it.displayTitle.lowercase() })
        LibrarySortType.DATE_ADDED ->
            compareBy({ it.favoriteAtMillis }, { it.displayTitle.lowercase() })
        LibrarySortType.RANDOM -> error("unreachable")
    }
    val ordered = filtered.sortedWith(comparator)
    return if (ascending) ordered else ordered.asReversed()
}

/** 分组后的节：标题 + 该节条目（节内保持传入顺序，不再二次排序）。 */
data class LibrarySection(
    val title: String,
    val entries: List<LibraryEntry>,
)

/**
 * 按 [groupBy] 切分已排序条目。CATEGORY 分组下调用方按页签一次只看一个分类，
 * 因此 CATEGORY 视为不切分（返回单一节）；SOURCE / STATUS 返回组头节。
 */
fun groupLibraryEntries(
    ordered: List<LibraryEntry>,
    groupBy: LibraryGroupBy,
): List<LibrarySection> {
    if (ordered.isEmpty()) return emptyList()
    return when (groupBy) {
        LibraryGroupBy.CATEGORY -> listOf(LibrarySection("", ordered))
        LibraryGroupBy.SOURCE -> ordered.groupBy { it.sourceName.ifBlank { it.sourceKey } }
            // toSortedMap 是 JVM 专属，Kotlin/Native（iOS）无此扩展：改用 entries 明式排序。
            // 大小写无关比较下同名不同大小写的来源（如 "Foo"/"FOO"）比较结果为 0，
            // sortedWith 稳定排序保留其原始相对顺序，两组都不丢失作品。
            .entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
            .map { (name, items) -> LibrarySection(name, items) }
        LibraryGroupBy.STATUS -> {
            val rank = mapOf(
                LibraryStatus.ONGOING to 0,
                LibraryStatus.COMPLETED to 1,
                LibraryStatus.UNKNOWN to 2,
            )
            ordered.groupBy { it.status }
                .entries.sortedWith(compareBy({ rank[it.key] ?: Int.MAX_VALUE }))
                .map { (status, items) -> LibrarySection(status.name, items) }
        }
    }
}

/**
 * 网格列数：列表与全景模式固定单列，其余网格模式取偏好列数并夹到 1..6。
 * 空闲列数滑杆（显示页）直接写偏好，本函数只做钳制。
 */
fun libraryColumnsFor(mode: LibraryDisplayMode, columnsPref: Int): Int = when (mode) {
    LibraryDisplayMode.LIST,
    LibraryDisplayMode.COMFORTABLE_GRID_PANORAMA,
    -> 1
    LibraryDisplayMode.COMPACT_GRID,
    LibraryDisplayMode.COMFORTABLE_GRID,
    LibraryDisplayMode.COVER_ONLY_GRID,
    -> columnsPref.coerceIn(1, 6)
}
