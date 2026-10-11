pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

buildCache {
    local {
        // CI keeps this directory between runs with actions/cache; see perf-device.yml.
        System.getenv("GRADLE_BUILD_CACHE_DIR")?.let { directory = file(it) }
    }
}

rootProject.name = "hootbook"
include(":app")
include(":benchmark")
include(":core-bindings")
include(":core-platform")
