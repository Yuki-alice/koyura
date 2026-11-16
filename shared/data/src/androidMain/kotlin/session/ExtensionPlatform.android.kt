package app.koyura.data.session

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.network.createDefaultHttpClient
import app.koyura.source.runtime.VeneraEnvironment
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Opens the extension session using the app's private files directory.
 *
 * Storage layout: `<filesDir>/venera-ext/scripts` holds revision scripts, database
 * `extensions.db` in the default database location. The session owns the
 * driver, HTTP client and QuickJS runtime; release everything with
 * [ExtensionSession.close] (idempotent).
 */
suspend fun Context.openExtensionSession(environment: VeneraEnvironment): ExtensionSession {
    val appDataDir = File(filesDir, "venera-ext").toOkioPath()
    val driver = AndroidSqliteDriver(ExtensionDb.Schema, this, "extensions.db")
    return openExtensionSession(
        driver = driver,
        fileSystem = FileSystem.SYSTEM,
        appDataDir = appDataDir,
        httpClient = createDefaultHttpClient(),
        environment = environment,
    )
}
