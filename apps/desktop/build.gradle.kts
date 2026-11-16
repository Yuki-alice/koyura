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
        mainClass = "app.koyura.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Koyura"
            packageVersion = libs.versions.app.get()

            macOS {
                bundleID = "app.koyura"
            }

            linux {
                packageName = "koyura"
            }
        }
    }
}
