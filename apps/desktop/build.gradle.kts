import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared:app"))
    implementation(libs.compose.ui)
}

compose.desktop {
    application {
        mainClass = "dev.reader.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Reader"
            packageVersion = libs.versions.app.get()

            macOS {
                bundleID = "dev.reader.app"
            }

            linux {
                packageName = "reader"
            }
        }
    }
}
