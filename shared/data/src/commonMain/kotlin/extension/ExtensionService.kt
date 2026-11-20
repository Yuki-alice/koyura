package app.koyura.data.extension

import app.koyura.data.search.SavedSearchStore
import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.domain.extension.SourceAccountConfig
import app.koyura.domain.extension.SourceAccountField
import app.koyura.domain.extension.SourceAccountFieldType
import app.koyura.domain.extension.SourceAccountInfo
import app.koyura.domain.extension.SourceAccountInfoConfig
import app.koyura.domain.extension.SourceLoginState
import app.koyura.domain.extension.ExtensionFailure
import app.koyura.domain.extension.InstalledExtensionRecord
import app.koyura.domain.extension.OriginRecord
import app.koyura.domain.extension.RepositoryRecord
import app.koyura.domain.extension.UpdateCheckResult
import app.koyura.source.runtime.KtorVeneraHttpTransport
import app.koyura.source.runtime.SourceSessions
import app.koyura.source.runtime.VeneraCallback
import app.koyura.source.runtime.VeneraCatalog
import app.koyura.source.runtime.VeneraCatalogEntry
import app.koyura.source.runtime.VeneraCatalogError
import app.koyura.source.runtime.VeneraEnvironment
import app.koyura.source.runtime.VeneraUiHost
import app.koyura.source.runtime.VeneraSourceRuntime
import app.koyura.source.runtime.isVeneraUpdateAvailable
import app.koyura.source.runtime.loadVeneraCatalog
import app.koyura.source.runtime.normalizeVeneraUrl
import app.koyura.source.runtime.selectCatalogEntry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.buffer

private fun sanitizeFileName(key: String): String {
    val mapped = key.map { c ->
        if (c.isLetterOrDigit() || c == '_' || c == '.' || c == '(' || c == ')' || c == '-') c else '_'
    }.joinToString("")
    return mapped.ifBlank { "source" }
}

/** Immutable revision file identity: the name binds key, version and content, so a
 * same-version candidate with different bytes never overwrites the live file. */
internal fun revisionFileName(key: String, version: String, script: String): String {
    val blackhole = okio.Buffer()
    val hashing = HashingSink.sha256(blackhole)
    val buffered = hashing.buffer()
    buffered.writeUtf8(script)
    buffered.flush()
    val digest = hashing.hash.hex().take(16)
    return sanitizeFileName("${key}@${version}-${digest}") + ".js"
}

/**
 * Injectable extension lifecycle service (milestones 2-3, consistency round).
 *
 * Persistence split:
 * - SQLDelight ([SqlDriver]/[ExtensionDb]): repository configs, install origins,
 *   extension revisions. The extensions row is the commit pointer: `file_name`
 *   names the live immutable revision file.
 * - Okio ([FileSystem] + [scriptsDir]): immutable raw script bytes
 *   `<sanitized-key>@<version>.js`.
 *
 * Commit order:
 * - Install: row-exists check → live `register` (candidate `init` runs exactly
 *   once) → write revision file → DB transaction (extension row + origin row).
 *   File/DB failure after registration rolls the live instance back.
 * - Replace: peek metadata (no `init`) to name the revision → write new revision
 *   file → DB transaction moves the pointer → live `replace` (`init` once). The
 *   previous live instance (with its memory state) is never unregistered before
 *   the candidate succeeds; on candidate failure it stays callable.
 *
 * Crash recovery ([open]/[restore]): `*.new` leftovers are deleted; every row's
 * file must exist and parse — missing files are reported and skipped (row kept
 * for reinstall/uninstall retry); the installed version follows the actual
 * script metadata and a drifted row is healed. Files not referenced by any row
 * are deleted as orphans. A file written before its DB commit is therefore an
 * orphan or still shadows a complete old revision: restart restores exactly one
 * complete revision per key.
 */
