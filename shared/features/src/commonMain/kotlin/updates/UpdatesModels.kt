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

/**
 * 更新数据源。`loadUpdates` 读上次扫描的持久 feed；[checkNow] 触发一次真实扫描并返回
 * 新 feed；[retryFailures] 只重试上次失败的作品。默认实现把两者等同 `loadUpdates`，
 * Fake 与无引擎装配时行为不变。
 */
interface UpdatesRepository {
    suspend fun loadUpdates(): List<UpdateWork>
    suspend fun checkNow(): List<UpdateWork> = loadUpdates()
    suspend fun retryFailures(): List<UpdateWork> = loadUpdates()
}

/** 更新列表加载态（对齐 Library / History 的三态）。 */
sealed interface UpdatesLoadState {
    data object Loading : UpdatesLoadState
    data object Ready : UpdatesLoadState
    data class Failed(val message: String) : UpdatesLoadState
}
