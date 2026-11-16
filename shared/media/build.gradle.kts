import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    kotlin("multiplatform") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("com.android.kotlin.multiplatform.library") version "9.2.1"
}

val fixtureRoot = layout.projectDirectory.dir("fixtures").asFile.absolutePath

kotlin {
    jvmToolchain(21)

    android {
        namespace = "app.koyura.media"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 26
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("com.squareup.okio:okio:3.18.2")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            implementation("com.fleeksoft.ksoup:ksoup:0.2.6")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.named<Test>("jvmTest") {
    val fixtures = file(fixtureRoot)
    inputs.dir(fixtures)
    systemProperty("reader.media.fixtures", fixtures.absolutePath)
}

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    val fixtures = file(fixtureRoot)
    inputs.dir(fixtures)
    environment("SIMCTL_CHILD_READER_MEDIA_FIXTURES", fixtures.absolutePath)
}
