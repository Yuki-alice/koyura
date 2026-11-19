package app.koyura.features.library

/**
 * 书架与详情交互轨的界面层模型。
 *
 * 持久化语义（与持久化轨共享的约定，见本轨契约 `domain` 段）：
 * - 远端作品去重键为三元组 (sourceKey, kind, workId)。`sourceKey` 与
 *   `SourceInfo.id` / `ExtensionInfo.key` 同一命名空间（Venera 稳定 key），`workId`
 *   即 `WorkSummary.id` / `WorkDetail.id`（源远端作品 ID）。严禁以标题去重。
 * - [LibraryKind] 取值为 COMIC 或 NOVEL；漫画对齐轮只产生 COMIC 行，NOVEL 行不得写入。
 * - 未读数 / 已下载数 / 继续阅读章为派生值，由章节与 reading_progress 联查得出，
 *   不存快照。本文件的对应字段是仓储联查后填好的只读载荷，不是快照。
 * - 收藏与取消收藏按三元主键 upsert / delete，重复收藏返回原行（收藏时间不变）。
 * - SY / KMK 扩展的跨源迁移合并入口本轮只留 disabled 入口，不实现（见
 *   [LibraryRepository.migrationSupported]）。
 */
enum class LibraryKind {
    COMIC,
    NOVEL,
}

/** 书架条目三元主键。 */
data class LibraryEntryKey(
    val sourceKey: String,
    val kind: LibraryKind = LibraryKind.COMIC,
    val workId: String,
)

/** 作品完结状态（分组 `STATUS` 与完结筛选的依据）。 */
enum class LibraryStatus {
    ONGOING,
    COMPLETED,
    UNKNOWN,
}

/**
 * 书架中的一部作品。
 *
 * 快照字段（收藏当时写入）：[title]、[coverUrl]、[sourceName]、[favoriteAtMillis]。
 * 派生载荷（仓储每次联查重算）：[unreadCount]、[downloadedCount]、
 * [continueChapterId] / [continueChapterTitle] / [continuePageIndex]、
 * [lastReadAtMillis] 等时间戳。
 */
data class LibraryEntry(
    val sourceKey: String,
    val kind: LibraryKind = LibraryKind.COMIC,
    val workId: String,
    val title: String,
    val coverUrl: String = "",
    val sourceName: String = "",
    val language: String = "",
    val isLocal: Boolean = false,
    val isNsfw: Boolean = false,
    val status: LibraryStatus = LibraryStatus.UNKNOWN,
    val totalChapters: Int = 0,
    val bookmarked: Boolean = false,
    val favoriteAtMillis: Long = 0L,
    val lastUpdateCheckMillis: Long? = null,
    val unreadCount: Long = 0L,
    val downloadedCount: Long = 0L,
    val lastReadAtMillis: Long? = null,
    val latestChapterMillis: Long? = null,
    val chapterFetchMillis: Long? = null,
    val continueChapterId: String? = null,
    val continueChapterTitle: String? = null,
    val continuePageIndex: Int = 0,
) {
    val key: LibraryEntryKey
        get() = LibraryEntryKey(sourceKey, kind, workId)

    /** 展示标题：快照为空时回退到远端 ID。 */
    val displayTitle: String
        get() = title.ifBlank { workId }

    /** 是否已开始阅读（派生：存在阅读进度即算开始）。 */
    val hasStarted: Boolean
        get() = lastReadAtMillis != null || continueChapterId != null

    /**
     * 是否可开始/继续阅读。
     *
     * 任何已收藏作品都可尝试打开详情再进阅读（无分章作品用 `chapterId = null` 契约）：
     * 新收藏未读显示“开始”，有进度（含 `chapterId = null` 的无分章进度）显示“继续”。
     * 标签区分仍看 [hasStarted]，此处恒为 true 以保证新作可开始、章后进度可续。
     */
    val canContinue: Boolean
        get() = true
}

/** 书架分类。`id` 自增，`order` 决定页签顺序，`isHidden` 为隐藏分类。 */
data class LibraryCategory(
    val id: Long,
    val name: String,
    val order: Long,
    val isHidden: Boolean = false,
    val isDefault: Boolean = false,
)

/** 五种显示模式，对齐上游 `LibraryDisplayMode`（含 KMK 全景与封面网格）。 */
enum class LibraryDisplayMode {
    COMPACT_GRID,
    COMFORTABLE_GRID,
    COMFORTABLE_GRID_PANORAMA,
    LIST,
    COVER_ONLY_GRID,
    ;

