package app.koyura.app.history

import app.koyura.data.history.HistoryStore
import app.koyura.domain.library.LibraryRepository
import app.koyura.domain.library.WorkKind
import kotlin.time.Clock

/**
 * 生产历史服务：SQLite 持久化 + 与书架共享收藏记录。
 *
 * - 列表只读本地 `reading_progress`，封面按需从已提交的书架行取快照 URL
 *   （未收藏作品封面为空，回退占位；字节经共用封面管线加载）。
 * - 单条删除与清空直写 `reading_progress`，重启后仍生效；书架收藏、分类与
 *   章节进度行独立存放，不受影响。
 * - 收藏心直接写书架三元主键行，与详情页、书架页同一记录。
 */
class VeneraHistoryService(
    private val store: HistoryStore,
    private val library: LibraryRepository,
) : HistoryService {
    override suspend fun loadHistory(): List<HistoryEntry> {
        val covers = library.listEntries().associate { entry ->
            (entry.manga.sourceKey to entry.manga.workId) to entry.manga.coverUrl
        }
        return store.latestEntries().map { record ->
            record.toEntry().copy(cover = covers[record.sourceKey to record.workId].orEmpty())
        }
    }

    override suspend fun deleteEntry(sourceKey: String, workId: String) {
        store.deleteEntry(sourceKey, workId)
    }

    override suspend fun clearAll() {
        store.clearAll()
    }

    override suspend fun setFavorite(
        sourceKey: String,
        workId: String,
        title: String,
        coverUrl: String,
        sourceName: String?,
        favorite: Boolean,
    ): Boolean {
        if (favorite) {
            library.addToLibrary(
                sourceKey = sourceKey,
                kind = WorkKind.COMIC,
                workId = workId,
                title = title,
                coverUrl = coverUrl,
                sourceName = sourceName,
                addedAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
        } else {
            library.removeFromLibrary(sourceKey, WorkKind.COMIC, workId)
        }
        return true
    }

    override suspend fun favoriteKeys(): Set<Pair<String, String>> =
        library.listEntries().map { it.manga.sourceKey to it.manga.workId }.toSet()
}
