package app.koyura.data.library

import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.extension.Library_category
import app.koyura.data.extension.Library_manga
import app.koyura.data.extension.Reading_progress
import app.koyura.domain.extension.ReadingProgress
import app.koyura.domain.library.LibraryCategory
import app.koyura.domain.library.LibraryChapter
import app.koyura.domain.library.LibraryEntry
import app.koyura.domain.library.LibraryManga
import app.koyura.domain.library.LibraryMigrationReport
import app.koyura.domain.library.LibraryQuery
import app.koyura.domain.library.LibraryRepository
import app.koyura.domain.library.LibrarySort
import app.koyura.domain.library.WorkKind
import kotlin.random.Random

/**
 * 书架仓储实现（数据所有：sq + 迁移 + 仓储实现）。
 *
 * - 收藏身份为三元主键 `(sourceKey, kind, workId)`，`kind` 存枚举名；
 *   漫画对齐轮只产生 `COMIC` 行，[addToLibrary] 拒绝 `NOVEL`（[WorkKind] 本身仍可建模）。
 * - 收藏按主键 `INSERT OR IGNORE` + 回读：重复收藏返回原行（保留首次收藏时间）；
 *   快照刷新走 [refreshSnapshot]（更新检查等场景显式调用，不在重复收藏时偷换）。
 * - 未读数/已下载数/继续阅读章为派生值：本轮只有 `reading_progress` 可联查，
 *   [latestProgress] 返回同作品最新一行（`chapterId` 空字符串映射回 null，
 *   chapterless 作品即此语义）；章节表落地后再扩展派生口径。
 * - 源失效（未安装/已卸载）时收藏仍可见：书架行独立于 `extensions` 表，
 *   [listEntries] 不做任何源存在性过滤。
 * - 迁移/合并为单事务（`ExtensionDb.transaction`，异常即回滚）：新条目、归属映射、
 *   进度复制要么全提交要么全回滚，旧条目与旧进度行始终保留（用户确认清理前不删）。
 *
 * 并发说明：与 [app.koyura.data.reading.ComicReadingService] 的进度写并发时，
 * 以最后提交者为准；迁移类操作应在无前台阅读写入时执行（与扩展安装事务同要求）。
 */
class LibraryStore(driver: SqlDriver) : LibraryRepository {
    private val db = ExtensionDb(driver)
    private val queries get() = db.extensionDbQueries

    private fun seeded(): app.koyura.data.extension.ExtensionDbQueries {
        queries.seedDefaultCategory()
        return queries
    }

    override suspend fun addToLibrary(
        sourceKey: String,
        kind: WorkKind,
        workId: String,
        title: String,
        coverUrl: String,
        sourceName: String?,
        addedAtMillis: Long,
    ): LibraryManga {
        require(kind == WorkKind.COMIC) { "NOVEL rows are not written in the manga round" }
        val q = seeded()
        q.insertLibraryManga(sourceKey, kind.name, workId, title, coverUrl, sourceName, addedAtMillis, null)
        return q.selectLibraryByKey(sourceKey, kind.name, workId).executeAsOne().toManga()
    }

    /** 显式刷新标题/封面/源名/更新检查快照（不改变收藏时间与主键）。 */
    suspend fun refreshSnapshot(
        sourceKey: String,
        kind: WorkKind,
        workId: String,
        title: String,
        coverUrl: String,
        sourceName: String?,
        lastUpdateCheckMillis: Long?,
    ) {
        seeded().updateLibrarySnapshot(title, coverUrl, sourceName, lastUpdateCheckMillis, sourceKey, kind.name, workId)
    }

    override suspend fun removeFromLibrary(sourceKey: String, kind: WorkKind, workId: String) {
        val q = seeded()
        db.transaction {
            q.deleteMappingsForWork(sourceKey, kind.name, workId)
            q.deleteChaptersForWork(sourceKey, kind.name, workId)
            q.deleteLibraryManga(sourceKey, kind.name, workId)
        }
        // reading_progress 行保留：历史与“继续阅读”派生仍可读，禁止静默删库。
    }

