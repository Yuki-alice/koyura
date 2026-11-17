package app.koyura.app

import androidx.compose.runtime.staticCompositionLocalOf
import app.koyura.domain.reader.ReaderService
import app.koyura.domain.reader.preferences.ReaderPreferences

/** Provides the injected runtime to reader UI; unset until a service is wired. */
val LocalReaderService = staticCompositionLocalOf<ReaderService?> { null }

internal val LocalReaderPreferences = staticCompositionLocalOf<ReaderPreferences?> { null }
