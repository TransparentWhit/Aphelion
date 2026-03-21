rootProject.name = "Aphelion"

includeBuild("build-logic")
include(":server")
include(":server:ktor-kt")
include(":common")
include(":client")
include(":client:react-ts")

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

include("common:kt")