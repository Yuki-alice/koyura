package app.koyura.data.reader.preferences

import app.koyura.domain.reader.preferences.ReaderPreferences
import app.koyura.domain.reader.preferences.ReaderPreferencesSnapshot
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.domain.reader.preferences.WorkReaderOverridePatch
import app.koyura.domain.reader.preferences.patchedWith
import app.koyura.domain.reader.preferences.withOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * Test hook for the protected update path. Production code uses the default no-op.
 */
open class PreferenceUpdateHooks {
    /** Called before the update attempts the store mutex (outside the lock). */
    open suspend fun beforeUpdate(key: String) {}

    /** Called while holding the store mutex, after the old value was read, before commit. */
    open suspend fun afterRead(key: String) {}
}

/**
 * File-backed reader settings, independent from SQLite and the reading-progress files.
 *
 * Layout inside [directory]:
 * - `reader_global.json`: the global [ReaderSettings];
 * - `reader_overrides.json`: `"<sourceKey>/<workId>"` to [WorkReaderOverride].
 *
 * The application data directory is injected by the app assembly; [fileSystem] defaults to the
 * platform file system. All reads tolerate missing files and unknown or missing JSON fields by
 * falling back to defaults, so old configs keep working.
 *
 * Writes are serialized by a mutex: read, merge and commit happen in one protected update, so
 * overlapping patches merge instead of clobbering each other. Each write goes through a temp
 * file plus an atomic move, so a failed write leaves the previous config readable.
 * Call from a background dispatcher; small files only.
 */
class ReaderPreferencesStore(
    private val directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    },
    private val hooks: PreferenceUpdateHooks = PreferenceUpdateHooks(),
) : ReaderPreferences {
    private val globalFile: Path get() = directory / "reader_global.json"
    private val overridesFile: Path get() = directory / "reader_overrides.json"

    private val overridesSerializer = MapSerializer(String.serializer(), WorkReaderOverride.serializer())

    private val writeMutex = Mutex()

    suspend fun readGlobal(): ReaderSettings = withContext(Dispatchers.Default) {
        readJson(globalFile, ReaderSettings.serializer(), ReaderSettings())
    }

    override suspend fun writeGlobal(settings: ReaderSettings): Unit = withContext(Dispatchers.Default) {
        writeMutex.withLock {
            writeJson(globalFile, ReaderSettings.serializer(), settings)
        }
    }

    suspend fun readOverride(sourceKey: String, workId: String): WorkReaderOverride? =
        withContext(Dispatchers.Default) {
            readJson(overridesFile, overridesSerializer, emptyMap())[overrideKey(sourceKey, workId)]
        }

    /**
     * Merges [patch] into the stored override field-by-field. Read, merge and commit run in one
     * mutex-protected update, so two overlapping patches both survive.
     */
    override suspend fun writeOverride(sourceKey: String, workId: String, patch: WorkReaderOverridePatch) {
        withContext(Dispatchers.Default) {
            val key = overrideKey(sourceKey, workId)
            hooks.beforeUpdate(key)
            writeMutex.withLock {
                val current = readJson(overridesFile, overridesSerializer, emptyMap())
                hooks.afterRead(key)
                val merged = (current[key] ?: WorkReaderOverride()).patchedWith(patch)
                writeJson(overridesFile, overridesSerializer, current + (key to merged))
            }
        }
    }

    override suspend fun clearOverride(sourceKey: String, workId: String): Unit =
        withContext(Dispatchers.Default) {
            val key = overrideKey(sourceKey, workId)
            hooks.beforeUpdate(key)
            writeMutex.withLock {
                val current = readJson(overridesFile, overridesSerializer, emptyMap())
                hooks.afterRead(key)
                writeJson(overridesFile, overridesSerializer, current - key)
            }
        }

    override suspend fun load(sourceKey: String, workId: String): ReaderPreferencesSnapshot =
        withContext(Dispatchers.Default) {
            writeMutex.withLock {
                val global = readJson(globalFile, ReaderSettings.serializer(), ReaderSettings())
                val override = readJson(overridesFile, overridesSerializer, emptyMap())[overrideKey(sourceKey, workId)]
                ReaderPreferencesSnapshot(global, override)
            }
        }

    /** Effective settings for one work: its override merged over the global defaults. */
    suspend fun resolve(sourceKey: String, workId: String): ReaderSettings =
        load(sourceKey, workId).let { it.global.withOverride(it.override) }

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
