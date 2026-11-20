package app.koyura.data.session

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.network.createDefaultHttpClient
import app.koyura.source.runtime.VeneraEnvironment
import app.koyura.source.runtime.VeneraUiHost
import okio.FileSystem
import okio.Path
import java.util.Properties

/**
 * Opens the extension session in [appDataDir] (desktop JVM).
 *
 * Storage layout: `<appDataDir>/scripts` holds immutable revision scripts,
 * database `<appDataDir>/extensions.db` (SQLite file, created on first open). The
 * session owns the driver, HTTP client and QuickJS runtime; release
 * everything with [ExtensionSession.close] (idempotent).
 */
suspend fun openExtensionSession(
    appDataDir: Path,
    environment: VeneraEnvironment,
    sourceUi: VeneraUiHost = VeneraUiHost.Headless,
): ExtensionSession {
    FileSystem.SYSTEM.createDirectories(appDataDir)
    val driver = createExtensionDriver(appDataDir / ExtensionDatabaseName)
    return openExtensionSession(
        driver = driver,
        fileSystem = FileSystem.SYSTEM,
        appDataDir = appDataDir,
        httpClient = createDefaultHttpClient(),
        environment = environment,
        sourceUi = sourceUi,
        secrets = desktopSourceSecretStore(appDataDir),
    )
}

internal fun createExtensionDriver(path: Path): JdbcSqliteDriver =
    JdbcSqliteDriver("jdbc:sqlite:$path", Properties().apply {
        setProperty("journal_mode", "WAL")
        setProperty("busy_timeout", ExtensionDatabaseBusyTimeoutMillis.toString())
    })
