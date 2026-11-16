package app.koyura.app.reader

import kotlinx.coroutines.CancellationException

/**
 * In-memory [ReaderService] used by tests.
 *
 * Chapters are keyed by `(sourceKey, workId, chapterId)`; a [null] chapterId addresses works
 * without chapters. Page bytes are opaque payloads supplied by the test. Failures can be injected
 * per page key, and every call is recorded so tests can assert save discipline and stale-result
 * rejection.
 */
class FakeReaderService(
    chapters: Map<Triple<String, String, String?>, ChapterContent> = emptyMap(),
    pageBytes: Map<String, ByteArray> = emptyMap(),
) : ReaderService {
    var chapters: MutableMap<Triple<String, String, String?>, ChapterContent> = chapters.toMutableMap()
    var pageBytes: MutableMap<String, ByteArray> = pageBytes.toMutableMap()

    /** Saved positions by (sourceKey, workId). */
    val savedProgress = mutableMapOf<Pair<String, String>, ReaderProgress>()

    /** All saveProgress calls in order. */
    val saveCalls = mutableListOf<SaveCall>()

    /** Number of loadPageBytes calls per page key. */
    val loadCounts = mutableMapOf<String, Int>()

    /** Page keys that fail the next load, mapped to remaining failure counts. */
    val pageFailures = mutableMapOf<String, Int>()

    /** Gate support: calls whose key starts with [gateKey] wait for [gate]. */
    var gate: Gate? = null
    var gateKey: String? = null
    var gateConsumed = false

    data class SaveCall(
        val sourceKey: String,
        val workId: String,
        val chapterId: String?,
        val pageIndex: Int,
        val workTitle: String? = null,
        val chapterTitle: String? = null,
    )

    class Gate {
        private var release: (() -> Unit)? = null
        suspend fun await() = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            release = { continuation.resumeWith(Result.success(Unit)) }
        }

        fun open() {
            release?.invoke()
            release = null
        }
    }

    private suspend fun enter(key: String) {
        val currentGate = gate
        if (!gateConsumed && gateKey != null && key.startsWith(gateKey!!)) {
            gateConsumed = true
            currentGate?.await()
        }
    }

    override suspend fun readProgress(sourceKey: String, workId: String): ReaderProgress? {
        enter("progress:$sourceKey:$workId")
        return savedProgress[sourceKey to workId]
    }

    override suspend fun openChapter(sourceKey: String, workId: String, chapterId: String?): ChapterContent {
        enter("open:$sourceKey:$workId:$chapterId")
        val content = chapters[Triple(sourceKey, workId, chapterId)]
            ?: throw ReaderServiceException("Unknown chapter $chapterId")
        val saved = savedProgress[sourceKey to workId]
        // Continue where the reader left off when the saved chapter matches.
        val startPage = if (saved != null && saved.chapterId == chapterId) {
            saved.pageIndex.coerceIn(0, (content.pageKeys.size - 1).coerceAtLeast(0))
        } else {
            0
        }
        return content.copy(startPageIndex = startPage)
    }

    override suspend fun loadPageBytes(
        sourceKey: String,
        workId: String,
        chapterId: String?,
        imageKey: String,
    ): ByteArray {
        val pageKey = imageKey
        enter("page:$pageKey")
        loadCounts[pageKey] = (loadCounts[pageKey] ?: 0) + 1
        val remaining = pageFailures[pageKey] ?: 0
        if (remaining > 0) {
            pageFailures[pageKey] = remaining - 1
            throw ReaderServiceException("Failed to load $pageKey")
        }
        return pageBytes[pageKey] ?: throw ReaderServiceException("Unknown page $pageKey")
    }

    override suspend fun saveProgress(
        sourceKey: String,
        workId: String,
        chapterId: String?,
        pageKey: String,
        pageIndex: Int,
        workTitle: String?,
        chapterTitle: String?,
    ) {
        enter("save:$sourceKey:$workId:$chapterId:$pageIndex")
        saveCalls += SaveCall(sourceKey, workId, chapterId, pageIndex, workTitle, chapterTitle)
        savedProgress[sourceKey to workId] = ReaderProgress(chapterId, pageIndex)
    }
}

/** Builds a fake with one two-chapter work and one chapterless work. */
fun demoReaderService(): FakeReaderService = FakeReaderService(
    chapters = mutableMapOf(
        Triple("example_source", "demo", "ep_1") to ChapterContent(
            chapterId = "ep_1",
            title = "第一章",
            pageKeys = listOf("p0", "p1", "p2", "p3", "p4"),
            startPageIndex = 0,
        ),
        Triple("example_source", "demo", "ep_2") to ChapterContent(
            chapterId = "ep_2",
            title = "第二章",
            pageKeys = listOf("q0", "q1", "q2"),
            startPageIndex = 0,
        ),
        Triple("example_source", "single", null) to ChapterContent(
            chapterId = null,
            title = "",
            pageKeys = listOf("s0", "s1"),
            startPageIndex = 0,
        ),
    ),
    pageBytes = mutableMapOf(
        "p0" to "bytes-p0".encodeToByteArray(),
        "p1" to "bytes-p1".encodeToByteArray(),
        "p2" to "bytes-p2".encodeToByteArray(),
        "p3" to "bytes-p3".encodeToByteArray(),
        "p4" to "bytes-p4".encodeToByteArray(),
        "q0" to "bytes-q0".encodeToByteArray(),
        "q1" to "bytes-q1".encodeToByteArray(),
        "q2" to "bytes-q2".encodeToByteArray(),
        "s0" to "bytes-s0".encodeToByteArray(),
        "s1" to "bytes-s1".encodeToByteArray(),
    ),
)
