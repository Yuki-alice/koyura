package dev.reader.data.extension

import app.cash.sqldelight.db.SqlDriver
import dev.reader.data.extension.ExtensionDb
import dev.reader.domain.extension.ExtensionFailure
import dev.reader.domain.extension.InstalledExtensionRecord
import dev.reader.domain.extension.OriginRecord
import dev.reader.domain.extension.RepositoryRecord
import dev.reader.source.runtime.KtorVeneraHttpTransport
import dev.reader.source.runtime.VeneraCatalogEntry
import dev.reader.source.runtime.VeneraEnvironment
import dev.reader.source.runtime.VeneraSourceRuntime
import dev.reader.source.runtime.isVeneraUpdateAvailable
import dev.reader.source.runtime.loadVeneraCatalog
import dev.reader.source.runtime.normalizeVeneraUrl
import dev.reader.source.runtime.selectCatalogEntry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path

private fun sanitizeFileName(key: String): String {
    val mapped = key.map { c ->
        if (c.isLetterOrDigit() || c == '_' || c == '.' || c == '(' || c == ')' || c == '-') c else '_'
    }.joinToString("")
    return mapped.ifBlank { "source" }
}

/**
 * Injectable extension lifecycle service (milestones 2-3).
 *
 * Persistence split:
 * - SQLDelight ([SqlDriver]/[ExtensionDb]): repository configs, install origins,
 *   extension revisions.
 * - Okio ([FileSystem] + [scriptsDir]): raw script bytes `<sanitized-key>.js`.
 *
 * Commit order (install/replace): validate in a scratch QuickJS runtime
 * (expected-key + metadata + `init`), write temp file + atomic move, DB
 * transaction (extension row + origin row), then register into the live runtime.
 * Any validation/write failure leaves old files, rows and live instances
 * untouched. A live-registration failure rolls file + rows back and re-registers
 * the previous revision when replacing.
 *
 * Crash recovery ([open]/[restore]): deletes `*.new` leftovers, then for every
 * extension row checks the script file exists; missing files are reported and
 * skipped (row kept for reinstall/uninstall), parse failures are reported and
 * skipped. A file without a row is deleted as an orphan.
 */
