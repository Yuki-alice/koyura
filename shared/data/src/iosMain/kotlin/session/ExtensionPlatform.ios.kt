package app.koyura.data.session

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.extension.WebLoginBrowser
import app.koyura.network.createDefaultHttpClient
import app.koyura.source.runtime.VeneraEnvironment
import app.koyura.source.runtime.VeneraUiHost
import co.touchlab.sqliter.JournalMode
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Default iOS data directory (Application Support/venera-ext), created on use.
 */
@OptIn(ExperimentalForeignApi::class)
fun defaultIosDataDir(): Path {
    val manager = NSFileManager.defaultManager
    val url = manager.URLForDirectory(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        null,
        true,
        null,
    ) ?: error("Application Support directory is unavailable")
    val dir = (url.path + "/venera-ext").toPath()
    FileSystem.SYSTEM.createDirectories(dir)
    return dir
}

/**
 * Opens the extension session in [appDataDir] (iOS; defaults to Application
 * Support). Scripts live under `<appDataDir>/scripts`; the database is the
 * SQLiter-managed `extensions.db`. The session owns the driver, HTTP client
 * and QuickJS runtime; release everything with [ExtensionSession.close]
 * (idempotent).
 */
@OptIn(ExperimentalForeignApi::class)
suspend fun openExtensionSession(
    appDataDir: Path = defaultIosDataDir(),
    environment: VeneraEnvironment,
    webLoginBrowser: WebLoginBrowser? = null,
    sourceUi: VeneraUiHost = VeneraUiHost.Headless,
): ExtensionSession {
    FileSystem.SYSTEM.createDirectories(appDataDir)
    val driver = createExtensionDriver()
    return openExtensionSession(
        driver = driver,
        fileSystem = FileSystem.SYSTEM,
        appDataDir = appDataDir,
        httpClient = createDefaultHttpClient(),
        environment = environment,
        webLoginBrowser = webLoginBrowser,
        sourceUi = sourceUi,
        secrets = IosSourceSecretStore(),
    )
}

internal fun createExtensionDriver(
    name: String = ExtensionDatabaseName,
    basePath: String? = null,
): NativeSqliteDriver = NativeSqliteDriver(
    schema = ExtensionDb.Schema,
    name = name,
    onConfiguration = { config ->
        config.copy(
            journalMode = JournalMode.WAL,
            extendedConfig = config.extendedConfig.copy(
                busyTimeout = ExtensionDatabaseBusyTimeoutMillis,
                basePath = basePath ?: config.extendedConfig.basePath,
            ),
        )
    },
)
