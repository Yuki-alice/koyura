package app.koyura.data.extension

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import okio.Path
import kotlin.uuid.Uuid

actual fun createTestDriver(): SqlDriver {
    // NOTE: ":memory:" databases appear to be shared process-wide by the
    // native driver (rows leaked across tests). Use a unique name per driver
    // so each test gets an isolated database.
    return NativeSqliteDriver(ExtensionDb.Schema, "test-${Uuid.random()}.db")
}

actual fun createFileBackedTestDriver(directory: Path, name: String): SqlDriver {
    // SQLiter manages the file location; the name alone isolates tests and
    // persists across driver instances in the same run.
    return NativeSqliteDriver(ExtensionDb.Schema, "$name.db")
}
