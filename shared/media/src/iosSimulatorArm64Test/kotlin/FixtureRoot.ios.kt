package app.koyura.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/**
 * Reads the fixture root from the environment that `simctl spawn` forwards.
 *
 * The build sets `SIMCTL_CHILD_READER_MEDIA_FIXTURES` on the test task; CoreSimulator strips the
 * `SIMCTL_CHILD_` prefix before launching the test binary.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun fixtureRoot(): String =
    getenv("READER_MEDIA_FIXTURES")?.toKString()
        ?: error("READER_MEDIA_FIXTURES is not set; run iosSimulatorArm64Test through Gradle")