    override suspend fun listEntries(query: LibraryQuery): List<LibraryEntry> {
        val q = seeded()
        val categories = q.selectCategoriesOrdered().executeAsList()
        val hiddenIds = categories.filter { it.hidden == 1L }.map { it.id }.toSet()
        val mappings = q.selectAllMappings().executeAsList().groupBy(
            keySelector = { Triple(it.source_key, it.kind, it.work_id) },
            valueTransform = { it.category_id },
        )
        var entries = q.selectLibraryAll().executeAsList().map { row ->
            LibraryEntry(row.toManga(), (mappings[Triple(row.source_key, row.kind, row.work_id)] ?: emptyList()).sorted())
        }
        query.categoryId?.let { only ->
            entries = entries.filter { only in it.categoryIds }
        } ?: run {
            if (!query.includeHiddenCategories) {
                entries = entries.filter { entry ->
                    entry.categoryIds.isEmpty() || entry.categoryIds.any { it !in hiddenIds }
                }
            }
        }
        val needle = query.search.trim().lowercase()
        if (needle.isNotEmpty()) {
            entries = entries.filter { entry ->
                entry.manga.title.lowercase().contains(needle) ||
                    entry.manga.workId.lowercase().contains(needle) ||
                    (entry.manga.sourceName ?: "").lowercase().contains(needle)
            }
        }
        entries = when (query.sort) {
            LibrarySort.NAME -> entries.sortedWith(compareBy({ it.manga.title.lowercase() }, { it.manga.workId }))
            LibrarySort.DATE_ADDED -> entries.sortedWith(compareBy({ it.manga.addedAtMillis }, { it.manga.workId }))
            LibrarySort.LAST_CHECKED -> entries.sortedWith(
                compareBy<LibraryEntry> { it.manga.lastUpdateCheckMillis == null }
                    .thenBy { it.manga.lastUpdateCheckMillis ?: Long.MAX_VALUE }
                    .thenBy { it.manga.workId },
            )
            LibrarySort.RANDOM -> entries.shuffled(Random(query.randomSeed))
        }
        if (!query.ascending) entries = entries.reversed()
        return entries
    }

    override suspend fun listCategories(includeHidden: Boolean): List<LibraryCategory> {
        val q = seeded()
        return q.selectCategoriesOrdered().executeAsList()
            .map { it.toCategory() }
            .filter { includeHidden || !it.hidden }
    }

    override suspend fun createCategory(name: String): LibraryCategory {
        require(name.isNotBlank()) { "Category name must not be blank" }
        val q = seeded()
        val maxOrder = q.selectCategoriesOrdered().executeAsList().maxOfOrNull { it.sort_order } ?: -1L
        q.insertCategory(name, maxOrder + 1, 0, 0)
        return q.selectCategoriesOrdered().executeAsList().last().toCategory()
    }

    override suspend fun renameCategory(id: Long, name: String) {
        require(name.isNotBlank()) { "Category name must not be blank" }
        seeded().updateCategoryName(name, id)
    }

    override suspend fun deleteCategory(id: Long) {
        val q = seeded()
        db.transaction {
            // 只删归属映射：作品行保留，转为无分类。禁止静默删库。
            q.deleteMappingsForCategory(id)
            q.deleteCategory(id)
        }
    }

    override suspend fun reorderCategories(orderedIds: List<Long>) {
        val q = seeded()
        db.transaction {
            orderedIds.forEachIndexed { index, id ->
                q.updateCategoryOrder(index.toLong(), id)
            }
        }
    }

    override suspend fun setCategoryHidden(id: Long, hidden: Boolean) {
        seeded().updateCategoryHidden(if (hidden) 1 else 0, id)
    }

