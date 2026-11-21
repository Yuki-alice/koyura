package app.koyura.features.library

/**
 * 书架单测与界面测试共用的内存仓储（对标 FakeReaderService：feature 单测直接用，
 * app 链路测试经 `:shared:features` 依赖复用同一套存取纪律）。
 *
 * 固定数据集（覆盖分类 / 排序 / 搜索语义）：
 * - 同名不同源两条：srcA/w1 与 srcB/w2，标题同为 "同名作品"；
 * - 同源同名不同 ID 两条：srcA/w3 与 srcA/w4，标题同为 "连载双子星"；
 * - 无分类作品一条：srcA/w5 "孤本"；
 * - 隐藏分类作品一条：srcB/w6 "隐藏之作"（归属分类 2）；
 * - 空分类一个：id 3 "空分类"；
 * - chapterless 作品一条：srcA/w7 "单行本"（continueChapterId 为 null）。
 *
 * 状态夹具：空态（[emptySnapshot]）、加载态（session 初始即 Loading）、错误态
 * （[failures] 注入，对标 FakeBrowseService.failures）。
 * 封面三态夹具：正常 URL（[thumbnailBytes] 有字节）、损坏图（[thumbnailFailures]
 * 注入）、空字符串（entry.coverUrl 为空）。
 */
