package app.koyura.data.reader.preferences

import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.domain.reader.preferences.withOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * File-backed reader settings, independent from SQLite and the reading-progress files.
 *
 * Layout inside [directory]:
 * - `reader_global.json`: the global [ReaderSettings];
 * - `reader_overrides.json`: `"<sourceKey>/<workId>"` to [WorkReaderOverride].
 *
 * The application data directory is injected by the app assembly; [fileSystem] defaults to the
 * platform file system. All reads tolerate missing files and unknown or missing JSON fields by
 * falling back to defaults, so old configs keep working. Writes go through a temp file plus an
 * atomic move. Call from a background dispatcher; small files only.
 */
class ReaderPreferencesStore(
    private val directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    },
) {
    private val globalFile: Path get() = directory / "reader_global.json"
    private val overridesFile: Path get() = directory / "reader_overrides.json"

    private val overridesSerializer = MapSerializer(String.serializer(), WorkReaderOverride.serializer())

    suspend fun readGlobal(): ReaderSettings = withContext(Dispatchers.Default) {
        readJson(globalFile, ReaderSettings.serializer(), ReaderSettings())
    }

    suspend fun writeGlobal(settings: ReaderSettings): Unit = withContext(Dispatchers.Default) {
        writeJson(globalFile, ReaderSettings.serializer(), settings)
    }

    suspend fun readOverride(sourceKey: String, workId: String): WorkReaderOverride? =
        withContext(Dispatchers.Default) {
            readJson(overridesFile, overridesSerializer, emptyMap())[overrideKey(sourceKey, workId)]
        }

    /** Merges [patch] into the stored override field-by-field. */
    suspend fun writeOverride(sourceKey: String, workId: String, patch: WorkReaderOverride) {
        withContext(Dispatchers.Default) {
            val current = readJson(overridesFile, overridesSerializer, emptyMap())
            val key = overrideKey(sourceKey, workId)
            val merged = (current[key] ?: WorkReaderOverride()).patchedWith(patch)
            writeJson(overridesFile, overridesSerializer, current + (key to merged))
        }
    }

    suspend fun clearOverride(sourceKey: String, workId: String): Unit =
        withContext(Dispatchers.Default) {
            val current = readJson(overridesFile, overridesSerializer, emptyMap())
            writeJson(overridesFile, overridesSerializer, current - overrideKey(sourceKey, workId))
        }

    /** Effective settings for one work: its override merged over the global defaults. */
    suspend fun resolve(sourceKey: String, workId: String): ReaderSettings =
        withContext(Dispatchers.Default) {
            val global = readJson(globalFile, ReaderSettings.serializer(), ReaderSettings())
            global.withOverride(readJson(overridesFile, overridesSerializer, emptyMap())[overrideKey(sourceKey, workId)])
        }

    private fun overrideKey(sourceKey: String, workId: String): String = "$sourceKey/$workId"

    private fun <T> readJson(file: Path, serializer: kotlinx.serialization.KSerializer<T>, default: T): T {
        if (!fileSystem.exists(file)) return default
        return try {
            json.decodeFromString(serializer, fileSystem.read(file) { readUtf8() })
        } catch (_: Exception) {
            default
        }
    }

    private fun <T> writeJson(file: Path, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        fileSystem.createDirectories(directory)
        val tmp = directory / "${file.name}.tmp"
        fileSystem.write(tmp) { writeUtf8(json.encodeToString(serializer, value)) }
        fileSystem.atomicMove(tmp, file)
    }
}
