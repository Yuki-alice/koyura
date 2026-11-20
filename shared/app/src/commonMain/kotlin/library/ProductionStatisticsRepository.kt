package app.koyura.app.library

import app.koyura.data.library.LibraryStatisticsStore
import app.koyura.features.statistics.StatisticsChapters
import app.koyura.features.statistics.StatisticsEntries
import app.koyura.features.statistics.StatisticsOverview
import app.koyura.features.statistics.StatisticsRepository
import app.koyura.features.statistics.StatisticsSnapshot
import app.koyura.features.statistics.StatisticsTrackers

class ProductionStatisticsRepository(private val store: LibraryStatisticsStore) : StatisticsRepository {
    override suspend fun load(): StatisticsSnapshot = store.load().let {
        StatisticsSnapshot(
            overview = StatisticsOverview(it.inLibrary, it.readDurationMillis, it.completedEntries),
            entries = StatisticsEntries(it.inGlobalUpdate, it.started, it.local),
            chapters = StatisticsChapters(it.totalChapters, it.readChapters, it.downloadedChapters),
            // Tracking has no production service yet. The missing score renders as N/A.
            trackers = StatisticsTrackers(trackedEntries = 0, meanScore = Double.NaN, used = 0),
        )
    }
}
