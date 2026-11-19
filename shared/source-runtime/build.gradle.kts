import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "app.koyura.source.runtime"
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
            api(libs.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.quickjs)
            implementation(libs.ksoup)
            implementation(libs.okio)
            api(project(":shared:network"))
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

val fixtureName = "doc/examples/minimal_source.js"
val veneraReferenceDir = providers.gradleProperty("veneraReferenceDir")
val veneraFixture = veneraReferenceDir
    .map { sourceDir -> rootProject.file(sourceDir).resolve(fixtureName) }
    .orElse(rootProject.layout.projectDirectory.file("reference/Venera-Next/$fixtureName").asFile)

tasks.named<Test>("jvmTest") {
    val fixture = veneraFixture.get()
    inputs.files(fixture)
    systemProperty("reader.venera.example", fixture.absolutePath)
    systemProperty("reader.venera.required", veneraReferenceDir.isPresent.toString())
    testLogging.showStandardStreams = true
}

val communityFixtureDir = providers.gradleProperty("veneraCommunityFixtureDir")
tasks.named<Test>("jvmTest") {
    communityFixtureDir.orNull?.let { path ->
        val directory = rootProject.file(path)
        inputs.files(directory.resolve("baozi.js"), directory.resolve("mxs.js"))
        systemProperty("reader.venera.community", directory.absolutePath)
    }
}
