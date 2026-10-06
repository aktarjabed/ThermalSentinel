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

// Named for the product, not for the phase that introduced it: this is the monitoring
// engine and its UI, and the "Ui" suffix predated the engine.
rootProject.name = "ThermalSentinel"
include(":app")
