import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "app.koyura.network"
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
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosArm64Main.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        iosSimulatorArm64Main.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
