pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
    }
}

rootProject.name = "horizon-issues"

// `:core` is the GitHub layer in plain Kotlin; `-PcoreOnly` builds and tests it without the Android SDK.
include(":core")
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
}
