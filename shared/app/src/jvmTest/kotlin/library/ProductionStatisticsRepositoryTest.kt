package app.koyura.app.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.library.LibraryChapter
import app.koyura.domain.library.WorkKind
import app.koyura.domain.updates.UpdateTitleMetadata
import app.koyura.features.statistics.StatisticsChapters
import app.koyura.features.statistics.StatisticsOverview
import app.koyura.features.statistics.StatisticsSnapshot
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ProductionStatisticsRepositoryTest {
    @Test
    fun emptyLibraryHasZeroCountsAndNoTrackerScore() = runTest {
        withDatabase { root ->
            val session = open(root)
            try {
                val result = ProductionStatisticsRepository(session.statistics).load()
                assertEquals(StatisticsSnapshot(), result)
                assertTrue(result.trackers.meanScore.isNaN())
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun progressAndDurationSurvivePositionWritesAndRestart() = runTest {
        withDatabase { root ->
            val session = open(root)
            try {
                session.library.addToLibrary("source", WorkKind.COMIC, "work", "Title", addedAtMillis = 1)
                session.library.syncChapters("source", WorkKind.COMIC, "work", listOf(
                    LibraryChapter("source", WorkKind.COMIC, "work", "chapter"),
                ))
                session.history.save("source", "work", "chapter", "page", 0, 1, null, null)
                session.history.addReadDuration("source", "work", "chapter", 5_000)
                session.history.save("source", "work", "chapter", "next", 1, 2, null, null)
                session.history.deleteEntry("source", "work")
                session.library.markChapterRead("source", WorkKind.COMIC, "work", "chapter", true)
                session.updates.setTitleMetadata("source", "work", UpdateTitleMetadata(completed = true))
            } finally {
                session.close()
            }
            val reopened = open(root)
            try {
                val result = ProductionStatisticsRepository(reopened.statistics).load()
                assertEquals(StatisticsOverview(1, 5_000, 1), result.overview)
                assertEquals(1, result.entries.started)
                assertEquals(0, result.entries.inGlobalUpdate)
                assertEquals(StatisticsChapters(total = 1, read = 1), result.chapters)
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun countsCompletedDownloadsAcrossWorksButExcludesFailedJobs() = runTest {
        withDatabase { root ->
            val driver = JdbcSqliteDriver("jdbc:sqlite:${root / "extensions.db"}")
            ExtensionDb.Schema.create(driver)
            val session = open(root, driver)
            try {
                val jobs = ExtensionDb(driver).downloadJobsQueries
                jobs.insertDownload("complete", "source", "not-in-library", "chapter")
                jobs.updateDownload("COMPLETE", 2, 2, "complete")
                jobs.insertDownload("failed", "source", "not-in-library", "other")
                jobs.updateDownload("FAILED", 0, null, "failed")
                assertEquals(StatisticsChapters(downloaded = 1), ProductionStatisticsRepository(session.statistics).load().chapters)
            } finally {
                session.close()
            }
        }
    }

    private suspend fun withDatabase(test: suspend (Path) -> Unit) {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "statistics-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(root)
        try {
            test(root)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    private suspend fun open(root: Path, driver: JdbcSqliteDriver = JdbcSqliteDriver("jdbc:sqlite:${root / "extensions.db"}")) =
        openExtensionSession(
            driver, FileSystem.SYSTEM, root, HttpClient(MockEngine { error("Unexpected network") }),
            VeneraEnvironment("1.16.0", "en", "test"),
        )
}
