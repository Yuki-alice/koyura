package dev.reader.data.extension

import app.cash.sqldelight.db.SqlDriver

/** In-memory driver for integration tests (actuals per platform). */
expect fun createTestDriver(): SqlDriver
