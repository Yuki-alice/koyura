pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "koyura"

include(":apps:android")
include(":apps:desktop")
include(":shared:app")
include(":shared:design-system")
include(":shared:source-runtime")
include(":shared:network")
include(":shared:domain")
include(":shared:data")
include(":shared:features")
