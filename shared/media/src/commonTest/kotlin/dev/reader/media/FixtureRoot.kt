package dev.reader.media

/**
 * Absolute path of the `fixtures/` directory, supplied by the build.
 *
 * - `jvmTest` reads the `reader.media.fixtures` system property set in `build.gradle.kts`.
 * - `iosSimulatorArm64Test` reads the `READER_MEDIA_FIXTURES` environment variable, which the
 *   build injects as `SIMCTL_CHILD_READER_MEDIA_FIXTURES` so `simctl spawn` forwards it.
 *
 * The value is injected per platform because the tests open real files from the source tree.
 */
expect fun fixtureRoot(): String