    override suspend fun setWorkCategories(
        sourceKey: String,
        kind: WorkKind,
        workId: String,
        categoryIds: List<Long>,
    ) {
        val q = seeded()
        val known = q.selectCategoriesOrdered().executeAsList().map { it.id }.toSet()
        require(categoryIds.all { it in known }) { "Unknown category id in $categoryIds" }
        db.transaction {
            q.deleteMappingsForWork(sourceKey, kind.name, workId)
            categoryIds.distinct().forEach { q.insertMapping(sourceKey, kind.name, workId, it) }
        }
    }

    override suspend fun batchAssignToCategory(keys: List<Triple<String, WorkKind, String>>, categoryId: Long) {
        val q = seeded()
        require(q.selectCategoryById(categoryId).executeAsOneOrNull() != null) { "Unknown category id $categoryId" }
        db.transaction {
            keys.forEach { (sourceKey, kind, workId) ->
                q.insertMapping(sourceKey, kind.name, workId, categoryId)
            }
        }
    }

    override suspend fun getPref(key: String): String? =
        seeded().selectPref(key).executeAsOneOrNull()?.pref_value

    override suspend fun setPref(key: String, value: String) {
        seeded().upsertPref(key, value)
    }

    override suspend fun deletePref(key: String) {
        seeded().deletePref(key)
    }

    suspend fun allPrefs(): Map<String, String> =
        seeded().selectAllPrefs().executeAsList().associate { it.pref_key to it.pref_value }

    override suspend fun syncChapters(
        sourceKey: String,
        kind: WorkKind,
        workId: String,
        chapters: List<LibraryChapter>,
    ) {
        val q = seeded()
        db.transaction {
            val existing = q.selectChaptersForWork(sourceKey, kind.name, workId)
                .executeAsList().associate { it.chapter_id to (it.is_read == 1L) }
            // 全量同步：保留同 ID 已读态，新章默认未读；消失章节随目录移除，
            // 阅读进度（reading_progress）与历史隐藏标记原样保留。
            q.deleteChaptersForWork(sourceKey, kind.name, workId)
            for ((index, chapter) in chapters.withIndex()) {
                val kept = existing[chapter.chapterId] ?: false
                q.upsertChapter(
                    source_key = sourceKey,
                    kind = kind.name,
                    work_id = workId,
                    chapter_id = chapter.chapterId,
                    title = chapter.title,
                    sort_index = index.toLong(),
                    is_read = if (kept) 1 else 0,
                )
            }
        }
    }

    override suspend fun listChapters(sourceKey: String, kind: WorkKind, workId: String): List<LibraryChapter> =
        seeded().selectChaptersForWork(sourceKey, kind.name, workId).executeAsList().map { row ->
            LibraryChapter(
                sourceKey = row.source_key,
                kind = WorkKind.valueOf(row.kind),
                workId = row.work_id,
                chapterId = row.chapter_id,
                title = row.title,
                sortIndex = row.sort_index.toInt(),
                isRead = row.is_read == 1L,
            )
        }

    override suspend fun markChaptersRead(
        sourceKey: String,
        kind: WorkKind,
        workId: String,
        chapterIds: List<String>,
        read: Boolean,
    ) {
        val q = seeded()
        db.transaction {
            if (chapterIds.isEmpty()) {
                q.updateChaptersReadForWork(if (read) 1 else 0, sourceKey, kind.name, workId)
            } else {
                for (chapterId in chapterIds.distinct()) {
                    q.updateChapterRead(if (read) 1 else 0, sourceKey, kind.name, workId, chapterId)
                }
            }
        }
    }

    override suspend fun markChapterRead(
        sourceKey: String,
        kind: WorkKind,
        workId: String,
        chapterId: String,
        read: Boolean,
    ) {
        seeded().updateChapterRead(if (read) 1 else 0, sourceKey, kind.name, workId, chapterId)
    }

    /** 章节派生计数：总数与未读数（无目录时均为 0，调用方按章后备语义展示）。 */
    suspend fun chapterCounts(sourceKey: String, kind: WorkKind, workId: String): Pair<Int, Int> {
        val rows = seeded().selectChaptersForWork(sourceKey, kind.name, workId).executeAsList()
        val total = rows.size
        val unread = rows.count { it.is_read != 1L }
        return total to unread
    }

