package app.koyura.data.extension

import app.cash.sqldelight.db.SqlDriver
import okio.Path

/** In-memory driver for integration tests (actuals per platform). */
expect fun createTestDriver(): SqlDriver

/**
 * File-backed driver whose rows survive closing and reopening with the same
 * [name]. JVM stores `<directory>/[name].db`; iOS uses a SQLiter-managed file
 * with the same name. Each test must use a unique [name].
 */
expect fun createFileBackedTestDriver(directory: Path, name: String): SqlDriver