class FakeLibraryRepository(
    entries: List<LibraryEntry> = emptyList(),
    categories: List<LibraryCategory> = defaultLibraryCategories(),
    links: Map<LibraryEntryKey, Set<Long>> = emptyMap(),
    prefs: LibraryPrefs = LibraryPrefs(),
    thumbnailBytes: Map<String, ByteArray> = mapOf(
        "https://img.example/a1.png" to "bytes-a1".encodeToByteArray(),
        "https://img.example/twins.png" to "bytes-twins".encodeToByteArray(),
    ),
    thumbnailFailures: MutableMap<String, String> = mutableMapOf(
        "https://img.example/broken.png" to "404",
    ),
) : LibraryRepository {
    var entries: MutableList<LibraryEntry> = entries.toMutableList()
    var categories: MutableList<LibraryCategory> = categories.toMutableList()
    var links: MutableMap<LibraryEntryKey, Set<Long>> = links.toMutableMap()
    var prefs: LibraryPrefs = prefs

    var thumbnailBytes: MutableMap<String, ByteArray> = thumbnailBytes.toMutableMap()
    var thumbnailFailures: MutableMap<String, String> = thumbnailFailures

    /** 下一次指定操作抛错（用后即焚），key 为操作名，如 "load"、"removeFromLibrary"。 */
    val failures = mutableMapOf<String, String>()

    /** 各操作的调用次数，key 为 "操作[:目标]"。 */
    val calls = mutableMapOf<String, Int>()

    /** 封面逐 URL 请求次数（断言单次语义与按源隔离用）。 */
    val thumbnailLoadCounts = mutableMapOf<String, Int>()

    override val migrationSupported: Boolean = false

    private fun enter(op: String, target: String = "") {
        val key = if (target.isEmpty()) op else "$op:$target"
        calls[key] = (calls[key] ?: 0) + 1
        failures[op]?.let { message ->
            failures.remove(op)
            throw LibraryRepositoryException(message)
        }
    }

    private fun snapshotLocked() = LibrarySnapshot(
        entries = entries.toList(),
        categories = categories.toList(),
        links = links.toMap(),
        prefs = prefs,
    )

    override suspend fun load(): LibrarySnapshot {
        enter("load")
        return snapshotLocked()
    }

    override suspend fun addToLibrary(entry: NewLibraryEntry): LibrarySnapshot {
        enter("addToLibrary", entry.workId)
        if (entry.kind != LibraryKind.COMIC) {
            throw LibraryRepositoryException("Only COMIC rows are written in the manga round")
        }
        val key = LibraryEntryKey(entry.sourceKey, entry.kind, entry.workId)
        val existing = entries.firstOrNull { it.key == key }
        if (existing != null) return snapshotLocked() // 重复收藏返回原行
        entries += LibraryEntry(
            sourceKey = entry.sourceKey,
            kind = entry.kind,
            workId = entry.workId,
            title = entry.title,
            coverUrl = entry.coverUrl,
            sourceName = entry.sourceName,
            language = entry.language,
            isLocal = entry.isLocal,
            isNsfw = entry.isNsfw,
            status = entry.status,
            totalChapters = entry.totalChapters,
            favoriteAtMillis = entries.size.toLong() + 1L,
        )
        if (entry.categoryIds.isNotEmpty()) {
            links[key] = entry.categoryIds
        }
        return snapshotLocked()
    }

    override suspend fun removeFromLibrary(keys: Set<LibraryEntryKey>): LibrarySnapshot {
        enter("removeFromLibrary")
        entries.removeAll { it.key in keys }
        keys.forEach { links.remove(it) }
        return snapshotLocked()
    }

    override suspend fun setEntryCategories(key: LibraryEntryKey, categoryIds: Set<Long>): LibrarySnapshot {
        enter("setEntryCategories", key.workId)
        if (categoryIds.isEmpty()) links.remove(key) else links[key] = categoryIds
        return snapshotLocked()
    }

    override suspend fun markAsRead(keys: Set<LibraryEntryKey>, read: Boolean): LibrarySnapshot {
        enter("markAsRead")
        entries.replaceAll { entry ->
            if (entry.key !in keys) {
                entry
            } else if (read) {
                entry.copy(unreadCount = 0)
            } else {
                entry.copy(unreadCount = entry.totalChapters.coerceAtLeast(1).toLong())
            }
        }
        return snapshotLocked()
    }

    // 章节目录仅生产仓储实现（LibraryStore + ProductionLibraryRepository），
    // 内存假仓储保持派生字段直写，单测聚焦视图语义。
    // 详情已读映射：内存实现按 `unreadCount` 近似（未读>0 即未读），批量子集沿用整作标记。
    override suspend fun chapterReadStates(key: LibraryEntryKey): Map<String, Boolean> {
        enter("chapterReadStates", key.workId)
        return emptyMap()
    }

    // 批量下载选章用：按 `totalChapters` 生成 `c1..cN`（c1 最旧），
    // 末尾 `unreadCount` 个为未读（与生产「新章在后」语义一致）。
    override suspend fun listChapters(key: LibraryEntryKey): List<LibraryChapterEntry> {
        enter("listChapters", key.workId)
        val entry = entries.firstOrNull { it.key == key } ?: return emptyList()
        val total = entry.totalChapters.coerceAtLeast(0)
        if (total == 0) return emptyList()
        val unread = entry.unreadCount.coerceIn(0, total.toLong()).toInt()
        return (1..total).map { index ->
            LibraryChapterEntry(
                chapterId = "c$index",
                title = "Chapter $index",
                isRead = index <= total - unread,
            )
        }
    }

    override suspend fun markDetailChapters(
        key: LibraryEntryKey,
        chapterIds: Set<String>,
        read: Boolean,
    ): LibrarySnapshot {
        enter("markDetailChapters")
        return markAsRead(setOf(key), read)
    }

    override suspend fun addCategory(name: String): LibrarySnapshot {
        enter("addCategory")
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "blank" }
        val nextId = (categories.maxOfOrNull { it.id } ?: 0L) + 1L
        val nextOrder = (categories.maxOfOrNull { it.order } ?: 0L) + 1L
        categories += LibraryCategory(id = nextId, name = trimmed, order = nextOrder)
        return snapshotLocked()
    }

    override suspend fun renameCategory(id: Long, name: String): LibrarySnapshot {
        enter("renameCategory")
        categories.replaceAll { if (it.id == id) it.copy(name = name.trim()) else it }
        return snapshotLocked()
    }

    override suspend fun setCategoryHidden(id: Long, hidden: Boolean): LibrarySnapshot {
        enter("setCategoryHidden")
        categories.replaceAll { if (it.id == id) it.copy(isHidden = hidden) else it }
        return snapshotLocked()
    }

    override suspend fun setCategoryOrder(orderedIds: List<Long>): LibrarySnapshot {
        enter("setCategoryOrder")
        val rank = orderedIds.withIndex().associate { (index, id) -> id to index.toLong() }
        categories.replaceAll { it.copy(order = rank[it.id] ?: it.order) }
        return snapshotLocked()
    }

    override suspend fun removeCategory(id: Long): LibrarySnapshot {
        enter("removeCategory")
        categories.removeAll { it.id == id }
        val updated = links.mapValues { (_, ids) -> ids - id }.filterValues { it.isNotEmpty() }
        links.clear()
        links.putAll(updated)
        return snapshotLocked()
    }

    override suspend fun savePrefs(prefs: LibraryPrefs): LibrarySnapshot {
        enter("savePrefs")
        this.prefs = prefs
        return snapshotLocked()
    }

    override suspend fun loadThumbnail(sourceKey: String, coverUrl: String): ByteArray {
        val key = "$sourceKey\n$coverUrl"
        thumbnailLoadCounts[key] = (thumbnailLoadCounts[key] ?: 0) + 1
        if (coverUrl.isBlank()) throw LibraryRepositoryException("Empty cover")
        thumbnailFailures[coverUrl]?.let { message ->
            thumbnailFailures.remove(coverUrl)
            throw LibraryRepositoryException(message)
        }
        return thumbnailBytes[coverUrl] ?: throw LibraryRepositoryException("No bytes for $coverUrl")
    }
}

