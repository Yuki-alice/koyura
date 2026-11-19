package app.koyura.features.updates

/**
 * 更新页 Fake 仓储：默认空（与白皮书 16_updates 空态截图一致）。
 *
 * 预置样例经 [sampleEntries]/[sampleFailures] 注入供测试使用；`retryFailures`
 * 清空失败集并返回条目（模拟一次成功的重试扫描）；真实源由引擎轨接入，
 * 届时调用方把本类换成真实仓储即可，页面与会话不动。
 */
class FakeUpdatesRepository(
    private val works: List<UpdateWork> = emptyList(),
    private val failure: String? = null,
    failed: List<UpdateFailure> = emptyList(),
) : UpdatesRepository {
    private val failures = failed.toMutableList()

    override suspend fun loadUpdates(): List<UpdateWork> {
        failure?.let { throw IllegalStateException(it) }
        return works
    }

    override suspend fun retryFailures(): List<UpdateWork> {
        failure?.let { throw IllegalStateException(it) }
        failures.clear()
        return works
    }

    override suspend fun failures(): List<UpdateFailure> {
        failure?.let { throw IllegalStateException(it) }
        return failures.toList()
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

        fun sampleFailures(): List<UpdateFailure> = listOf(
            UpdateFailure(
                sourceKey = "demo-source",
                workId = "midnight-ramen",
                title = "Midnight Ramen Club",
                message = "HTTP 429: too many requests",
            ),
            UpdateFailure(
                sourceKey = "demo-source",
                workId = "gone-work",
                title = "Gone Work",
                message = "HTTP 404: not found",
            ),
        )
    }
}
