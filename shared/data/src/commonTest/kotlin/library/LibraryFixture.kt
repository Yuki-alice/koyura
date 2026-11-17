package app.koyura.data.library

import app.koyura.data.history.HistoryStore
import app.koyura.domain.library.WorkKind

/**
 * 书架固定数据集（覆盖分类 / 排序 / 搜索语义）。
 *
 * 契约要求的数据形状（`LibraryMigrationTest` 复用同一形状做升级断言）：
 * - 同名不同源两条：`w1@s1` 与 `w9@s2`，标题均为“同名作品”；
 * - 同源同名不同 ID 两条：`w2@s1` 与 `w3@s1`，标题均为“连载中”；
 * - 无分类作品一条：`w4@s1`；
 * - 隐藏分类作品一条：`w5@s1`， exclusive 归属隐藏分类“隐藏存档”；
 * - 空分类一个：“空分类”（无作品）；
 * - chapterless 作品一条：`w6@s1`“单行本”，进度 `chapterId` 为 null（落库为空字符串）。
 *
 * 注：契约字面要求该夹具落在 `shared/app/.../library/LibraryFixture.kt`，
 * 该路径归主 agent / A 所有，本轨只写 data/domain 自有目录；此处的实现即该夹具的
 * 规范内容，集成负责人按序将其转存或复出口到 app 路径（见提交 notes）。
 */
data class SeededLibrary(
    val ongoingId: Long,
    val hiddenArchiveId: Long,
    val emptyCategoryId: Long,
    val defaultId: Long,
)

object LibraryFixture {
    const val SAME_TITLE = "同名作品"
    const val ONGOING_TITLE = "连载中"

    suspend fun seed(store: LibraryStore, history: HistoryStore? = null): SeededLibrary {
        val ongoing = store.createCategory("连载")
        val hiddenArchive = store.createCategory("隐藏存档")
        store.setCategoryHidden(hiddenArchive.id, true)
        val empty = store.createCategory("空分类")

        store.addToLibrary("s1", WorkKind.COMIC, "w1", SAME_TITLE, "https://example.invalid/w1.png", "源一", 1000L)
        store.setWorkCategories("s1", WorkKind.COMIC, "w1", listOf(ongoing.id))
        store.addToLibrary("s2", WorkKind.COMIC, "w9", SAME_TITLE, "https://example.invalid/w9.png", "源二", 2000L)
        store.setWorkCategories("s2", WorkKind.COMIC, "w9", listOf(ongoing.id))

        store.addToLibrary("s1", WorkKind.COMIC, "w2", ONGOING_TITLE, "", "源一", 3000L)
        store.setWorkCategories("s1", WorkKind.COMIC, "w2", listOf(ongoing.id))
        store.addToLibrary("s1", WorkKind.COMIC, "w3", ONGOING_TITLE, "", "源一", 4000L)
        store.setWorkCategories("s1", WorkKind.COMIC, "w3", listOf(ongoing.id))

        store.addToLibrary("s1", WorkKind.COMIC, "w4", "无分类作品", "", "源一", 5000L)

        store.addToLibrary("s1", WorkKind.COMIC, "w5", "隐藏分类作品", "", "源一", 6000L)
        store.setWorkCategories("s1", WorkKind.COMIC, "w5", listOf(hiddenArchive.id))

        store.addToLibrary("s1", WorkKind.COMIC, "w6", "单行本", "", "源一", 7000L)
        history?.save(
            sourceKey = "s1",
            workId = "w6",
            chapterId = null,
            pageKey = "s0",
            pageIndex = 0,
            readAtMillis = 1_700_000_000_000,
            workTitle = "单行本",
            chapterTitle = null,
        )

        val default = store.listCategories().single { it.isDefault }
        return SeededLibrary(
            ongoingId = ongoing.id,
            hiddenArchiveId = hiddenArchive.id,
            emptyCategoryId = empty.id,
            defaultId = default.id,
        )
    }
}
