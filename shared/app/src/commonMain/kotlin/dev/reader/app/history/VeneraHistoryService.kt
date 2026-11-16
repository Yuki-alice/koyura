package dev.reader.app.history

import dev.reader.data.history.HistoryStore

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
