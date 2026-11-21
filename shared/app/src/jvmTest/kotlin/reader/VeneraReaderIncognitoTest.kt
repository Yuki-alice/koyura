package app.koyura.app.reader

import app.koyura.data.session.openExtensionSession
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/**
 * 隐身模式：落位照常（续读可用），历史行与阅读时长不记录。
 */
class VeneraReaderIncognitoTest {
    @Test
    fun incognitoSkipsHistoryButKeepsPosition() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "incognito-${Uuid.random()}"
        val environment = VeneraEnvironment("1.16.0", "en", "test")
        try {
            val session = openExtensionSession(root, environment)
            try {
                var incognito = true
                val reader = VeneraReaderService(
                    reading = session.comicReading,
                    history = session.history,
                    isIncognito = { incognito },
                )
                reader.saveProgress("src", "work", "ch", "page-1", 0, workTitle = "W", chapterTitle = "C")
                assertNotNull(session.comicReading.readProgress("src", "work", "ch"))
                // 位置照常落位，但不碰历史元数据：时间戳与标题保持空。
                val incognitoEntries = session.history.latestEntries()
                assertEquals(1, incognitoEntries.size)
                assertEquals(null, incognitoEntries.single().readAtMillis)

                incognito = false
                reader.saveProgress("src", "work", "ch", "page-1", 0, workTitle = "W", chapterTitle = "C")
                val tracked = session.history.latestEntries()
                assertEquals(1, tracked.size)
                assertNotNull(tracked.single().readAtMillis)
                assertEquals("W", tracked.single().workTitle)
            } finally {
                session.close()
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
