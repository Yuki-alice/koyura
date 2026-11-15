package dev.reader.data.extension

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlin.uuid.Uuid

actual fun createTestDriver(): SqlDriver {
    // NOTE: ":memory:" databases appear to be shared process-wide by the
    // native driver (rows leaked across tests), and the driver takes a bare
    // name (no path separators). Use a unique name per driver for isolation.
    return NativeSqliteDriver(ExtensionDb.Schema, "test-${Uuid.random()}.db")
}
