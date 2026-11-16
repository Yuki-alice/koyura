package app.koyura.data.extension

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import okio.FileSystem
import okio.Path

actual fun createTestDriver(): SqlDriver {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    ExtensionDb.Schema.create(driver)
    return driver
}

actual fun createFileBackedTestDriver(directory: Path, name: String): SqlDriver {
    FileSystem.SYSTEM.createDirectories(directory)
    // No schema creation here: openExtensionSession ensures it idempotently,
    // which is exactly what the reopen path exercises.
    return JdbcSqliteDriver("jdbc:sqlite:${directory / "$name.db"}")
}
