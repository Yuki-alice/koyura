package app.koyura.data.session

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb
import kotlin.test.assertEquals

internal fun assertExtensionDatabasePolicy(driver: SqlDriver) {
    fun assertPragmas() {
        val mode = driver.executeQuery(null, "PRAGMA journal_mode", { cursor ->
            check(cursor.next().value)
            QueryResult.Value(cursor.getString(0))
        }, 0).value
        val timeout = driver.executeQuery(null, "PRAGMA busy_timeout", { cursor ->
            check(cursor.next().value)
            QueryResult.Value(cursor.getLong(0))
        }, 0).value
        assertEquals("wal", mode)
        assertEquals(ExtensionDatabaseBusyTimeoutMillis.toLong(), timeout)
        println("SQLite journal_mode=$mode busy_timeout=$timeout")
    }
    assertPragmas()
    ExtensionDb(driver).transaction { assertPragmas() }
}
