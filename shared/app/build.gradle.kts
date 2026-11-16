import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "dev.reader.shared.app"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 26
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        androidResources.enable = true
    }

    jvm()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ReaderShared"
            isStatic = true
            binaryOption("bundleId", "dev.reader.shared.app")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:design-system"))
            implementation(project(":shared:data"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.mock)
            implementation(libs.okio)
        }
    }
}

val veneraReferenceDir = providers.gradleProperty("veneraReferenceDir")

tasks.withType<Test>().configureEach {
    // Screenshot tests write PNGs into a caller-chosen directory.
    systemProperty("reader.screenshotDir", providers.gradleProperty("readerScreenshotDir").getOrElse("build/screenshots"))
    // Upstream Venera fixture for the real-service flow tests.
    val fixture = veneraReferenceDir
        .map { dir -> rootProject.file(dir).resolve("doc/examples/minimal_source.js") }
        .orElse(rootProject.layout.projectDirectory.file("reference/Venera-Next/doc/examples/minimal_source.js").asFile)
    inputs.files(fixture)
    systemProperty("reader.venera.example", fixture.get().absolutePath)
    systemProperty("reader.venera.required", veneraReferenceDir.isPresent.toString())
}

compose.resources {
    packageOfResClass = "dev.reader.app.resources"
}