fun defaultLibraryCategories(): List<LibraryCategory> = listOf(
    LibraryCategory(id = 1L, name = "默认", order = 0L, isDefault = true),
    LibraryCategory(id = 2L, name = "隐藏书单", order = 1L, isHidden = true),
    LibraryCategory(id = 3L, name = "空分类", order = 2L),
)

/** 固定数据集：同名不同源 / 同源同名不同 ID / 无分类 / 隐藏分类 / 空分类 / chapterless。 */
fun libraryFixtureSnapshot(): LibrarySnapshot {
    val entries = listOf(
        LibraryEntry(
            sourceKey = "srcA", workId = "w1", title = "同名作品",
            coverUrl = "https://img.example/a1.png", sourceName = "源A", language = "zh",
            totalChapters = 10, unreadCount = 3, downloadedCount = 2,
            lastReadAtMillis = 1000L, favoriteAtMillis = 10L,
            status = LibraryStatus.ONGOING,
            lastUpdateCheckMillis = 500L, latestChapterMillis = 900L, chapterFetchMillis = 800L,
            continueChapterId = "c3", continueChapterTitle = "第3话", continuePageIndex = 5,
        ),
        LibraryEntry(
            sourceKey = "srcB", workId = "w2", title = "同名作品",
            coverUrl = "https://img.example/broken.png", sourceName = "源B", language = "en",
            totalChapters = 8, unreadCount = 0, downloadedCount = 0,
            lastReadAtMillis = 2000L, favoriteAtMillis = 20L,
            status = LibraryStatus.COMPLETED, bookmarked = true,
            continueChapterId = "d8", continueChapterTitle = "Ch.8", continuePageIndex = 0,
        ),
        LibraryEntry(
            sourceKey = "srcA", workId = "w3", title = "连载双子星",
            coverUrl = "https://img.example/twins.png", sourceName = "源A", language = "zh",
            totalChapters = 20, unreadCount = 7, favoriteAtMillis = 30L,
            lastReadAtMillis = 1500L,
            lastUpdateCheckMillis = 300L, latestChapterMillis = 700L, chapterFetchMillis = 600L,
            continueChapterId = "t5", continueChapterTitle = "第5话", continuePageIndex = 1,
        ),
        LibraryEntry(
            sourceKey = "srcA", workId = "w4", title = "连载双子星",
            coverUrl = "", sourceName = "源A", language = "zh",
            totalChapters = 5, unreadCount = 0, favoriteAtMillis = 40L, isNsfw = true,
        ),
        LibraryEntry(
            sourceKey = "srcA", workId = "w5", title = "孤本",
            coverUrl = "https://img.example/a1.png", sourceName = "源A", language = "ja",
            totalChapters = 1, unreadCount = 1, favoriteAtMillis = 50L,
            continueChapterId = "only", continueChapterTitle = "全一话", continuePageIndex = 0,
        ),
        LibraryEntry(
            sourceKey = "srcB", workId = "w6", title = "隐藏之作",
            coverUrl = "", sourceName = "源B", language = "en",
            totalChapters = 12, unreadCount = 12, favoriteAtMillis = 60L,
        ),
        LibraryEntry(
            sourceKey = "srcA", workId = "w7", title = "单行本",
            coverUrl = "https://img.example/a1.png", sourceName = "源A", language = "zh",
            totalChapters = 1, unreadCount = 1, favoriteAtMillis = 70L,
            continueChapterId = null,
        ),
    )
    val links = mapOf(
        LibraryEntryKey("srcA", LibraryKind.COMIC, "w1") to setOf(1L),
        LibraryEntryKey("srcB", LibraryKind.COMIC, "w2") to setOf(1L),
        LibraryEntryKey("srcA", LibraryKind.COMIC, "w3") to setOf(1L),
        LibraryEntryKey("srcA", LibraryKind.COMIC, "w4") to setOf(1L),
        LibraryEntryKey("srcB", LibraryKind.COMIC, "w6") to setOf(2L),
        LibraryEntryKey("srcA", LibraryKind.COMIC, "w7") to setOf(1L),
    )
    return LibrarySnapshot(entries, defaultLibraryCategories(), links, LibraryPrefs())
}

/** 空态夹具：零条目、零分类。 */
fun emptyLibrarySnapshot(): LibrarySnapshot = LibrarySnapshot()

private fun <T> MutableList<T>.replaceAll(transform: (T) -> T) {
    for (index in indices) set(index, transform(get(index)))
}
