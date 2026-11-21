package app.koyura.features.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import app.koyura.features.downloads.DownloadQueueRepository
import app.koyura.features.downloads.LocalDownloadQueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 书架列表加载态（对齐 History / Browse 的 Loading / Ready / Failed 三态 + 未接线）。 */
sealed interface LibraryLoadState {
    data object Loading : LibraryLoadState
    data object Ready : LibraryLoadState
    data class Failed(val message: String) : LibraryLoadState

    /** 未注入仓储：书架入口可用但呈空态，其余功能保持可点但报可恢复错误。 */
    data object Unavailable : LibraryLoadState
}

/** 单张封面的加载态：空 URL 直接 Empty（不请求网络），失败即 Failed（占位）。 */
sealed interface LibraryCoverState {
    data object Empty : LibraryCoverState
    data object Loading : LibraryCoverState
    data class Ready(val bytes: ByteArray) : LibraryCoverState
    data class Failed(val message: String) : LibraryCoverState
}

/** 返回键消费结果：调用方（装配层 BackHandler）据此决定是否继续 pop。 */
enum class LibraryBackOutcome {
    /** 清掉了选择态，页面不动。 */
    ClearedSelection,

    /** 清掉了搜索，页面不动。 */
    ClearedSearch,

    /** 本层无可清状态，交回导航栈 / 平台。 */
    NotConsumed,
}

/**
 * 书架页状态座：真实数据的唯一读者与唯一写者。
 *
 * 并发规则（沿用 BrowseSession 的写法）：
 * - 每次变更后重读完整快照再呈现，页内容、选择态与徽标都收敛到已提交结果；
 * - 失败只记 `actionError`，已提交的快照原样保留，可重试；
 * - 取消不是失败：不记错误，目标可立即重试；
 * - 封面按“可见即加载”单次请求：内存缓存以 `sourceKey + coverUrl` 隔离不同源，
 *   命中（成功或失败）不再请求；并发重复请求单飞，整轮只打一次仓储。
 */