    /**
     * 同作品最新进度（派生“继续阅读”用）。
     *
     * `chapter_id` 空字符串映射回 null，与既有 `HistoryStore` 同语义；
     * 源失效时同样可读（只读进度表，不碰扩展运行时）。
     */
    suspend fun latestProgress(sourceKey: String, workId: String): ReadingProgress? =
        seeded().selectLatestProgress(sourceKey, workId).executeAsOneOrNull()?.toProgress()

    override suspend fun migrateSource(oldSourceKey: String, newSourceKey: String): LibraryMigrationReport {
        if (oldSourceKey == newSourceKey) return LibraryMigrationReport(0, 0, 0, 0)
        val q = seeded()
        var copiedEntries = 0
        var copiedMappings = 0
        var copiedProgress = 0
        var mergedProgress = 0
        db.transaction {
            for (row in q.selectLibraryBySource(oldSourceKey).executeAsList()) {
                if (q.selectLibraryByKey(newSourceKey, row.kind, row.work_id).executeAsOneOrNull() == null) {
                    q.insertLibraryManga(
                        newSourceKey, row.kind, row.work_id, row.title,
                        row.cover_url, row.source_name, row.added_at, row.last_update_check,
                    )
                    copiedEntries++
                }
            }
            for (mapping in q.selectMappingsBySource(oldSourceKey).executeAsList()) {
                if (q.selectLibraryByKey(newSourceKey, mapping.kind, mapping.work_id).executeAsOneOrNull() == null) {
                    continue
                }
                val exists = q.selectMappingsForWork(newSourceKey, mapping.kind, mapping.work_id)
                    .executeAsList().any { it.category_id == mapping.category_id }
                if (!exists) {
                    q.insertMapping(newSourceKey, mapping.kind, mapping.work_id, mapping.category_id)
                    copiedMappings++
                }
            }
            for (progress in q.selectProgressBySource(oldSourceKey).executeAsList()) {
                val current = q.selectProgress(newSourceKey, progress.work_id, progress.chapter_id).executeAsOneOrNull()
                if (current == null) {
                    q.upsertProgressWithMeta(
                        newSourceKey, progress.work_id, progress.chapter_id,
                        progress.page_key, progress.page_index,
                        progress.read_at, progress.work_title, progress.chapter_title,
                    )
                    copiedProgress++
                } else if (progressIsNewer(progress, current)) {
                    q.upsertProgressWithMeta(
                        newSourceKey, progress.work_id, progress.chapter_id,
                        progress.page_key, progress.page_index,
                        progress.read_at, progress.work_title, progress.chapter_title,
                    )
                    mergedProgress++
                }
            }
            for (row in q.selectLibraryBySource(oldSourceKey).executeAsList()) {
                for (chapter in q.selectChaptersForWork(oldSourceKey, row.kind, row.work_id).executeAsList()) {
                    if (q.selectChapterById(newSourceKey, chapter.kind, chapter.work_id, chapter.chapter_id)
                            .executeAsOneOrNull() == null
                    ) {
                        q.upsertChapter(
                            newSourceKey, chapter.kind, chapter.work_id, chapter.chapter_id,
                            chapter.title, chapter.sort_index, chapter.is_read,
                        )
                    }
                }
            }
            // 旧 key 的书架行、归属映射与进度行全部保留：用户确认清理前不删。
        }
        return LibraryMigrationReport(copiedEntries, copiedMappings, copiedProgress, mergedProgress)
    }