class ExtensionService(
    driver: SqlDriver,
    private val fileSystem: FileSystem,
    private val scriptsDir: Path,
    private val httpClient: HttpClient,
    private val environment: VeneraEnvironment,
    private val sourceSessions: SourceSessions = SourceSessions(),
    webLoginBrowser: WebLoginBrowser? = null,
    private val sourceUi: VeneraUiHost = VeneraUiHost.Headless,
) {
    internal val sourceTransport = KtorVeneraHttpTransport(httpClient, sessions = sourceSessions)
    val accounts = SourceAccountService(this, webLoginBrowser)
    val browse = SourceBrowseService(this, SavedSearchStore(driver))

    private val db = ExtensionDb(driver)
    private val mutex = Mutex()
    private val updating = mutableSetOf<String>()
    private val loginGeneration = mutableMapOf<String, Long>()
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

    fun updateRepository(id: String, name: String, url: String): RepositoryRecord {
        val current = queries().selectRepositoryById(id).executeAsOneOrNull()
            ?: throw ExtensionFailure.StorageFailure(NoSuchElementException("missing repository: $id"))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ExtensionFailure.StorageFailure(IllegalArgumentException("missing repository name"))
        val normalized = try {
            normalizeVeneraUrl(url)
        } catch (e: Exception) {
            throw ExtensionFailure.StorageFailure(e)
        }
        if (queries().selectAllRepositories().executeAsList().any { it.id != id && it.url == normalized }) {
            throw ExtensionFailure.StorageFailure(IllegalStateException("duplicate repository url: $normalized"))
        }
        queries().updateRepository(trimmed, normalized, id)
        return RepositoryRecord(id, trimmed, normalized, current.enabled != 0L)
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

    // ---- browse entries: preview, installed list, per-key call ----

    /**
     * Loads a catalog for display without saving anything. Variants sharing a
     * key are identified by `(repositoryId, sourceKey, resolved entry URL)`.
     */
    suspend fun previewCatalog(repositoryId: String): Pair<String, VeneraCatalog> {
        val repo = queries().selectRepositoryById(repositoryId).executeAsOneOrNull()
            ?: throw ExtensionFailure.StorageFailure(NoSuchElementException("missing repository: $repositoryId"))
        return repo.url to loadVeneraCatalog(httpClient, repo.url)
    }

    suspend fun previewCatalogUrl(url: String): Pair<String, VeneraCatalog> {
        val normalized = try {
            normalizeVeneraUrl(url)
        } catch (e: Exception) {
            throw ExtensionFailure.StorageFailure(e)
        }
        return normalized to loadVeneraCatalog(httpClient, normalized)
    }

    fun listInstalled(): List<InstalledExtensionRecord> =
        queries().selectAllExtensions().executeAsList().map {
            InstalledExtensionRecord(
                it.source_key, it.name, it.version, it.file_name,
                it.origin_kind, it.origin_repository_id, it.origin_repository_name, it.origin_url,
            )
        }

    internal suspend fun <T> withBrowseRuntime(block: suspend (VeneraSourceRuntime?) -> T): T =
        mutex.withLock { block(runtime) }

    suspend fun call(key: String, callback: VeneraCallback, args: kotlinx.serialization.json.JsonArray): JsonElement {
        val live = runtime ?: throw ExtensionFailure.MissingExtension(key)
        if (live.registeredSources().none { it.key == key }) throw ExtensionFailure.MissingExtension(key)
        return live.call(key, callback, args)
    }

    // ---- lifecycle ----

    /** Creates dirs, deletes `*.new` leftovers. Call once before use. */
    suspend fun open() {
        fileSystem.createDirectories(scriptsDir)
        fileSystem.listOrNull(scriptsDir)?.forEach { path ->
            if (path.name.endsWith(".new")) runCatching { fileSystem.delete(path) }
        }
    }

    /**
     * Registers persisted revisions into a new live runtime. Missing files and
     * parse failures are reported and skipped (rows kept); a row whose version
     * drifted from its file's actual script metadata is healed to the file.
     * Unreferenced script files are deleted as orphans.
     */
    suspend fun restore(): RestoreReport {
        val failures = mutableListOf<RestoreFailure>()
        val live = VeneraSourceRuntime.create(environment, sourceSessions, sourceUi, sourceTransport)
        runtime?.close()
        runtime = live
        val rows = queries().selectAllExtensions().executeAsList()
        val referenced = rows.map { it.file_name }.toSet()
        fileSystem.listOrNull(scriptsDir)?.forEach { path ->
            if (path.name.endsWith(".js") && path.name !in referenced) {
                runCatching { fileSystem.delete(path) }
            }
        }
        var registered = 0
        for (row in rows) {
            val script = try {
                fileSystem.read(scriptsDir / row.file_name) { readUtf8() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failures += RestoreFailure(row.source_key, e)
                continue
            }
            try {
                val source = live.register(script, row.file_name, expectedKey = row.source_key)
                if (source.version != row.version || source.name != row.name) {
                    queries().upsertExtension(
                        row.source_key, source.name, source.version, row.file_name,
                        row.origin_kind, row.origin_repository_id, row.origin_repository_name, row.origin_url,
                    )
                }
                registered++
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failures += RestoreFailure(row.source_key, e)
            }
        }
        return RestoreReport(registered = registered, failures = failures.toList())
    }

    suspend fun installFromCatalog(
        repositoryId: String,
        entry: VeneraCatalogEntry,
        previewedRepoUrl: String,
    ): InstalledExtensionRecord = mutex.withLock {
        val repo = queries().selectRepositoryById(repositoryId).executeAsOneOrNull()
            ?: throw ExtensionFailure.StorageFailure(NoSuchElementException("missing repository: $repositoryId"))
        val previewed = try {
            normalizeVeneraUrl(previewedRepoUrl)
        } catch (e: Exception) {
            throw ExtensionFailure.StorageFailure(e)
        }
        if (previewed != repo.url) {
            throw ExtensionFailure.StaleCatalog(previewed, repo.url)
        }
        val script = downloadScript(entry.url)
        installValidated(
            script = script,
            expectedKey = entry.key,
            fileProbeName = entry.key,
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
                fileProbeName = expectedKey ?: "source",
                originKind = "url",
                originUrl = normalized,
            )
        }

    suspend fun installFromContent(name: String, content: String, expectedKey: String? = null): InstalledExtensionRecord =
        mutex.withLock {
            installValidated(
                script = content,
                expectedKey = expectedKey,
                fileProbeName = name.ifBlank { expectedKey ?: "source" },
                originKind = "file",
            )
        }

    suspend fun uninstall(key: String) {
        mutex.withLock {
            val row = queries().selectExtensionByKey(key).executeAsOneOrNull()
                ?: throw ExtensionFailure.MissingExtension(key)
            invalidateWebLogin(key)
            withContext(Dispatchers.IO) { sourceSessions.forget(key) }
            try {
                runtime?.unregister(key)
            } catch (_: Exception) {
            }
            // Best effort: a crash here leaves the row behind; restart reports
            // the missing file and a retry converges (missing files tolerated).
            runCatching { fileSystem.delete(scriptsDir / row.file_name) }
            deleteRevisionFiles(key, keep = null)
            db.transaction {
                queries().deleteExtension(key)
                queries().deleteOrigin(key)
            }
        }
    }

    suspend fun checkUpdates(): Map<String, String> = checkUpdatesDetailed().updates

    suspend fun checkUpdatesDetailed(): UpdateCheckResult {
        val updates = mutableMapOf<String, String>()
        val repoFailures = mutableMapOf<String, String>()
        val ambiguous = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val rows = queries().selectAllExtensions().executeAsList()
        val repoIds = rows.mapNotNull { queries().selectOriginByKey(it.source_key).executeAsOneOrNull()?.repository_id }.toSet()
        for (repoId in repoIds) {
            val repo = queries().selectRepositoryById(repoId).executeAsOneOrNull()
            if (repo == null || repo.enabled == 0L) continue
            val catalog = try {
                loadVeneraCatalog(httpClient, repo.url)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                repoFailures[repoId] = e.message ?: e.toString()
                continue
            }
            val entries = catalog.entries.map {
                VeneraCatalogEntry(it.key, it.name, it.version, it.url, it.description)
            }
            for (row in rows) {
                val origin = queries().selectOriginByKey(row.source_key).executeAsOneOrNull()
                if (origin?.repository_id != repoId) continue
                val entry = try {
                    selectCatalogEntry(row.source_key, entries, origin.url)
                } catch (e: VeneraCatalogError.AmbiguousSource) {
                    if (row.source_key !in ambiguous) ambiguous += row.source_key
                    continue
                } catch (e: VeneraCatalogError.MissingSource) {
                    if (row.source_key !in missing) missing += row.source_key
                    continue
                }
                if (isVeneraUpdateAvailable(entry.version, row.version)) {
                    updates[row.source_key] = entry.version
                }
            }
        }
        return UpdateCheckResult(updates, repoFailures, ambiguous, missing)
    }

    suspend fun update(key: String): InstalledExtensionRecord {
        mutex.withLock { if (!updating.add(key)) throw ExtensionFailure.UpdateInProgress(key) }
        try {
            return mutex.withLock {
                val row = try {
                    queries().selectExtensionByKey(key).executeAsOneOrNull()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    throw ExtensionFailure.StorageFailure(e)
                } ?: throw ExtensionFailure.MissingExtension(key)
                val origin = queries().selectOriginByKey(key).executeAsOneOrNull()
                val repo = origin?.repository_id?.let { queries().selectRepositoryById(it).executeAsOneOrNull() }
                val targetUrl = if (repo != null && repo.enabled != 0L) {
                    val catalog = loadVeneraCatalog(httpClient, repo.url)
                    val entries = catalog.entries.map {
                        VeneraCatalogEntry(it.key, it.name, it.version, it.url, it.description)
                    }
                    try {
                        selectCatalogEntry(key, entries, origin?.url)
                    } catch (e: VeneraCatalogError.AmbiguousSource) {
                        throw ExtensionFailure.AmbiguousSource(key)
                    } catch (e: VeneraCatalogError) {
                        throw ExtensionFailure.MissingSource(key)
                    }.url
                } else {
                    // Upstream fallback: the script's own declared URL.
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
                currentCoroutineContext().ensureActive()
                val script = downloadScript(targetUrl)
                currentCoroutineContext().ensureActive()
                replaceValidated(key, row, script, targetUrl, repo)
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { updating.remove(key) }
            }
        }
    }

    suspend fun sourceAccountConfig(key: String): SourceAccountConfig? = mutex.withLock {
        val live = runtime ?: return@withLock null
        if (live.liveInstanceSeq(key) == null) return@withLock null
        accountConfig(live, key)?.config
    }

    suspend fun sourceLoginState(key: String): SourceLoginState = mutex.withLock {
        if (runtime?.liveInstanceSeq(key) == null) return@withLock SourceLoginState.LOGGED_OUT
        val marker = withContext(Dispatchers.IO) { sourceSessions.accountMarker(key) }
        when {
            savedForm(marker) != null -> SourceLoginState.FORM
            marker == JsonPrimitive("ok") -> SourceLoginState.WEB
            else -> SourceLoginState.LOGGED_OUT
        }
    }

    suspend fun sourceAccountInfo(key: String): List<SourceAccountInfo> = mutex.withLock {
        val live = runtime ?: return@withLock emptyList()
        if (live.liveInstanceSeq(key) == null) return@withLock emptyList()
        (live.accountInfo(key) as JsonArray).map { item ->
            val row = item.jsonObject
            SourceAccountInfo(row.getValue("title").jsonPrimitive.content, row["value"]?.jsonPrimitive?.contentOrNull)
        }
    }

    private data class AccountForm(val config: SourceAccountConfig, val usesLogin: Boolean)

    private suspend fun accountConfig(live: VeneraSourceRuntime, key: String): AccountForm? {
        val value = live.describeAccount(key) as? JsonObject ?: return null
        val form = value["form"] == JsonPrimitive(true)
        val fields = when (val declared = value["fields"]) {
            is JsonArray -> declared.map { item ->
                val field = item.jsonObject
                val label = field.getValue("label").jsonPrimitive.content
                val type = when (field.getValue("type").jsonPrimitive.content.lowercase()) {
                    "username", "text" -> SourceAccountFieldType.TEXT
                    "password" -> SourceAccountFieldType.PASSWORD
                    "cookies" -> SourceAccountFieldType.COOKIES
                    else -> error("Unsupported source account field type")
                }
                SourceAccountField(label, type)
            }
            JsonNull, null -> if (form) listOf(
                SourceAccountField("Username", SourceAccountFieldType.TEXT),
                SourceAccountField("Password", SourceAccountFieldType.PASSWORD),
            ) else emptyList()
            else -> error("Invalid source account fields")
        }
        val info = (value.getValue("infoItems") as JsonArray).map {
            SourceAccountInfoConfig(it.jsonObject.getValue("title").jsonPrimitive.content, it.jsonObject["hasValue"] == JsonPrimitive(true))
        }
        val register = value["registerWebsite"]?.jsonPrimitive?.contentOrNull?.takeIf(WebLoginMonitor::allowedUrl)
        val config = SourceAccountConfig(fields, register, value["declaresWebLogin"] == JsonPrimitive(true), info)
        return AccountForm(config, form)
    }

    internal suspend fun loginSource(key: String, arguments: JsonArray) = mutex.withLock {
        val live = runtime ?: throw ExtensionFailure.MissingExtension(key)
        authenticateForm(live, key, arguments)
    }

    internal suspend fun loginSourceForm(key: String, values: JsonArray): Boolean = mutex.withLock {
        val live = runtime ?: throw ExtensionFailure.MissingExtension(key)
        val form = accountConfig(live, key) ?: return@withLock false
        require(values.size == form.config.fields.size) { "Incorrect number of source account fields" }
        if (form.usesLogin) {
            authenticateForm(live, key, values)
            true
        } else {
            if (form.config.fields.isEmpty()) return@withLock false
            authenticateCookies(live, key, JsonArray(listOf(values)))
        }
    }

    internal suspend fun reLoginSource(key: String): Boolean = mutex.withLock {
        val live = runtime ?: return@withLock false
        if (live.registeredSources().none { it.key == key && VeneraCallback.Login in it.callbacks }) return@withLock false
        val marker = withContext(Dispatchers.IO) { sourceSessions.accountMarker(key) }
        val args = savedForm(marker) ?: return@withLock false
        authenticateForm(live, key, args)
        true
    }

    private fun savedForm(marker: JsonElement): JsonArray? =
        (marker as? JsonArray)?.takeIf { values -> values.all { (it as? JsonPrimitive)?.isString == true } }

    private suspend fun authenticateForm(live: VeneraSourceRuntime, key: String, arguments: JsonArray) {
        sourceSessions.invalidateHttpCache(key)
        try {
            live.call(key, VeneraCallback.Login, arguments)
            withContext(Dispatchers.IO) { sourceSessions.saveFormLogin(key, arguments) }
        } finally {
            sourceSessions.invalidateHttpCache(key)
        }
        invalidateWebLogin(key)
    }

    internal suspend fun validateSourceCookies(key: String, values: JsonArray): Boolean = mutex.withLock {
        val live = runtime ?: throw ExtensionFailure.MissingExtension(key)
        authenticateCookies(live, key, values)
    }

    private suspend fun authenticateCookies(live: VeneraSourceRuntime, key: String, values: JsonArray): Boolean {
        sourceSessions.invalidateHttpCache(key)
        val accepted = try {
            live.call(key, VeneraCallback.ValidateCookies, values) == JsonPrimitive(true)
        } finally {
            sourceSessions.invalidateHttpCache(key)
        }
        if (accepted) {
            withContext(Dispatchers.IO) { sourceSessions.applyLoginMarker(key) }
            invalidateWebLogin(key)
        }
        return accepted
    }

    internal suspend fun logoutSource(key: String) = mutex.withLock {
        invalidateWebLogin(key)
        try {
            val live = runtime
            if (live?.registeredSources()?.any { it.key == key && VeneraCallback.Logout in it.callbacks } == true) {
                live.call(key, VeneraCallback.Logout)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { sourceSessions.clear(key) }
        }
    }

    internal suspend fun beginWebLogin(key: String): Pair<WebLoginOwner, String>? = mutex.withLock {
        val live = runtime ?: throw ExtensionFailure.MissingExtension(key)
        val instance = live.liveInstanceSeq(key) ?: throw ExtensionFailure.MissingExtension(key)
        val start = live.webLoginUrl(key) ?: return@withLock null
        invalidateWebLogin(key)
        WebLoginOwner(key, live, instance, loginGeneration.getValue(key)) to start
    }

    internal suspend fun checkWebLogin(owner: WebLoginOwner, arguments: JsonArray): Boolean = mutex.withLock {
        isCurrent(owner) && owner.runtime.call(owner.key, VeneraCallback.CheckWebLogin, arguments) == JsonPrimitive(true)
    }

    internal suspend fun finishWebLogin(owner: WebLoginOwner, capture: WebLoginCapture): Boolean = mutex.withLock {
        if (!isCurrent(owner)) return@withLock false
        withContext(Dispatchers.IO) {
            sourceSessions.saveWebLogin(owner.key, capture.page.url, capture.cookies, capture.localStorage)
        }
        if (owner.runtime.registeredSources().any { it.key == owner.key && VeneraCallback.WebLoginSucceeded in it.callbacks }) {
            owner.runtime.call(owner.key, VeneraCallback.WebLoginSucceeded)
        }
        true
    }

    private fun invalidateWebLogin(key: String) {
        loginGeneration[key] = (loginGeneration[key] ?: 0L) + 1
    }

    private suspend fun isCurrent(owner: WebLoginOwner): Boolean =
        runtime === owner.runtime && loginGeneration[owner.key] == owner.generation &&
            owner.runtime.liveInstanceSeq(owner.key) == owner.instance

    suspend fun runSourceSetting(key: String, name: String): JsonElement =
        (runtime ?: throw ExtensionFailure.MissingExtension(key)).runSetting(key, name)

    suspend fun setSourceSetting(key: String, name: String, value: JsonElement?) = mutex.withLock {
        if (runtime?.registeredSources()?.none { it.key == key } != false) throw ExtensionFailure.MissingExtension(key)
        withContext(Dispatchers.IO) { sourceSessions.setSetting(key, name, value) }
    }

    suspend fun close() {
        try {
            runtime?.close()
        } finally {
            runtime = null
            sourceTransport.close()
            withContext(NonCancellable + Dispatchers.IO) { sourceSessions.close() }
        }
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

    private suspend fun installValidated(
        script: String,
        expectedKey: String?,
        fileProbeName: String,
        originKind: String,
        originRepositoryId: String? = null,
        originRepositoryName: String? = null,
        originUrl: String? = null,
    ): InstalledExtensionRecord {
        val live = runtime ?: throw ExtensionFailure.StorageFailure(IllegalStateException("service not restored"))
        val fileProbe = sanitizeFileName(fileProbeName) + ".js"
        // Metadata first without init; the single initializing registration below
        // runs the candidate init exactly once. The persisted version always
        // follows the actual script metadata, never a caller-supplied label.
        val peeked = try {
            live.peekMetadata(script, fileProbe, expectedKey)
        } catch (e: IllegalArgumentException) {
            if (expectedKey != null && e.message?.startsWith("Expected source") == true) {
                val actual = Regex("got '([^']*)'").find(e.message!!)?.groupValues?.getOrNull(1)
                throw ExtensionFailure.KeyMismatch(expectedKey, actual)
            }
            throw ExtensionFailure.InvalidScript(e)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            currentCoroutineContext().ensureActive()
            throw ExtensionFailure.InvalidScript(e)
        }
        if (queries().selectExtensionByKey(peeked.key).executeAsOneOrNull() != null) {
            throw ExtensionFailure.AlreadyInstalled(peeked.key)
        }
        val registered = try {
            live.register(script, fileProbe, expectedKey = peeked.key)
        } catch (e: ExtensionFailure) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            currentCoroutineContext().ensureActive()
            throw ExtensionFailure.InvalidScript(e)
        }
        val fileName = revisionFileName(registered.key, registered.version, script)
        val target = scriptsDir / fileName
        val temp = scriptsDir / "$fileName.new"
        try {
            fileSystem.write(temp) { writeUtf8(script) }
            fileSystem.atomicMove(temp, target)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            runCatching { fileSystem.delete(temp, mustExist = false) }
            try {
                live.unregister(registered.key)
            } catch (_: Exception) {
            }
            throw ExtensionFailure.StorageFailure(e)
        }
        try {
            db.transaction {
                queries().upsertExtension(registered.key, registered.name, registered.version, fileName, originKind, originRepositoryId, originRepositoryName, originUrl)
                queries().upsertOrigin(registered.key, originKind, originRepositoryId, originRepositoryName, originUrl)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            runCatching { fileSystem.delete(target, mustExist = false) }
            try {
                live.unregister(registered.key)
            } catch (_: Exception) {
            }
            throw ExtensionFailure.StorageFailure(e)
        }
        return InstalledExtensionRecord(registered.key, registered.name, registered.version, fileName, originKind, originRepositoryId, originRepositoryName, originUrl)
    }

    private suspend fun replaceValidated(
        key: String,
        row: app.koyura.data.extension.Extensions,
        script: String,
        originUrl: String?,
        repo: app.koyura.data.extension.Repositories?,
    ): InstalledExtensionRecord {
        val live = runtime ?: throw ExtensionFailure.StorageFailure(IllegalStateException("service not restored"))
        // Metadata first (no init) so the immutable revision file can be named.
        // The persisted version always follows the actual script metadata.
        val peeked = live.peekMetadata(script, row.file_name, expectedKey = key)
        val fileName = revisionFileName(key, peeked.version, script)
        val target = scriptsDir / fileName
        val oldFileName = row.file_name
        if (fileName != oldFileName) {
            val temp = scriptsDir / "$fileName.new"
            try {
                fileSystem.write(temp) { writeUtf8(script) }
                fileSystem.atomicMove(temp, target)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                runCatching { fileSystem.delete(temp, mustExist = false) }
                throw ExtensionFailure.StorageFailure(e)
            }
        }
        currentCoroutineContext().ensureActive()
        val newOriginUrl = originUrl ?: row.origin_url
        // The pre-replace JS instance identity: a failed candidate must leave
        // exactly this instance registered.
        val oldSeq = runCatching { live.liveInstanceSeq(key) }.getOrNull()
        // Candidate, init and commit share one boundary: the live swap runs
        // first, and the DB pointer moves only after init succeeds. Anything
        // observed before that point (file, old rows, old instance) is a
        // complete revision, so an exit at any moment restarts consistently.
        try {
            sourceSessions.invalidateHttpCache(key)
            live.replace(key, script, fileName, expectedKey = key)
        } catch (e: Exception) {
            if (fileName != oldFileName) runCatching { fileSystem.delete(target, mustExist = false) }
            val cancelled = e is CancellationException || currentCoroutineContext()[Job]?.isActive == false
            if (cancelled && oldSeq != null) {
                // A stranded candidate (whose init never settles) leaves the JS
                // registry dirty. Put the exact displaced instance back first:
                // it keeps its memory state and needs no init rerun. Only when
                // there is nothing to displace back do we fall back to the
                // untouched old revision file.
                val stranded = runCatching { live.liveInstanceSeq(key) }.getOrNull()
                if (stranded != oldSeq) {
                    withContext(NonCancellable) {
                        val repaired = runCatching { live.repairDisplaced(key) }.getOrDefault(false)
                        if (!repaired) {
                            val oldBytes = runCatching { fileSystem.read(scriptsDir / oldFileName) { readUtf8() } }.getOrNull()
                            if (oldBytes != null) {
                                runCatching { live.replace(key, oldBytes, oldFileName, expectedKey = key) }
                            }
                        }
                    }
                }
                if (e is CancellationException) throw e
                currentCoroutineContext().ensureActive()
            }
            throw e
        }
        try {
            db.transaction {
                queries().upsertExtension(key, peeked.name, peeked.version, fileName, row.origin_kind, row.origin_repository_id, row.origin_repository_name, newOriginUrl)
                queries().upsertOrigin(key, row.origin_kind, row.origin_repository_id, row.origin_repository_name, newOriginUrl)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // The live instance already runs the new revision while the pointer
            // could not move: put the exact displaced instance back (no init
            // rerun, memory state intact) so rows, files and live agree again.
            // Only when there is nothing to displace back do we fall back to
            // the untouched old revision file.
            withContext(NonCancellable) {
                val repaired = runCatching { live.repairDisplaced(key) }.getOrDefault(false)
                if (!repaired) {
                    val oldBytes = runCatching { fileSystem.read(scriptsDir / oldFileName) { readUtf8() } }.getOrNull()
                    if (oldBytes != null) {
                        runCatching { live.replace(key, oldBytes, oldFileName, expectedKey = key) }
                    }
                }
            }
            if (fileName != oldFileName) runCatching { fileSystem.delete(target, mustExist = false) }
            throw ExtensionFailure.StorageFailure(e)
        }
        withContext(NonCancellable) {
            runCatching { live.finishReplacement(key) }
        }
        if (oldFileName != fileName) runCatching { fileSystem.delete(scriptsDir / oldFileName) }
        if (repo != null) {
            queries().upsertOrigin(key, row.origin_kind, repo.id, repo.name, newOriginUrl)
        }
        return InstalledExtensionRecord(key, peeked.name, peeked.version, fileName, row.origin_kind, row.origin_repository_id, row.origin_repository_name, newOriginUrl)
    }

    private fun deleteRevisionFiles(key: String, keep: String?) {
        val prefix = sanitizeFileName(key) + "@"
        fileSystem.listOrNull(scriptsDir)?.forEach { path ->
            if (!path.name.endsWith(".js")) return@forEach
            if (path.name == keep) return@forEach
            if (path.name == sanitizeFileName(key) + ".js" || path.name.startsWith(prefix)) {
                runCatching { fileSystem.delete(path) }
            }
        }
    }
}

data class RestoreFailure(val key: String, val cause: Throwable)
data class RestoreReport(val registered: Int, val failures: List<RestoreFailure>)
