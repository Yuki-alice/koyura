package app.koyura.data.session

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.extension.WebLoginBrowser
import app.koyura.domain.updates.LibraryUpdateNotifier
import app.koyura.network.createDefaultHttpClient
import app.koyura.source.runtime.VeneraEnvironment
import app.koyura.source.runtime.VeneraUiHost
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
suspend fun Context.openExtensionSession(environment: VeneraEnvironment,
    webLoginBrowser: WebLoginBrowser? = null,
    sourceUi: VeneraUiHost = VeneraUiHost.Headless,
    isOnline: () -> Boolean = { true },
    isUnmetered: (() -> Boolean)? = null,
    updateNotifier: LibraryUpdateNotifier? = null,
): ExtensionSession {
    val appDataDir = File(filesDir, "venera-ext").toOkioPath()
    val driver = createExtensionDriver(this)
    return openExtensionSession(
        driver = driver,
        fileSystem = FileSystem.SYSTEM,
        appDataDir = appDataDir,
        httpClient = createDefaultHttpClient(),
        environment = environment,
        webLoginBrowser = webLoginBrowser,
        sourceUi = sourceUi,
        isOnline = isOnline,
        isUnmetered = isUnmetered,
        updateNotifier = updateNotifier,
        secrets = AndroidSourceSecretStore(this),
    )
}

internal fun createExtensionDriver(context: Context, name: String = ExtensionDatabaseName): AndroidSqliteDriver {
    val callback = object : AndroidSqliteDriver.Callback(ExtensionDb.Schema) {
        override fun onConfigure(db: SupportSQLiteDatabase) {
            check(db.enableWriteAheadLogging()) { "Cannot enable write-ahead logging for $name" }
            val busyTimeout = "PRAGMA busy_timeout = $ExtensionDatabaseBusyTimeoutMillis"
            if (db.isExecPerConnectionSQLSupported) {
                db.execPerConnectionSQL(busyTimeout, emptyArray())
            } else {
                // On Android versions without a connection initializer, set the writer timeout at open.
                db.query(busyTimeout).use { it.moveToFirst() }
            }
        }
    }
    return AndroidSqliteDriver(ExtensionDb.Schema, context, name, callback = callback)
}
