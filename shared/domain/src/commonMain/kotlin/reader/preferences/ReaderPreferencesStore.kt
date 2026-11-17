package app.koyura.domain.reader.preferences

data class ReaderPreferencesSnapshot(
    val global: ReaderSettings = ReaderSettings(),
    val override: WorkReaderOverride? = null,
)

interface ReaderPreferences {
    suspend fun load(sourceKey: String, workId: String): ReaderPreferencesSnapshot
    suspend fun writeGlobal(settings: ReaderSettings)
    suspend fun writeOverride(sourceKey: String, workId: String, patch: WorkReaderOverridePatch)
    suspend fun clearOverride(sourceKey: String, workId: String)
}
