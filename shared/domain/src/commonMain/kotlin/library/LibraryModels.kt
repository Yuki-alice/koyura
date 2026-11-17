package app.koyura.domain.library

/**
 * 书架领域模型与用例接口（书架持久化轨）。
 *
 * 收藏身份为三元主键 `(sourceKey, kind, workId)`：
 * - `sourceKey` 是源的稳定 key（与 `SourceInfo.id` / `extension.key` 同一命名空间，
 *   Venera 同包名多脚本以安装键为准），不是仓库地址也不是显示名；
 * - `workId` 是源远端作品 ID（`WorkSummary.id` / `WorkDetail.id`），严禁以标题去重；
 * - `kind` 取值为 [WorkKind.COMIC] 或 [WorkKind.NOVEL]。
 *
 * 同名不同源、同源同名不同 ID 都是不同行；`chapterless` 作品的章节在领域层统一为
 * `null`，落库时映射为空字符串（见 `reading_progress` 的既有语义）。
 */
enum class WorkKind {
    COMIC,
    NOVEL,
}

/**
 * 一条书架收藏：三元主键 + 标题/封面/源名快照 + 收藏时间 + 最近更新检查时间。
 *
 * 未读数、已下载数、继续阅读章均为派生值，由章节与 `reading_progress` 联查得出，
 * 不存快照（章节表与下载队列落地前，派生值只读 `reading_progress` 的最新行）。
 */
data class LibraryManga(
    val sourceKey: String,
    val kind: WorkKind,
    val workId: String,
    /** 作品标题快照（展示用，身份仍以 [workId] 为准）。 */
    val title: String,
    /** 封面快照：源返回的远端 URL，可能为空字符串（空时 UI 回退占位，见封面契约）。 */
    val coverUrl: String = "",
    /** 源显示名快照（源卸载或改名后收藏仍可见，显示此快照）。 */
    val sourceName: String? = null,
    /** 收藏时间，调用方传入的 epoch millis。 */
    val addedAtMillis: Long = 0L,
    /** 最近一次更新检查时间，调用方传入的 epoch millis，未检查过为 null。 */
    val lastUpdateCheckMillis: Long? = null,
)

/** 书架分类：自增 id + 名称 + 排序 + 隐藏标记 + 默认标记。 */
data class LibraryCategory(
    val id: Long,
    val name: String,
    val sortOrder: Int = 0,
    val hidden: Boolean = false,
    val isDefault: Boolean = false,
)

/** 书架条目与其分类归属（一作可属多类）。 */
data class LibraryEntry(
    val manga: LibraryManga,
    val categoryIds: List<Long> = emptyList(),
)

/** 书架列表排序。RANDOM 必须带确定性种子（见 [LibraryQuery.randomSeed]）。 */
enum class LibrarySort {
    NAME,
    DATE_ADDED,
    LAST_CHECKED,
    RANDOM,
}

/**
 * 书架查询：分类、搜索、排序语义。
 *
 * - [categoryId] 为 null 时查全库（含无分类作品）；指向隐藏分类时只返回该分类作品。
 * - [includeHiddenCategories] 为 false 时， exclusive 归属隐藏分类的作品被排除，
 *   无分类作品始终可见。
 * - [randomSeed] 只在 [sort] 为 RANDOM 时生效，同一种子同序。
 */
data class LibraryQuery(
    val search: String = "",
    val categoryId: Long? = null,
    val includeHiddenCategories: Boolean = false,
    val sort: LibrarySort = LibrarySort.NAME,
    val ascending: Boolean = true,
    val randomSeed: Long = 0L,
)

/** 书架网格/筛选/排序/分组偏好的落盘键（`library_prefs` 的键集合）。 */
object LibraryPrefsKeys {
    const val DISPLAY_MODE = "display.mode"
    const val DISPLAY_COLUMNS = "display.columns"
    const val FILTER_SPEC = "filter.spec"
    const val SORT_SPEC = "sort.spec"
    const val GROUP_SPEC = "group.spec"
}

/**
 * SY / KMK 扩展的跨源迁移合并入口：本轮只留 disabled 入口，不实现。
 *
 * 调用 [requireCrossSourceEnabled] 直接抛 [UnsupportedOperationException]，
 * 防止合入任何静默迁移逻辑；同源换 key 的迁移走 [LibraryRepository.migrateSource]。
 */
object LibraryMigrationGate {
    const val CROSS_SOURCE_ENABLED = false

    fun requireCrossSourceEnabled(): Nothing =
        throw UnsupportedOperationException("Cross-source (SY/KMK) migration is disabled this round.")
}

/** 源内迁移/合并的结果统计（只计数，不删旧行）。 */
data class LibraryMigrationReport(
    val copiedEntries: Int,
    val copiedMappings: Int,
    val copiedProgressRows: Int,
    val mergedProgressRows: Int,
)

/**
 * 书架仓储接口（domain 所有，实现在 `:shared:data` 的 `LibraryStore`）。
 *
 * - 收藏与取消按三元主键 upsert / delete，重复收藏返回原行；
 * - 删除分类只删归属映射，作品行保留（变为无分类）；
 * - 迁移/合并为单事务，任一步失败全部回滚且旧条目保留，旧 `reading_progress`
 *   行复制到新 key 后保留（至用户确认清理前不删）。
 */
interface LibraryRepository {
    suspend fun addToLibrary(
        sourceKey: String,
        kind: WorkKind,
        workId: String,
        title: String,
        coverUrl: String = "",
        sourceName: String? = null,
        addedAtMillis: Long,
    ): LibraryManga

    suspend fun removeFromLibrary(sourceKey: String, kind: WorkKind, workId: String)

    suspend fun listEntries(query: LibraryQuery = LibraryQuery()): List<LibraryEntry>

    suspend fun listCategories(includeHidden: Boolean = true): List<LibraryCategory>

    suspend fun createCategory(name: String): LibraryCategory

    suspend fun renameCategory(id: Long, name: String)

    suspend fun deleteCategory(id: Long)

    suspend fun reorderCategories(orderedIds: List<Long>)

    suspend fun setCategoryHidden(id: Long, hidden: Boolean)

    suspend fun setWorkCategories(sourceKey: String, kind: WorkKind, workId: String, categoryIds: List<Long>)

    suspend fun batchAssignToCategory(keys: List<Triple<String, WorkKind, String>>, categoryId: Long)

    suspend fun getPref(key: String): String?

    suspend fun setPref(key: String, value: String)

    suspend fun deletePref(key: String)

    /** 同源换 key（源 key 变更/仓库变体/旧源卸载后重装）：复制条目、归属（取并集）与进度。 */
    suspend fun migrateSource(oldSourceKey: String, newSourceKey: String): LibraryMigrationReport

    /** 把一条收藏合并到另一条（分类归属取并集，进度以较新者为准，旧条目保留）。 */
    suspend fun mergeEntry(
        from: Triple<String, WorkKind, String>,
        to: Triple<String, WorkKind, String>,
    ): LibraryMigrationReport
}
