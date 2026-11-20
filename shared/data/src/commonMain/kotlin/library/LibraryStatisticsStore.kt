package app.koyura.data.library

import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.updates.LibraryUpdateScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

data class LibraryStatistics(
    val inLibrary: Int,
    val readDurationMillis: Long,
    val completedEntries: Int,
    val inGlobalUpdate: Int,
    val started: Int,
    val local: Int,
    val totalChapters: Int,
    val readChapters: Int,
    val downloadedChapters: Int,
)

class LibraryStatisticsStore internal constructor(driver: SqlDriver, private val updates: LibraryUpdateScanner) {
    private val db = ExtensionDb(driver)
    private val queries = db.statisticsQueries

    suspend fun load(): LibraryStatistics = withContext(Dispatchers.IO) {
        val eligible = updates.globalUpdateWorkCount()
        db.transactionWithResult {
            val library = queries.libraryStatistics().executeAsOne()
            LibraryStatistics(
                inLibrary = library.in_library.toInt(),
                readDurationMillis = queries.totalReadDuration().executeAsOne(),
                completedEntries = library.completed.toInt(),
                inGlobalUpdate = eligible,
                started = library.started.toInt(),
                local = library.local_works.toInt(),
                totalChapters = (library.total_chapters ?: 0).toInt(),
                readChapters = (library.read_chapters ?: 0).toInt(),
                downloadedChapters = queries.totalCompletedDownloads().executeAsOne().toInt(),
            )
        }
    }
}
