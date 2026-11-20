import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "app.koyura.data"
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
            api(project(":shared:domain"))
            api(project(":shared:source-runtime"))
            api(project(":shared:network"))
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.okio)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosArm64Main.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        iosSimulatorArm64Main.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("ExtensionDb") {
            packageName.set("app.koyura.data.extension")
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

tasks.named<Test>("jvmTest") {
    providers.gradleProperty("veneraCommunityFixtureDir").orNull?.let { path ->
        val directory = rootProject.file(path)
        inputs.files(listOf("manhuagui.js", "komga.js", "comic_walker.js", "manga_dex.js").map(directory::resolve))
        systemProperty("reader.venera.community.e2e", directory.absolutePath)
    }
    val live = providers.gradleProperty("veneraCommunityLive").orNull == "true"
    systemProperty("reader.venera.community.live", live.toString())
    if (live) outputs.upToDateWhen { false }
}