class LibrarySession(
    private val repository: LibraryRepository?,
    val downloads: DownloadQueueRepository? = null,
) {
    var loadState: LibraryLoadState by mutableStateOf(LibraryLoadState.Loading)
        private set

    var snapshot: LibrarySnapshot by mutableStateOf(LibrarySnapshot())
        private set

    /** 页内搜索（页内状态，不进返回栈；返回键第二优先级清除）。 */
    var query: String by mutableStateOf("")
        private set

    /** 当前分类页签：null = 全部。页内状态，不进返回栈。 */
    var activeCategoryId: Long? by mutableStateOf(null)
        private set

    /** 多选集合（三元主键）。非空时工具栏切选择模式并隐藏底栏（由装配层读取）。 */
    var selection: Set<LibraryEntryKey> by mutableStateOf(emptySet())
        private set

    /** 变更失败的可恢复提示；成功或手动清除后置空。 */
    var actionError: String? by mutableStateOf(null)
        private set

    /** 封面态：缓存键见 [coverCacheKey]。 */
    val covers: SnapshotStateMap<String, LibraryCoverState> = mutableStateMapOf()

    private val coverMutex = Mutex()
    private val coverInFlight = mutableSetOf<String>()

    /** 隐藏过滤后的全量（无分类/搜索）：页签计数与选中清理的同一基准。 */
    private val hiddenFilteredEntries: List<LibraryEntry>
        get() {
            if (snapshot.prefs.showHiddenCategories) return snapshot.entries
            val hiddenIds = snapshot.categories.filter { it.isHidden }.map { it.id }.toSet()
            if (hiddenIds.isEmpty()) return snapshot.entries
            return snapshot.entries.filter { entry ->
                val ids = snapshot.links[entry.key] ?: emptySet()
                ids.isEmpty() || ids.any { it !in hiddenIds }
            }
        }

    /** 当前视图（隐藏 + 搜索 + 分类 + 筛选 + 排序已应用，分组未切分）。 */
    val visibleEntries: List<LibraryEntry>
        get() {
            // 先缩到隐藏基准，再走同一视图函数，保证搜索/分组/计数/全选同一可见范围；
            // 混合归属与无分类在基准中已保留，仅归属隐藏的在此消失。
            val base = hiddenFilteredEntries
            return applyLibraryView(
                entries = base,
                links = snapshot.links,
                query = query,
                categoryId = activeCategoryId,
                filters = snapshot.prefs.filters,
                sortType = snapshot.prefs.sortType,
                ascending = snapshot.prefs.ascending,
                randomSeed = snapshot.prefs.randomSeed,
            )
        }

    /** 指定页签在当前隐藏开关下的计数基数（搜索外，隐藏已过滤）。 */
    fun countForTab(categoryId: Long?): Int {
        val base = hiddenFilteredEntries
        if (categoryId == null) return base.size
        return base.count { (snapshot.links[it.key] ?: emptySet()).contains(categoryId) }
    }

    /** 分组后的视图（组头由 UI 渲染）。 */
    val visibleSections: List<LibrarySection>
        get() = groupLibraryEntries(visibleEntries, snapshot.prefs.groupBy)

    /** 页签（含“全部”）：隐藏分类仅在偏好允许时出现。 */
    val tabs: List<LibraryCategory?>
        get() {
            val ordered = snapshot.orderedCategories
            val shown = if (snapshot.prefs.showHiddenCategories) ordered else ordered.filterNot { it.isHidden }
            return listOf(null) + shown
        }

    /** 返回键是否应被书架页消费（选择态 / 搜索非空时消费）。 */
    val wantsBack: Boolean
        get() = selection.isNotEmpty() || query.isNotEmpty()

    suspend fun refresh() {
        val current = repository
        if (current == null) {
            loadState = LibraryLoadState.Unavailable
            return
        }
        loadState = LibraryLoadState.Loading
        try {
            snapshot = current.load()
            normalizeAfterSnapshotChange()
            loadState = LibraryLoadState.Ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadState = LibraryLoadState.Failed(failure.userMessage())
        }
    }

    /**
     * 快照变更后的收敛：当前页签被隐藏或删除时回“全部”，选中仅保留隐藏基准内仍可见者。
     * 收藏身份、分类归属与历史收藏状态不受影响（仅页签与选中收敛）。
     */
    private fun normalizeAfterSnapshotChange() {
        val allowedTabs = tabs.mapNotNull { it?.id }.toSet()
        if (activeCategoryId != null && activeCategoryId !in allowedTabs) {
            activeCategoryId = null
        }
        val visibleKeys = hiddenFilteredEntries.map { it.key }.toSet()
        selection = selection.filter { it in visibleKeys }.toSet()
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun selectCategory(id: Long?) {
        activeCategoryId = id
    }

    // region 选择

    fun toggleSelect(key: LibraryEntryKey) {
        selection = if (selection.contains(key)) selection - key else selection + key
    }

    fun clearSelection() {
        selection = emptySet()
    }

    /** 全选当前视图（批量操作入口）。 */
    fun selectAllVisible() {
        selection = visibleEntries.map { it.key }.toSet()
    }

    /** 反选当前视图。 */
    fun invertSelection() {
        val visible = visibleEntries.map { it.key }.toSet()
        selection = visible - selection
    }

    /**
     * 返回键规则：选择态下先清选择，再清搜索，最后交回导航（各 tab 根上回书架、
     * 阅读器返回落在原 tab 由装配层既有逻辑处理，本层不碰返回栈）。
     */
    fun consumeBack(): LibraryBackOutcome = when {
        selection.isNotEmpty() -> {
            clearSelection()
            LibraryBackOutcome.ClearedSelection
        }
        query.isNotEmpty() -> {
            query = ""
            LibraryBackOutcome.ClearedSearch
        }
        else -> LibraryBackOutcome.NotConsumed
    }

    // endregion

    // region 收藏与批量操作（全部落盘：成功即新快照，失败只记 actionError）

    fun clearActionError() {
        actionError = null
    }

    /**
     * 装配层展示继续/打开失败等非变更错误：只记提示、不碰已提交快照，
     * 用户可重试或手动清除。
     */
    fun noteError(message: String) {
        actionError = message
    }

    /** 详情页收藏 / 取消收藏（重复收藏返回原行，收藏时间不变由仓储保证）。 */
    suspend fun setFavorite(entry: NewLibraryEntry, favorite: Boolean): Boolean {
        val current = repository ?: return fail("Library is not connected")
        return try {
            snapshot = if (favorite) current.addToLibrary(entry) else
                current.removeFromLibrary(setOf(LibraryEntryKey(entry.sourceKey, entry.kind, entry.workId)))
            normalizeAfterSnapshotChange()
            actionError = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    fun isFavorite(key: LibraryEntryKey): Boolean =
        snapshot.entries.any { it.key == key }

    /** 底部批量：移除所选（需 UI 二次确认，确认后调用）。 */
    suspend fun removeSelected(): Boolean = mutateSelected { repo, keys ->
        repo.removeFromLibrary(keys)
    }.also { if (it) clearSelection() }

    /**
     * 撤销移除：按原快照把条目连同分类归属写回（Snackbar 的「撤销」）。
     *
     * 只回填**展示快照**字段；阅读进度/已读状态属于进度表，删除书架不删进度，
     * 因此重新收藏后由仓储联查自然恢复。
     */
    suspend fun restore(
        entries: List<LibraryEntry>,
        categories: Map<LibraryEntryKey, Set<Long>>,
    ): Boolean {
        val current = repository ?: return fail("Library is not connected")
        if (entries.isEmpty()) return true
        return try {
            var next = snapshot
            for (entry in entries) {
                next = current.addToLibrary(
                    NewLibraryEntry(
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
                    ),
                )
                val ids = categories[entry.key].orEmpty()
                if (ids.isNotEmpty()) next = current.setEntryCategories(entry.key, ids)
            }
            snapshot = next
            normalizeAfterSnapshotChange()
            actionError = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    /** 底部批量：改所选分类（一作多类）。 */
    suspend fun setCategoriesForSelected(categoryIds: Set<Long>): Boolean {
        val current = repository ?: return fail("Library is not connected")
        val keys = selection.toSet()
        if (keys.isEmpty()) return true
        return try {
            var next = snapshot
            for (key in keys) {
                next = current.setEntryCategories(key, categoryIds)
            }
            snapshot = next
            normalizeAfterSnapshotChange()
            actionError = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    /** 底部批量：标已读 / 标未读。 */
    suspend fun markSelectedAsRead(read: Boolean): Boolean =
        mutateSelected { repo, keys -> repo.markAsRead(keys, read) }

    /**
     * 更多页「仅看已下载」开关：直写书架筛选偏好（与筛选弹层的下载行同一字段）。
     *
     * 开 = 只看已下载，关 = 回到不过滤（会覆盖弹层里手设的排除态，与上游同语义）。
     */
    suspend fun setDownloadedOnlyFilter(enabled: Boolean): Boolean {
        val current = repository ?: return fail("Library is not connected")
        return try {
            snapshot = current.savePrefs(
                snapshot.prefs.copy(
                    filters = snapshot.prefs.filters.copy(
                        downloaded = if (enabled) LibraryTriState.INCLUDE else LibraryTriState.DISABLED,
                    ),
                ),
            )
            normalizeAfterSnapshotChange()
            actionError = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    /**
     * 底部批量：下载所选（只收未读章节）。
     *
     * 无下载仓储（测试/预览）返回 false 由调用方置灰；所选无可下章节记
     * `actionError` 并保留选择态，其余失败按作跳过、成功后清空选择。
     */
    suspend fun downloadSelected(): Boolean {
        val current = repository ?: return fail("Library is not connected")
        val queue = downloads ?: return fail("Downloads are not connected")
        val keys = selection.toSet()
        if (keys.isEmpty()) return true
        return try {
            var enqueued = 0
            for (key in keys) {
                val unread = current.listChapters(key).filter { !it.isRead }.map { it.chapterId }
                if (unread.isEmpty()) continue
                queue.enqueueChapters(key.sourceKey, key.workId, unread)
                enqueued++
            }
            if (enqueued == 0) {
                fail("No new chapters to download")
                false
            } else {
                selection = emptySet()
                actionError = null
                true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    /** 详情章列表的已读映射（生产仓储读同一 `library_chapter` 表）。 */
    suspend fun chapterReadStates(key: LibraryEntryKey): Map<String, Boolean> {
        val current = repository ?: return emptyMap()
        return try {
            current.chapterReadStates(key)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** 详情批量标已读/未读（子集写入，成功即新快照，失败只记 `actionError`）。 */
    suspend fun markDetailChapters(key: LibraryEntryKey, chapterIds: Set<String>, read: Boolean): Boolean {
        val current = repository ?: return fail("Library is not connected")
        return try {
            snapshot = current.markDetailChapters(key, chapterIds, read)
            normalizeAfterSnapshotChange()
            actionError = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    /** 单条目改分类（详情页选择分类用，一作多类落盘）。 */
    suspend fun setEntryCategories(key: LibraryEntryKey, categoryIds: Set<Long>): Boolean {
        val current = repository ?: return fail("Library is not connected")
        return try {
            snapshot = current.setEntryCategories(key, categoryIds)
            normalizeAfterSnapshotChange()
            actionError = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    private suspend fun mutateSelected(
        block: suspend (LibraryRepository, Set<LibraryEntryKey>) -> LibrarySnapshot,
    ): Boolean {
        val current = repository ?: return fail("Library is not connected")
        val keys = selection.toSet()
        if (keys.isEmpty()) return true
        return try {
            snapshot = block(current, keys)
            normalizeAfterSnapshotChange()
            actionError = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    // endregion

    // region 分类管理

    suspend fun addCategory(name: String): Boolean = mutateCategories { it.addCategory(name) }

    suspend fun renameCategory(id: Long, name: String): Boolean =
        mutateCategories { it.renameCategory(id, name) }

    suspend fun setCategoryHidden(id: Long, hidden: Boolean): Boolean =
        mutateCategories { it.setCategoryHidden(id, hidden) }

    suspend fun setCategoryOrder(orderedIds: List<Long>): Boolean =
        mutateCategories { it.setCategoryOrder(orderedIds) }

    suspend fun removeCategory(id: Long): Boolean =
        mutateCategories { it.removeCategory(id) }.also { ok ->
            if (ok && activeCategoryId == id) activeCategoryId = null
        }

    private suspend fun mutateCategories(
        block: suspend (LibraryRepository) -> LibrarySnapshot,
    ): Boolean {
        val current = repository ?: return fail("Library is not connected")
        return try {
            snapshot = block(current)
            normalizeAfterSnapshotChange()
            actionError = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    // endregion

    // region 偏好（显示 / 排序 / 筛选 / 分组：每次落盘并可从新会话读回）

    suspend fun updatePrefs(transform: (LibraryPrefs) -> LibraryPrefs): Boolean {
        val current = repository ?: return fail("Library is not connected")
        return try {
            snapshot = current.savePrefs(transform(snapshot.prefs))
            normalizeAfterSnapshotChange()
            actionError = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail(failure.userMessage())
            false
        }
    }

    // endregion

    // region 封面（可见即加载 + 一屏预取由 UI 触发；单次无重试；按源隔离）

    /**
     * 确保一张封面已请求。空 URL 直接记 Empty（零网络）；命中缓存（成功或失败）
     * 不再请求；并发重复请求单飞。
     */
    suspend fun ensureCover(entry: LibraryEntry) {
        val key = coverCacheKey(entry.sourceKey, entry.coverUrl)
        if (covers.containsKey(key)) return
        if (entry.coverUrl.isBlank()) {
            covers[key] = LibraryCoverState.Empty
            return
        }
        val current = repository
        if (current == null) {
            covers[key] = LibraryCoverState.Failed("Library is not connected")
            return
        }
        val shouldLoad = coverMutex.withLock {
            if (covers.containsKey(key) || !coverInFlight.add(key)) false else true
        }
        if (!shouldLoad) return
        covers[key] = LibraryCoverState.Loading
        try {
            val bytes = current.loadThumbnail(entry.sourceKey, entry.coverUrl)
            covers[key] = LibraryCoverState.Ready(bytes)
        } catch (cancelled: CancellationException) {
            coverMutex.withLock { coverInFlight.remove(key) }
            covers.remove(key)
            throw cancelled
        } catch (failure: Exception) {
            covers[key] = LibraryCoverState.Failed(failure.userMessage())
        } finally {
            coverMutex.withLock { coverInFlight.remove(key) }
        }
    }

    /** 为可见区外一屏预取：只请求给定条目，不做全量预取。 */
    suspend fun prefetchCovers(entries: List<LibraryEntry>) {
        for (entry in entries) ensureCover(entry)
    }

    /**
     * 供 [app.koyura.designsystem.Cover] 调用的单次取图：
     * 先走缓存/单飞 [ensureCover]，命中即返字节，未命中（失败或空图）即抛错，
     * 上层回退占位。本函数自身不重试。
     */
    suspend fun coverBytes(entry: LibraryEntry): ByteArray {
        ensureCover(entry)
        val key = coverCacheKey(entry.sourceKey, entry.coverUrl)
        val ready = snapshotFlow { covers[key] }.first { it != LibraryCoverState.Loading } as? LibraryCoverState.Ready
        return ready?.bytes ?: throw LibraryRepositoryException("Cover unavailable")
    }

    /** 失败封面的手动重试（清缓存后重走单次请求）。 */
    suspend fun retryCover(entry: LibraryEntry) {
        covers.remove(coverCacheKey(entry.sourceKey, entry.coverUrl))
        ensureCover(entry)
    }

    private fun fail(message: String): Boolean {
        actionError = message
        return false
    }

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Operation failed"
}

/** 封面内存缓存键：源 key 与 URL 联合，天然按源隔离。 */
fun coverCacheKey(sourceKey: String, coverUrl: String): String = "$sourceKey\n$coverUrl"

/** 注入的书架仓储；未装配时书架呈未接线空态。 */
val LocalLibraryRepository = staticCompositionLocalOf<LibraryRepository?> { null }

/** 书架会话：tab 内单例，随页签与宽窄切换保持（由装配层在 Library 根持有）。 */
@Composable
fun rememberLibrarySession(
    repository: LibraryRepository? = LocalLibraryRepository.current,
    downloads: DownloadQueueRepository? = LocalDownloadQueueRepository.current,
): LibrarySession = remember(repository, downloads) { LibrarySession(repository, downloads) }
