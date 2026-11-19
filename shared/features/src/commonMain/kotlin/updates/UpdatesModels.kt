package app.koyura.features.updates

/** 单个待读章节（Fake 数据侧只给展示串，相对时间由 Fake 预先算好）。 */
data class UpdateChapter(
    val id: String,
    val title: String,
    val dateText: String,
)

/** 按作品聚合的更新组：首行是领导者（封面 + 标题 + 未读数），下面跟章节行。 */
data class UpdateWork(
    val sourceKey: String,
    val workId: String,
    val title: String,
    val coverUrl: String,
    val chapters: List<UpdateChapter>,
)

/** 更新数据源：本轮只有 Fake 实现，引擎轨接真实源后替换注入。 */
interface UpdatesRepository {
    suspend fun loadUpdates(): List<UpdateWork>
}

/** 更新列表加载态（对齐 Library / History 的三态）。 */
sealed interface UpdatesLoadState {
    data object Loading : UpdatesLoadState
    data object Ready : UpdatesLoadState
    data class Failed(val message: String) : UpdatesLoadState
}