    override suspend fun mergeEntry(
        from: Triple<String, WorkKind, String>,
        to: Triple<String, WorkKind, String>,
    ): LibraryMigrationReport {
        val (fromSource, fromKind, fromWork) = from
        val (toSource, toKind, toWork) = to
        val q = seeded()
        var copiedEntries = 0
        var copiedMappings = 0
        var copiedProgress = 0
        var mergedProgress = 0
        db.transaction {
            val source = q.selectLibraryByKey(fromSource, fromKind.name, fromWork).executeAsOneOrNull()
                ?: throw IllegalArgumentException("Unknown library entry $from")
            if (q.selectLibraryByKey(toSource, toKind.name, toWork).executeAsOneOrNull() == null) {
                q.insertLibraryManga(
                    toSource, toKind.name, toWork, source.title,
                    source.cover_url, source.source_name, source.added_at, source.last_update_check,
                )
                copiedEntries++
            }
            // 分类归属取并集。
            for (mapping in q.selectMappingsForWork(fromSource, fromKind.name, fromWork).executeAsList()) {
                val exists = q.selectMappingsForWork(toSource, toKind.name, toWork)
                    .executeAsList().any { it.category_id == mapping.category_id }
                if (!exists) {
                    q.insertMapping(toSource, toKind.name, toWork, mapping.category_id)
                    copiedMappings++
                }
            }
            // 进度以同章节较新的 read_at / pageIndex 为准；历史追加不合并去重体现在：
            // 不同章节各自复制，只在同 (workId, chapterId) 冲突时取新者。
            for (progress in q.selectProgressByWork(fromSource, fromWork).executeAsList()) {
                val current = q.selectProgress(toSource, toWork, progress.chapter_id).executeAsOneOrNull()
                if (current == null) {
                    q.upsertProgressWithMeta(
                        toSource, toWork, progress.chapter_id,
                        progress.page_key, progress.page_index,
                        progress.read_at, progress.work_title, progress.chapter_title,
                    )
                    copiedProgress++
                } else if (progressIsNewer(progress, current)) {
                    q.upsertProgressWithMeta(
                        toSource, toWork, progress.chapter_id,
                        progress.page_key, progress.page_index,
                        progress.read_at, progress.work_title, progress.chapter_title,
                    )
                    mergedProgress++
                }
            }
            for (chapter in q.selectChaptersForWork(fromSource, fromKind.name, fromWork).executeAsList()) {
                if (q.selectChapterById(toSource, toKind.name, toWork, chapter.chapter_id)
                        .executeAsOneOrNull() == null
                ) {
                    q.upsertChapter(
                        toSource, toKind.name, toWork, chapter.chapter_id,
                        chapter.title, chapter.sort_index, chapter.is_read,
                    )
                }
            }
            // 旧条目保留：失败回滚，已提交的合并也不删旧行。
        }
        return LibraryMigrationReport(copiedEntries, copiedMappings, copiedProgress, mergedProgress)
    }

    /** 较新者为准：先比 `read_at`（null 视为最旧），再比 `page_index`（大者为新）。 */
    private fun progressIsNewer(candidate: Reading_progress, current: Reading_progress): Boolean {
        val candidateTime = candidate.read_at ?: Long.MIN_VALUE
        val currentTime = current.read_at ?: Long.MIN_VALUE
        if (candidateTime != currentTime) return candidateTime > currentTime
        return candidate.page_index > current.page_index
    }

    private fun Library_manga.toManga() = LibraryManga(
        sourceKey = source_key,
        kind = WorkKind.valueOf(kind),
        workId = work_id,
        title = title,
        coverUrl = cover_url,
        sourceName = source_name,
        addedAtMillis = added_at,
        lastUpdateCheckMillis = last_update_check,
    )

    private fun Library_category.toCategory() = LibraryCategory(
        id = id,
        name = name,
        sortOrder = sort_order.toInt(),
        hidden = hidden == 1L,
        isDefault = is_default == 1L,
    )

    private fun Reading_progress.toProgress() = ReadingProgress(
        sourceKey = source_key,
        workId = work_id,
        chapterId = chapter_id.ifEmpty { null },
        pageKey = page_key,
        pageIndex = page_index.toInt(),
    )
}
