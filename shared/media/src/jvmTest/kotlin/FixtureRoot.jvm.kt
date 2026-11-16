package app.koyura.media

/** Reads the fixture root from the Gradle-injected system property. */
actual fun fixtureRoot(): String =
    System.getProperty("reader.media.fixtures")
        ?: error("reader.media.fixtures system property is not set; run jvmTest through Gradle")
