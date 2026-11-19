package app.koyura.features.updates

/**
 * 更新页 Fake 仓储：默认空（与白皮书 16_updates 空态截图一致）。
 *
 * 预置样例经 [sampleEntries] 注入，供测试与后续预览使用；真实源由引擎轨接入，
 * 届时调用方把本类换成真实仓储即可，页面与会话不动。
 */
class FakeUpdatesRepository(
    private val works: List<UpdateWork> = emptyList(),
    private val failure: String? = null,
) : UpdatesRepository {
    override suspend fun loadUpdates(): List<UpdateWork> {
        failure?.let { throw IllegalStateException(it) }
        return works
    }

    companion object {
        fun sampleEntries(): List<UpdateWork> = listOf(
            UpdateWork(
                sourceKey = "demo-source",
                workId = "kaze-no-kioku",
                title = "Kaze no Kioku",
                coverUrl = "",
                chapters = listOf(
                    UpdateChapter(id = "ch-3", title = "Chapter 3", dateText = "Today"),
                    UpdateChapter(id = "ch-2", title = "Chapter 2", dateText = "Yesterday"),
                ),
            ),
            UpdateWork(
                sourceKey = "demo-source",
                workId = "hoshi-no-utahime",
                title = "Hoshizora no Utahime",
                coverUrl = "",
                chapters = listOf(
                    UpdateChapter(id = "ch-12", title = "Chapter 12", dateText = "2 days ago"),
                ),
            ),
        )
    }
}
