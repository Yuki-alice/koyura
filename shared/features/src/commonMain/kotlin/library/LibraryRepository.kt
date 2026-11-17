package app.koyura.features.library

/**
 * 书架数据边界（界面层定义、持久化轨实现）。
 *
 * 沿用 Browse / History 的约定：界面只表达意图，仓储每次返回完整快照，
 * 失败抛 [LibraryRepositoryException]（用户可读信息），调用方把条目与错误原样呈现。
 * 漫画对齐轮只写入 [LibraryKind.COMIC] 行；NOVEL 行必须被拒绝。
 *
 * 生产实现由持久化轨提供（library_manga / library_category /
 * library_manga_category / library_prefs 四表 + 同一 ExtensionDb 单步升版）：
 * - 取图走 `comic.onThumbnailLoad` 回调或源 HTTP 直链并携带该源 Cookie 与请求头，
 *   按 sourceKey 隔离内存与磁盘缓存；
 * - 缩略图沿用上游单次无重试语义；
 * - 源 key 变更 / 仓库变体 / 旧源卸载后的进度与分类归属跟随三元主键迁移。
 */
interface LibraryRepository {
    /**
     * 跨源迁移合并入口本轮只留 disabled 入口：恒为 false，UI 置灰并注明后续轮。
     * 任一实现不得在本轮提供迁移写入。
     */
    val migrationSupported: Boolean
        get() = false

    /** 完整快照：条目、分类、归属与偏好一次读回。 */
    suspend fun load(): LibrarySnapshot

    /**
     * 按三元主键收藏（upsert）。重复收藏返回原行：收藏时间与分类归属保持不变，
     * 快照（标题 / 封面 / 源名）可用新值刷新。
     */
    suspend fun addToLibrary(entry: NewLibraryEntry): LibrarySnapshot

    /** 按三元主键取消收藏（delete）。 */
    suspend fun removeFromLibrary(keys: Set<LibraryEntryKey>): LibrarySnapshot

    /** 改条目分类归属（一作多类；空集表示无分类）。 */
    suspend fun setEntryCategories(key: LibraryEntryKey, categoryIds: Set<Long>): LibrarySnapshot

    /**
     * 批量标已读 / 标未读。[read] 为 true 时对应条目未读清零；
     * 为 false 时未读回到全部章节（派生值由仓储联查重算）。
     */
    suspend fun markAsRead(keys: Set<LibraryEntryKey>, read: Boolean): LibrarySnapshot

    suspend fun addCategory(name: String): LibrarySnapshot
    suspend fun renameCategory(id: Long, name: String): LibrarySnapshot
    suspend fun setCategoryHidden(id: Long, hidden: Boolean): LibrarySnapshot
    suspend fun setCategoryOrder(orderedIds: List<Long>): LibrarySnapshot

    /**
     * 删除分类。条目的其它分类归属保留；无归属条目变为无分类（不断言、不删条目）。
     */
    suspend fun removeCategory(id: Long): LibrarySnapshot

    suspend fun savePrefs(prefs: LibraryPrefs): LibrarySnapshot

    /**
     * 取一条封面字节：单次无重试，失败抛 [LibraryRepositoryException]。
     *
     * 生产实现经源会话 `comic.onThumbnailLoad` 单次调用（无 `onLoadFailed` 重试），
     * 无该回调时走源 HTTP 直链并携带该源 Cookie 与请求头。
     * [coverUrl] 为空时直接抛错，调用方回退占位（不得请求网络）。
     */
    suspend fun loadThumbnail(sourceKey: String, coverUrl: String): ByteArray
}

class LibraryRepositoryException(message: String) : Exception(message)

/** 书架完整快照：条目 + 分类 + 一作多类归属 + 偏好。 */
data class LibrarySnapshot(
    val entries: List<LibraryEntry> = emptyList(),
    val categories: List<LibraryCategory> = emptyList(),
    /** 三元主键 -> 分类 id 集合。 */
    val links: Map<LibraryEntryKey, Set<Long>> = emptyMap(),
    val prefs: LibraryPrefs = LibraryPrefs(),
) {
    /** 按页签顺序排列的分类。 */
    val orderedCategories: List<LibraryCategory>
        get() = categories.sortedWith(compareBy({ it.order }, { it.id }))

    /** 条目归属的分类 id（无归属返回空集）。 */
    fun categoriesOf(key: LibraryEntryKey): Set<Long> = links[key] ?: emptySet()

    /** 分类下的条目（[categoryId] 为 null 表示“全部”）。 */
    fun entriesIn(categoryId: Long?): List<LibraryEntry> {
        if (categoryId == null) return entries
        return entries.filter { categoriesOf(it.key).contains(categoryId) }
    }

    /** 无分类条目。 */
    fun uncategorized(): List<LibraryEntry> =
        entries.filter { categoriesOf(it.key).isEmpty() }
}
