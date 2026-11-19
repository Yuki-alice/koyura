package app.koyura.app.library

import app.koyura.data.history.HistoryStore
import app.koyura.domain.history.HistoryRecord
import app.koyura.domain.library.LibraryCategory as DomainLibraryCategory
import app.koyura.domain.library.LibraryEntry as DomainLibraryEntry
import app.koyura.domain.library.LibraryManga as DomainLibraryManga
import app.koyura.domain.library.LibraryPrefsKeys
import app.koyura.domain.library.LibraryQuery as DomainLibraryQuery
import app.koyura.domain.library.LibraryRepository as DomainLibraryRepository
import app.koyura.domain.library.WorkKind as DomainWorkKind
import app.koyura.features.library.LibraryCategory
import app.koyura.features.library.LibraryDisplayMode
import app.koyura.features.library.LibraryEntry
import app.koyura.features.library.LibraryEntryKey
import app.koyura.features.library.LibraryFilters
import app.koyura.features.library.LibraryGroupBy
import app.koyura.features.library.LibraryKind
import app.koyura.features.library.LibraryPrefs
import app.koyura.features.library.LibraryRepository as FeaturesLibraryRepository
import app.koyura.features.library.LibraryRepositoryException
import app.koyura.features.library.LibrarySnapshot
import app.koyura.features.library.LibrarySortType
import app.koyura.features.library.LibraryTriState
import app.koyura.features.library.NewLibraryEntry
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/**
 * 生产书架适配器：界面层 `features.LibraryRepository` 到数据层三元主键仓储的唯一桥。
 *
 * - 身份沿用 `(sourceKey, kind, workId)`：收藏/取消/归类全部调 domain 仓储并重读快照，
 *   以 SQLite 提交结果为准；重复收藏返回原行由 [DomainLibraryRepository] 保证。
 * - 继续阅读派生自 `reading_progress` 最新行（经 [HistoryStore.latestProgress]，
 *   忽略历史隐藏标记）；总章数与未读数派生自 `library_chapter`（无目录的老收藏为 0），
 *   下载计数待下载系统落地前恒为 0。
 * - [markAsRead] 逐部校验目录后写入 `library_chapter.is_read`，缺目录抛可恢复错误
 *   引导打开详情补齐；详情子集标记走 [markDetailChapters]。
 * - 偏好落 `library_prefs` 五键，格式见 [serializePrefs]/[parsePrefs]，缺键或坏值回退默认。
 * - 取图委托调用方注入的单次加载（生产为 `comic.onThumbnailLoad`），失败抛
 *   [LibraryRepositoryException]，空 URL 直接抛错以便调用方回退占位。
 */