    fun serialize(): String = when (this) {
        COMPACT_GRID -> "COMPACT_GRID"
        COMFORTABLE_GRID -> "COMFORTABLE_GRID"
        COMFORTABLE_GRID_PANORAMA -> "COMFORTABLE_GRID_PANORAMA"
        LIST -> "LIST"
        COVER_ONLY_GRID -> "COVER_ONLY_GRID"
    }

    companion object {
        val default: LibraryDisplayMode = COMPACT_GRID

        fun deserialize(serialized: String): LibraryDisplayMode = when (serialized) {
            "COMFORTABLE_GRID" -> COMFORTABLE_GRID
            "COMFORTABLE_GRID_PANORAMA" -> COMFORTABLE_GRID_PANORAMA
            "COMPACT_GRID" -> COMPACT_GRID
            "COVER_ONLY_GRID" -> COVER_ONLY_GRID
            "LIST" -> LIST
            else -> default
        }
    }
}

/** 九种排序（对齐上游 `LibrarySort.Type`，追踪均值与标签表本轮不做）。 */
enum class LibrarySortType {
    ALPHABETICAL,
    LAST_READ,
    LAST_UPDATE_CHECK,
    UNREAD_COUNT,
    TOTAL_CHAPTERS,
    LATEST_CHAPTER,
    CHAPTER_FETCH_DATE,
    DATE_ADDED,
    RANDOM,
}

/** 筛选三态：关闭 / 只看命中 / 排除命中（对齐上游 TriStateItem 语义）。 */
enum class LibraryTriState {
    DISABLED,
    INCLUDE,
    EXCLUDE,
    ;

    /** 复选行点按循环：关闭 -> 只看 -> 排除 -> 关闭。 */
    fun next(): LibraryTriState = when (this) {
        DISABLED -> INCLUDE
        INCLUDE -> EXCLUDE
        EXCLUDE -> DISABLED
    }
}

/** 六组筛选（对齐上游 FilterPage 六个 TriState 行 + 分类编辑入口）。 */
data class LibraryFilters(
    val downloaded: LibraryTriState = LibraryTriState.DISABLED,
    val unread: LibraryTriState = LibraryTriState.DISABLED,
    val started: LibraryTriState = LibraryTriState.DISABLED,
    val bookmarked: LibraryTriState = LibraryTriState.DISABLED,
    val completed: LibraryTriState = LibraryTriState.DISABLED,
    val lewd: LibraryTriState = LibraryTriState.DISABLED,
)

/** 分组方式：分类 / 图源 / 状态（对齐上游 GroupPage 三行）。 */
enum class LibraryGroupBy {
    CATEGORY,
    SOURCE,
    STATUS,
}

/** 书架偏好（显示 / 筛选 / 排序 / 分组）。落盘字段，跨会话恢复。 */
data class LibraryPrefs(
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.COMPACT_GRID,
    val sortType: LibrarySortType = LibrarySortType.ALPHABETICAL,
    val ascending: Boolean = true,
    val filters: LibraryFilters = LibraryFilters(),
    val groupBy: LibraryGroupBy = LibraryGroupBy.CATEGORY,
    /** 显示页开关：未读徽标 / 下载徽标 / 语言徽标 / 继续按钮 / 分类标签 / 隐藏分类 / 作品数。 */
    val showUnreadBadges: Boolean = true,
    val showDownloadBadges: Boolean = true,
    val showLanguageBadges: Boolean = true,
    val showContinueButton: Boolean = true,
    val showCategoryLabels: Boolean = true,
    val showHiddenCategories: Boolean = false,
    val showWorkCount: Boolean = true,
    /** 随机排序的确定性种子：同一种子同数据顺序必同（单测断言用）。 */
    val randomSeed: Long = 0L,
)

/** 收藏入库时的写入载荷（分类归属可选，缺省进入默认分类）。 */
data class NewLibraryEntry(
    val sourceKey: String,
    val kind: LibraryKind = LibraryKind.COMIC,
    val workId: String,
    val title: String = "",
    val coverUrl: String = "",
    val sourceName: String = "",
    val language: String = "",
    val isLocal: Boolean = false,
    val isNsfw: Boolean = false,
    val status: LibraryStatus = LibraryStatus.UNKNOWN,
    val totalChapters: Int = 0,
    val categoryIds: Set<Long> = emptySet(),
)
