package app.koyura.app

import androidx.compose.runtime.staticCompositionLocalOf
import app.koyura.domain.reader.ReaderService

/** Provides the injected runtime to reader UI; unset until a service is wired. */
val LocalReaderService = staticCompositionLocalOf<ReaderService?> { null }