class ProductionLibraryRepository(
    private val store: DomainLibraryRepository,
    private val history: HistoryStore,
    private val thumbnails: suspend (sourceKey: String, coverUrl: String) -> ByteArray,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : FeaturesLibraryRepository {

    override suspend fun load(): LibrarySnapshot = runRepo {
        val categories = store.listCategories(includeHidden = true)
        val entries = store.listEntries(DomainLibraryQuery(includeHiddenCategories = true))
        // 继续阅读派生读全量进度（忽略历史隐藏标记）：删除/清空历史隐藏的是
        // 列表展示，不影响书架与详情的续读位置。
        val progress = history.latestProgress()
        LibrarySnapshot(
            entries = entries.map { domain ->
                val (total, unread) = chapterCountsFor(domain)
                domain.toFeature(progress[domain.manga.sourceKey to domain.manga.workId], total, unread)
            },
            categories = categories.map { it.toFeature() },
            links = entries.associate { entry ->
                entry.manga.toFeatureKey() to entry.categoryIds.toSet()
            },
            prefs = parsePrefs { store.getPref(it) },
        )
    }

    private suspend fun chapterCountsFor(domain: DomainLibraryEntry): Pair<Int, Long> {
        val chapters = try {
            store.listChapters(domain.manga.sourceKey, domain.manga.kind, domain.manga.workId)
        } catch (_: Exception) {
            emptyList()
        }
        if (chapters.isEmpty()) return 0 to 0L
        val unread = chapters.count { !it.isRead }.toLong()
        return chapters.size to unread
    }

    /**
     * 详情目录同步：成功取得源目录后调用，保留同 ID 已读态，新章默认未读，
     * 无分章目录记单行（`chapterId = ""`，沿用 `reading_progress` 的空串语义），
     * 失败或取消不调用，保留上次有效目录。
     */
    suspend fun syncWorkDetail(
        sourceKey: String,
        kind: LibraryKind,
        workId: String,
        chapters: List<app.koyura.app.browse.WorkChapter>,
    ): Unit = runRepo {
        val domainKind = kind.toDomain()
        if (chapters.isEmpty()) {
            store.syncChapters(
                sourceKey, domainKind, workId,
                listOf(
                    app.koyura.domain.library.LibraryChapter(
                        sourceKey = sourceKey,
                        kind = domainKind,
                        workId = workId,
                        chapterId = "",
                        title = "",
                        sortIndex = 0,
                        isRead = false,
                    ),
                ),
            )
        } else {
            store.syncChapters(
                sourceKey, domainKind, workId,
                chapters.mapIndexed { index, chapter ->
                    app.koyura.domain.library.LibraryChapter(
                        sourceKey = sourceKey,
                        kind = domainKind,
                        workId = workId,
                        chapterId = chapter.id,
                        title = chapter.title,
                        sortIndex = index,
                        isRead = false,
                    )
                },
            )
        }
    }

    override suspend fun chapterReadStates(key: LibraryEntryKey): Map<String, Boolean> = runRepo {
        // 未收藏（无目录行）即空映射，详情回退到“仅最新进度章标读”的旧语义。
        try {
            store.listChapters(key.sourceKey, key.kind.toDomain(), key.workId)
                .associate { it.chapterId to it.isRead }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override suspend fun markDetailChapters(
        key: LibraryEntryKey,
        chapterIds: Set<String>,
        read: Boolean,
    ): LibrarySnapshot = runRepo {
        if (chapterIds.isEmpty()) {
            store.markChaptersRead(key.sourceKey, key.kind.toDomain(), key.workId, emptyList(), read)
        } else {
            store.markChaptersRead(key.sourceKey, key.kind.toDomain(), key.workId, chapterIds.toList(), read)
        }
        load()
    }

    override suspend fun addToLibrary(entry: NewLibraryEntry): LibrarySnapshot = runRepo {
        store.addToLibrary(
            sourceKey = entry.sourceKey,
            kind = entry.kind.toDomain(),
            workId = entry.workId,
            title = entry.title,
            coverUrl = entry.coverUrl,
            sourceName = entry.sourceName.ifBlank { null },
            addedAtMillis = nowMillis(),
        )
        if (entry.categoryIds.isNotEmpty()) {
            store.setWorkCategories(entry.sourceKey, entry.kind.toDomain(), entry.workId, entry.categoryIds.toList())
        }
        load()
    }

    override suspend fun removeFromLibrary(keys: Set<LibraryEntryKey>): LibrarySnapshot = runRepo {
        for (key in keys) {
            store.removeFromLibrary(key.sourceKey, key.kind.toDomain(), key.workId)
        }
        load()
    }

    override suspend fun setEntryCategories(key: LibraryEntryKey, categoryIds: Set<Long>): LibrarySnapshot = runRepo {
        store.setWorkCategories(key.sourceKey, key.kind.toDomain(), key.workId, categoryIds.toList())
        load()
    }

    override suspend fun markAsRead(keys: Set<LibraryEntryKey>, read: Boolean): LibrarySnapshot = runRepo {
        // 先校验全部有目录（老收藏缺目录时抛可恢复错误，调用方引导打开详情补齐），
        // 再逐部写入 `library_chapter.is_read`（单事务/部，`reading_progress` 页键/页码/隐藏标记原样保留）。
        for (key in keys) {
            val chapters = store.listChapters(key.sourceKey, key.kind.toDomain(), key.workId)
            if (chapters.isEmpty()) {
                throw LibraryRepositoryException("No chapters for ${key.workId}; open details to refresh")
            }
        }
        for (key in keys) {
            store.markChaptersRead(key.sourceKey, key.kind.toDomain(), key.workId, emptyList(), read)
        }
        load()
    }

    override suspend fun addCategory(name: String): LibrarySnapshot = runRepo {
        store.createCategory(name)
        load()
    }

    override suspend fun renameCategory(id: Long, name: String): LibrarySnapshot = runRepo {
        store.renameCategory(id, name)
        load()
    }

    override suspend fun setCategoryHidden(id: Long, hidden: Boolean): LibrarySnapshot = runRepo {
        store.setCategoryHidden(id, hidden)
        load()
    }

    override suspend fun setCategoryOrder(orderedIds: List<Long>): LibrarySnapshot = runRepo {
        store.reorderCategories(orderedIds)
        load()
    }

    override suspend fun removeCategory(id: Long): LibrarySnapshot = runRepo {
        store.deleteCategory(id)
        load()
    }

    override suspend fun savePrefs(prefs: LibraryPrefs): LibrarySnapshot = runRepo {
        val serialized = serializePrefs(prefs)
        store.setPref(LibraryPrefsKeys.DISPLAY_MODE, serialized.mode)
        store.setPref(LibraryPrefsKeys.DISPLAY_COLUMNS, serialized.columns)
        store.setPref(LibraryPrefsKeys.FILTER_SPEC, serialized.filters)
        store.setPref(LibraryPrefsKeys.SORT_SPEC, serialized.sort)
        store.setPref(LibraryPrefsKeys.GROUP_SPEC, serialized.group)
        store.setPref(LibraryPrefsKeys.SHOW_UNREAD_BADGES, serialized.showUnreadBadges)
        store.setPref(LibraryPrefsKeys.SHOW_DOWNLOAD_BADGES, serialized.showDownloadBadges)
        store.setPref(LibraryPrefsKeys.SHOW_LANGUAGE_BADGES, serialized.showLanguageBadges)
        store.setPref(LibraryPrefsKeys.SHOW_CONTINUE_BUTTON, serialized.showContinueButton)
        store.setPref(LibraryPrefsKeys.SHOW_CATEGORY_LABELS, serialized.showCategoryLabels)
        store.setPref(LibraryPrefsKeys.SHOW_HIDDEN_CATEGORIES, serialized.showHiddenCategories)
        store.setPref(LibraryPrefsKeys.SHOW_WORK_COUNT, serialized.showWorkCount)
        load()
    }

    override suspend fun loadThumbnail(sourceKey: String, coverUrl: String): ByteArray {
        if (coverUrl.isBlank()) throw LibraryRepositoryException("Empty cover")
        try {
            return thumbnails(sourceKey, coverUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw LibraryRepositoryException(failure.message?.takeIf { it.isNotBlank() } ?: "Cover unavailable")
        }
    }

    private suspend fun <T> runRepo(block: suspend () -> T): T {
        try {
            return block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (repository: LibraryRepositoryException) {
            throw repository
        } catch (failure: Exception) {
            throw LibraryRepositoryException(failure.message?.takeIf { it.isNotBlank() } ?: "Library operation failed")
        }
    }
}

private fun LibraryKind.toDomain(): DomainWorkKind = when (this) {
    LibraryKind.COMIC -> DomainWorkKind.COMIC
    LibraryKind.NOVEL -> DomainWorkKind.NOVEL
}

private fun DomainLibraryManga.toFeatureKey(): LibraryEntryKey = LibraryEntryKey(
    sourceKey = sourceKey,
    kind = when (kind) {
        DomainWorkKind.COMIC -> LibraryKind.COMIC
        DomainWorkKind.NOVEL -> LibraryKind.NOVEL
    },
    workId = workId,
)

private fun DomainLibraryEntry.toFeature(
    progress: HistoryRecord?,
    totalChapters: Int = 0,
    unreadCount: Long = 0L,
): LibraryEntry = LibraryEntry(
    sourceKey = manga.sourceKey,
    kind = when (manga.kind) {
        DomainWorkKind.COMIC -> LibraryKind.COMIC
        DomainWorkKind.NOVEL -> LibraryKind.NOVEL
    },
    workId = manga.workId,
    title = manga.title,
    coverUrl = manga.coverUrl,
    sourceName = manga.sourceName.orEmpty(),
    favoriteAtMillis = manga.addedAtMillis,
    lastUpdateCheckMillis = manga.lastUpdateCheckMillis,
    totalChapters = totalChapters,
    unreadCount = unreadCount,
    lastReadAtMillis = progress?.readAtMillis,
    continueChapterId = progress?.chapterId,
    continueChapterTitle = progress?.chapterTitle,
    continuePageIndex = progress?.pageIndex ?: 0,
)

private fun DomainLibraryCategory.toFeature(): LibraryCategory = LibraryCategory(
    id = id,
    name = name,
    order = sortOrder.toLong(),
    isHidden = hidden,
    isDefault = isDefault,
)

internal data class SerializedPrefs(
    val mode: String,
    val columns: String,
    val filters: String,
    val sort: String,
    val group: String,
    val showUnreadBadges: String,
    val showDownloadBadges: String,
    val showLanguageBadges: String,
    val showContinueButton: String,
    val showCategoryLabels: String,
    val showHiddenCategories: String,
    val showWorkCount: String,
)

/** 落盘格式（`library_prefs` 十二键）：filter 为六个三态 ordinal 逗号分隔，sort 为 `TYPE,asc,seed`，七开关为 `true/false`。 */
internal fun serializePrefs(prefs: LibraryPrefs): SerializedPrefs = SerializedPrefs(
    mode = prefs.displayMode.serialize(),
    columns = prefs.columns.toString(),
    filters = listOf(
        prefs.filters.downloaded,
        prefs.filters.unread,
        prefs.filters.started,
        prefs.filters.bookmarked,
        prefs.filters.completed,
        prefs.filters.lewd,
    ).joinToString(",") { it.ordinal.toString() },
    sort = "${prefs.sortType.name},${prefs.ascending},${prefs.randomSeed}",
    group = prefs.groupBy.name,
    showUnreadBadges = prefs.showUnreadBadges.toString(),
    showDownloadBadges = prefs.showDownloadBadges.toString(),
    showLanguageBadges = prefs.showLanguageBadges.toString(),
    showContinueButton = prefs.showContinueButton.toString(),
    showCategoryLabels = prefs.showCategoryLabels.toString(),
    showHiddenCategories = prefs.showHiddenCategories.toString(),
    showWorkCount = prefs.showWorkCount.toString(),
)

internal suspend fun parsePrefs(get: suspend (String) -> String?): LibraryPrefs {
    val mode = get(LibraryPrefsKeys.DISPLAY_MODE)?.let {
        runCatching { LibraryDisplayMode.deserialize(it) }.getOrNull()
    } ?: LibraryDisplayMode.default
    val columns = get(LibraryPrefsKeys.DISPLAY_COLUMNS)?.toIntOrNull()?.coerceIn(1, 6) ?: 2
    val filters = get(LibraryPrefsKeys.FILTER_SPEC)?.let(::parseFilters) ?: LibraryFilters()
    val (sortType, ascending, seed) = get(LibraryPrefsKeys.SORT_SPEC)?.let(::parseSort) ?: Triple(LibrarySortType.ALPHABETICAL, true, 0L)
    val groupBy = get(LibraryPrefsKeys.GROUP_SPEC)?.let {
        runCatching { LibraryGroupBy.valueOf(it) }.getOrNull()
    } ?: LibraryGroupBy.CATEGORY
    // 逐键容错：旧库缺字段或单项非法值按默认值恢复，已有布局/列数/筛选/排序/分组不丢失。
    // 注意：每键单独读库，连续修改多个开关时调用方以完整快照写回，不互相覆盖。
    suspend fun bool(key: String, default: Boolean): Boolean =
        get(key)?.toBooleanStrictOrNull() ?: default
    return LibraryPrefs(
        displayMode = mode,
        columns = columns,
        filters = filters,
        sortType = sortType,
        ascending = ascending,
        groupBy = groupBy,
        randomSeed = seed,
        showUnreadBadges = bool(LibraryPrefsKeys.SHOW_UNREAD_BADGES, true),
        showDownloadBadges = bool(LibraryPrefsKeys.SHOW_DOWNLOAD_BADGES, true),
        showLanguageBadges = bool(LibraryPrefsKeys.SHOW_LANGUAGE_BADGES, true),
        showContinueButton = bool(LibraryPrefsKeys.SHOW_CONTINUE_BUTTON, true),
        showCategoryLabels = bool(LibraryPrefsKeys.SHOW_CATEGORY_LABELS, true),
        showHiddenCategories = bool(LibraryPrefsKeys.SHOW_HIDDEN_CATEGORIES, false),
        showWorkCount = bool(LibraryPrefsKeys.SHOW_WORK_COUNT, true),
    )
}

private fun parseFilters(spec: String): LibraryFilters {
    val parts = spec.split(",").map { it.toIntOrNull() ?: 0 }
    fun part(index: Int): LibraryTriState =
        LibraryTriState.entries.getOrNull(parts.getOrElse(index) { 0 }) ?: LibraryTriState.DISABLED
    return LibraryFilters(
        downloaded = part(0),
        unread = part(1),
        started = part(2),
        bookmarked = part(3),
        completed = part(4),
        lewd = part(5),
    )
}

private fun parseSort(spec: String): Triple<LibrarySortType, Boolean, Long> {
    val parts = spec.split(",")
    val type = parts.getOrNull(0)?.let {
        runCatching { LibrarySortType.valueOf(it) }.getOrNull()
    } ?: LibrarySortType.ALPHABETICAL
    val ascending = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true
    val seed = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return Triple(type, ascending, seed)
}
