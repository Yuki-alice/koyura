package app.koyura.app.history

import app.koyura.app.browse.BrowseServiceException

/** In-memory [HistoryService] used by tests. */
class FakeHistoryService(
    var entries: List<HistoryEntry> = emptyList(),
) : HistoryService {
    var loadCalls = 0
    var failNext: String? = null

    /** (sourceKey, workId) pairs passed to [deleteEntry], in order. */
    val deletedKeys = mutableListOf<Pair<String, String>>()

    /** Number of [clearAll] calls. */
    var clearCalls = 0

    override suspend fun loadHistory(): List<HistoryEntry> {
        loadCalls += 1
        failNext?.let {
            failNext = null
            throw BrowseServiceException(it)
        }
        return entries
    }

    override suspend fun deleteEntry(sourceKey: String, workId: String) {
        deletedKeys += sourceKey to workId
        entries = entries.filterNot { it.sourceKey == sourceKey && it.workId == workId }
    }

    override suspend fun clearAll() {
        clearCalls += 1
        entries = emptyList()
    }
}

fun historyEntry(
    sourceKey: String = "example_source",
    workId: String = "demo",
    chapterId: String? = "ep_2",
    pageKey: String = "https://example.invalid/3.png",
    pageIndex: Int = 2,
    workTitle: String? = "星图",
    chapterTitle: String? = "第二章",
    readAtMillis: Long? = 1_794_916_800_000,
    cover: String = "",
) = HistoryEntry(
    sourceKey = sourceKey,
    workId = workId,
    chapterId = chapterId,
    pageKey = pageKey,
    pageIndex = pageIndex,
    workTitle = workTitle,
    chapterTitle = chapterTitle,
    readAtMillis = readAtMillis,
    cover = cover,
)