class ExtensionService(
    driver: SqlDriver,
    private val fileSystem: FileSystem,
    private val scriptsDir: Path,
    private val httpClient: HttpClient,
    private val environment: VeneraEnvironment,
) {
    private val db = ExtensionDb(driver)
    private val mutex = Mutex()
    private val updating = mutableSetOf<String>()
    var runtime: VeneraSourceRuntime? = null
        private set

    private fun queries() = db.extensionDbQueries

    // ---- repositories (SQL-backed mirror of the milestone-1 protocol) ----

    fun listRepositories(): List<RepositoryRecord> =
        queries().selectAllRepositories().executeAsList().map {
            RepositoryRecord(it.id, it.name, it.url, it.enabled != 0L)
        }

    fun addRepository(name: String, url: String): RepositoryRecord {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ExtensionFailure.StorageFailure(IllegalArgumentException("missing repository name"))
        val normalized = try {
            normalizeVeneraUrl(url)
        } catch (e: Exception) {
            throw ExtensionFailure.StorageFailure(e)
        }
        if (queries().selectAllRepositories().executeAsList().any { it.url == normalized }) {
            throw ExtensionFailure.StorageFailure(IllegalStateException("duplicate repository url: $normalized"))
        }
        val record = RepositoryRecord(Uuid.random().toString(), trimmed, normalized)
        queries().insertRepository(record.id, record.name, record.url, 1L)
        return record
    }

    fun setRepositoryEnabled(id: String, enabled: Boolean) {
        queries().selectRepositoryById(id).executeAsOneOrNull()
            ?: throw ExtensionFailure.StorageFailure(NoSuchElementException("missing repository: $id"))
        queries().updateRepositoryEnabled(if (enabled) 1L else 0L, id)
    }

    fun removeRepository(id: String) {
        val current = queries().selectRepositoryById(id).executeAsOneOrNull()
            ?: throw ExtensionFailure.StorageFailure(NoSuchElementException("missing repository: $id"))
        db.transaction {
            queries().deleteRepository(id)
            queries().snapshotOriginsRepositoryName(current.name, current.id)
        }
    }

    fun originFor(key: String): OriginRecord? {
        val row = queries().selectOriginByKey(key).executeAsOneOrNull() ?: return null
        return OriginRecord(row.source_key, row.kind, row.repository_id, row.repository_name, row.url)
    }

    // ---- lifecycle ----

    /** Creates dirs + schema state, cleans temp leftovers. Call once before use. */
    suspend fun open() {
        fileSystem.createDirectories(scriptsDir)
        fileSystem.listOrNull(scriptsDir)?.forEach { path ->
            if (path.name.endsWith(".new")) fileSystem.delete(path, mustExist = false)
        }
    }

    /**
     * Registers persisted revisions into a new live runtime. Returns failures
     * (missing file / parse-init error) without throwing; rows are kept.
     */
    suspend fun restore(): RestoreReport {
        val failures = mutableListOf<RestoreFailure>()
        val live = VeneraSourceRuntime.create(environment, KtorVeneraHttpTransport(httpClient))
        runtime?.close()
        runtime = live
        // Orphan files (no row) are removed.
        val rows = queries().selectAllExtensions().executeAsList()
        val knownFiles = rows.map { it.file_name }.toSet()
        fileSystem.listOrNull(scriptsDir)?.forEach { path ->
            if (path.name.endsWith(".js") && path.name !in knownFiles) {
                fileSystem.delete(path, mustExist = false)
            }
        }
        for (row in rows) {
            val scriptPath = scriptsDir / row.file_name
            val bytes = try {
                fileSystem.read(scriptPath) { readByteArray() }
            } catch (e: Exception) {
                failures += RestoreFailure(row.source_key, e)
                continue
            }
            try {
                live.register(bytes.decodeToString(), row.file_name, expectedKey = row.source_key)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failures += RestoreFailure(row.source_key, e)
            }
        }
        return RestoreReport(registered = rows.size - failures.size, failures = failures.toList())
    }

    suspend fun installFromCatalog(repositoryId: String, entry: VeneraCatalogEntry): InstalledExtensionRecord =
        mutex.withLock {
            val repo = queries().selectRepositoryById(repositoryId).executeAsOneOrNull()
                ?: throw ExtensionFailure.StorageFailure(NoSuchElementException("missing repository: $repositoryId"))
            if (repo.id != repositoryId || repo.url.let { normalizeVeneraUrl(it) } != normalizeVeneraUrl(repo.url)) {
                throw ExtensionFailure.StorageFailure(IllegalStateException("repository changed"))
            }
            val script = downloadScript(entry.url)
            installValidated(
                script = script,
                expectedKey = entry.key,
                name = entry.name,
                version = entry.version,
                originKind = "repository",
                originRepositoryId = repo.id,
                originRepositoryName = repo.name,
                originUrl = entry.url,
            )
        }

    suspend fun installFromUrl(url: String, expectedKey: String? = null): InstalledExtensionRecord =
        mutex.withLock {
            val normalized = try {
                normalizeVeneraUrl(url)
            } catch (e: Exception) {
                throw ExtensionFailure.StorageFailure(e)
            }
            val script = downloadScript(normalized)
            installValidated(
                script = script,
                expectedKey = expectedKey,
                name = null,
                version = null,
                originKind = "url",
                originUrl = normalized,
            )
        }

    suspend fun installFromContent(name: String, content: String, expectedKey: String? = null): InstalledExtensionRecord =
        mutex.withLock {
            installValidated(
                script = content,
                expectedKey = expectedKey,
                name = name.ifBlank { null },
                version = null,
                originKind = "file",
            )
        }

    suspend fun uninstall(key: String) {
        mutex.withLock {
            val row = queries().selectExtensionByKey(key).executeAsOneOrNull()
                ?: throw ExtensionFailure.MissingExtension(key)
            val live = runtime
            // Unregister first so a missing runtime still allows file+row cleanup.
            try {
                live?.unregister(key)
            } catch (_: Exception) {
            }
            fileSystem.delete(scriptsDir / row.file_name, mustExist = false)
            db.transaction {
                queries().deleteExtension(key)
                queries().deleteOrigin(key)
            }
        }
    }

    suspend fun checkUpdates(): Map<String, String> {
        val live = runtime ?: return emptyMap()
        val updates = mutableMapOf<String, String>()
        val rows = queries().selectAllExtensions().executeAsList()
        for (row in rows) {
            val origin = queries().selectOriginByKey(row.source_key).executeAsOneOrNull()
            val repoId = origin?.repository_id
            val repo = repoId?.let { queries().selectRepositoryById(it).executeAsOneOrNull() }
            if (repo == null || repo.enabled == 0L) continue
            val catalog = try {
                loadVeneraCatalog(httpClient, repo.url)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                continue
            }
            val entry = try {
                selectCatalogEntry(row.source_key, catalog.entries.map {
                    VeneraCatalogEntry(it.key, it.name, it.version, it.url, it.description)
                }, origin?.url)
            } catch (_: Exception) {
                continue
            }
            if (isVeneraUpdateAvailable(entry.version, row.version)) {
                updates[row.source_key] = entry.version
            }
        }
        return updates
    }

    suspend fun update(key: String): InstalledExtensionRecord {
        mutex.withLock {
            if (!updating.add(key)) throw ExtensionFailure.UpdateInProgress(key)
        }
        try {
            return mutex.withLock {
                val row = queries().selectExtensionByKey(key).executeAsOneOrNull()
                    ?: throw ExtensionFailure.MissingExtension(key)
                val origin = queries().selectOriginByKey(key).executeAsOneOrNull()
                val repo = origin?.repository_id?.let { queries().selectRepositoryById(it).executeAsOneOrNull() }
                val targetUrl = if (repo != null && repo.enabled != 0L) {
                    val catalog = loadVeneraCatalog(httpClient, repo.url)
                    val entries = catalog.entries.map {
                        VeneraCatalogEntry(it.key, it.name, it.version, it.url, it.description)
                    }
                    try {
                        selectCatalogEntry(key, entries, origin?.url)
                    } catch (e: dev.reader.source.runtime.VeneraCatalogError) {
                        throw ExtensionFailure.MissingSource(key)
                    }.url
                } else {
                    // Upstream fallback: script-declared URL.
                    val live = runtime ?: throw ExtensionFailure.MissingExtension(key)
                    val source = live.registeredSources().firstOrNull { it.key == key }
                        ?: throw ExtensionFailure.MissingExtension(key)
                    val selfUrl = source.url ?: throw ExtensionFailure.MissingSource(key)
                    try {
                        normalizeVeneraUrl(selfUrl)
                    } catch (e: Exception) {
                        throw ExtensionFailure.StorageFailure(e)
                    }
                }
                val script = downloadScript(targetUrl)
                replaceValidated(key, row, script, targetUrl, repo)
            }
        } finally {
            mutex.withLock { updating.remove(key) }
        }
    }

    suspend fun close() {
        runtime?.close()
        runtime = null
    }

    // ---- internals ----

    private suspend fun downloadScript(url: String): String {
        try {
            val response = httpClient.get(url)
            if (response.status.value != 200) {
                throw ExtensionFailure.StorageFailure(IllegalStateException("source server HTTP ${response.status.value}"))
            }
            return response.bodyAsText()
        } catch (e: ResponseException) {
            throw ExtensionFailure.StorageFailure(IllegalStateException("source server HTTP ${e.response.status.value}", e))
        }
    }

    private suspend fun validateInScratch(script: String, fileName: String, expectedKey: String?): Triple<String, String, String> {
        val scratch = VeneraSourceRuntime.create(environment, KtorVeneraHttpTransport(httpClient))
        try {
            val source = scratch.register(script, fileName, expectedKey = expectedKey)
            if (expectedKey != null && source.key != expectedKey) {
                throw ExtensionFailure.KeyMismatch(expectedKey, source.key)
            }
            return Triple(source.key, source.name, source.version)
        } catch (e: ExtensionFailure) {
            throw e
        } catch (e: IllegalArgumentException) {
            if (expectedKey != null && e.message?.startsWith("Expected source") == true) {
                val actual = Regex("got '([^']*)'").find(e.message!!)?.groupValues?.getOrNull(1)
                throw ExtensionFailure.KeyMismatch(expectedKey, actual)
            }
            throw ExtensionFailure.InvalidScript(e)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw ExtensionFailure.InvalidScript(e)
        } finally {
            scratch.close()
        }
    }

    private suspend fun installValidated(
        script: String,
        expectedKey: String?,
        name: String?,
        version: String?,
        originKind: String,
        originRepositoryId: String? = null,
        originRepositoryName: String? = null,
        originUrl: String? = null,
    ): InstalledExtensionRecord {
        val fileProbe = sanitizeFileName(expectedKey ?: name ?: "source") + ".js"
        val (key, parsedName, parsedVersion) = validateInScratch(script, fileProbe, expectedKey)
        if (queries().selectExtensionByKey(key).executeAsOneOrNull() != null) {
            throw ExtensionFailure.AlreadyInstalled(key)
        }
        val finalName = name ?: parsedName
        val finalVersion = version ?: parsedVersion
        val fileName = sanitizeFileName(key) + ".js"
        val target = scriptsDir / fileName
        val temp = scriptsDir / "$fileName.new"
        try {
            fileSystem.write(temp) { writeUtf8(script) }
            fileSystem.atomicMove(temp, target)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Cleanup must never mask the original failure (e.g. POSIX ENOTDIR
            // when the scripts dir itself is a file on Apple platforms).
            runCatching { fileSystem.delete(temp, mustExist = false) }
            throw ExtensionFailure.StorageFailure(e)
        }
        try {
            db.transaction {
                queries().upsertExtension(key, finalName, finalVersion, fileName, originKind, originRepositoryId, originRepositoryName, originUrl)
                queries().upsertOrigin(key, originKind, originRepositoryId, originRepositoryName, originUrl)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            fileSystem.delete(target, mustExist = false)
            throw ExtensionFailure.StorageFailure(e)
        }
        val live = runtime ?: throw ExtensionFailure.StorageFailure(IllegalStateException("service not restored"))
        try {
            live.register(script, fileName, expectedKey = key)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Roll back file + rows; live instance never admitted.
            fileSystem.delete(target, mustExist = false)
            db.transaction {
                queries().deleteExtension(key)
                queries().deleteOrigin(key)
            }
            throw e
        }
        return InstalledExtensionRecord(key, finalName, finalVersion, fileName, originKind, originRepositoryId, originRepositoryName, originUrl)
    }

    private suspend fun replaceValidated(
        key: String,
        row: dev.reader.data.extension.Extensions,
        script: String,
        originUrl: String?,
        repo: dev.reader.data.extension.Repositories?,
    ): InstalledExtensionRecord {
        val fileName = row.file_name
        val target = scriptsDir / fileName
        val oldBytes: ByteArray? = try {
            fileSystem.read(target) { readByteArray() }
        } catch (_: Exception) {
            null
        }
        val (_, parsedName, parsedVersion) = validateInScratch(script, fileName, key)
        val temp = scriptsDir / "$fileName.new"
        try {
            fileSystem.write(temp) { writeUtf8(script) }
            fileSystem.atomicMove(temp, target)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Cleanup must never mask the original failure (e.g. POSIX ENOTDIR
            // when the scripts dir itself is a file on Apple platforms).
            runCatching { fileSystem.delete(temp, mustExist = false) }
            throw ExtensionFailure.StorageFailure(e)
        }
        val newOriginUrl = originUrl ?: row.origin_url
        try {
            db.transaction {
                queries().upsertExtension(key, parsedName, parsedVersion, fileName, row.origin_kind, row.origin_repository_id, row.origin_repository_name, newOriginUrl)
                queries().upsertOrigin(key, row.origin_kind, row.origin_repository_id, row.origin_repository_name, newOriginUrl)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (oldBytes != null) {
                fileSystem.write(target) { write(oldBytes) }
            } else {
                fileSystem.delete(target, mustExist = false)
            }
            throw ExtensionFailure.StorageFailure(e)
        }
        val live = runtime ?: throw ExtensionFailure.StorageFailure(IllegalStateException("service not restored"))
        val previous = live.registeredSources().firstOrNull { it.key == key }
        try {
            if (previous != null) live.unregister(key)
            live.register(script, fileName, expectedKey = key)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Roll back file + rows, re-admit previous revision when possible.
            if (oldBytes != null) {
                fileSystem.write(target) { write(oldBytes) }
            }
            db.transaction {
                queries().upsertExtension(row.source_key, row.name, row.version, row.file_name, row.origin_kind, row.origin_repository_id, row.origin_repository_name, row.origin_url)
            }
            try {
                if (oldBytes != null) live.register(oldBytes.decodeToString(), fileName, expectedKey = key)
            } catch (_: Exception) {
            }
            throw e
        }
        if (repo != null) {
            queries().upsertOrigin(key, row.origin_kind, repo.id, repo.name, newOriginUrl)
        }
        return InstalledExtensionRecord(key, parsedName, parsedVersion, fileName, row.origin_kind, row.origin_repository_id, row.origin_repository_name, newOriginUrl)
    }
}

data class RestoreFailure(val key: String, val cause: Throwable)
data class RestoreReport(val registered: Int, val failures: List<RestoreFailure>)
