rootProject.name = "foodcheck-mobile"

include(":shared")
include(":composeApp")
// Shared Lifex in-app update SDK (sibling monorepo under codes/)
include(":appupdate")
project(":appupdate").projectDir =
    file("../../lifex-app-config/android/appupdate")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
