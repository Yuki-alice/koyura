package app.koyura.app.history

import app.koyura.data.history.HistoryStore

/**
 * Production [HistoryService] backed by the persistent progress table.
 *
 * Reads only; writes happen through the reader's save path, so browsing history
 * never needs the network.
 */
class VeneraHistoryService(private val store: HistoryStore) : HistoryService {
    override suspend fun loadHistory(): List<HistoryEntry> =
        store.latestEntries().map { it.toEntry() }
}
