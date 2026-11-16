package dev.reader.data.session

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.reader.data.extension.ExtensionDb
import dev.reader.network.createDefaultHttpClient
import dev.reader.source.runtime.VeneraEnvironment
import okio.FileSystem
import okio.Path

/**
 * Opens the extension session in [appDataDir] (desktop JVM).
 *
 * Storage layout: `<appDataDir>/scripts` holds immutable revision scripts,
 * database `<appDataDir>/extensions.db` (SQLite file, created on first open). The
 * session owns the driver, HTTP client and QuickJS runtime; release
 * everything with [ExtensionSession.close] (idempotent).
 */
suspend fun openExtensionSession(appDataDir: Path, environment: VeneraEnvironment): ExtensionSession {
    FileSystem.SYSTEM.createDirectories(appDataDir)
    val driver = JdbcSqliteDriver("jdbc:sqlite:${appDataDir / "extensions.db"}")
    return openExtensionSession(
        driver = driver,
        fileSystem = FileSystem.SYSTEM,
        appDataDir = appDataDir,
        httpClient = createDefaultHttpClient(),
        environment = environment,
    )
}
