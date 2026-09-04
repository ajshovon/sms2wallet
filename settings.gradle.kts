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
    // Deliberately no `plugins { id("org.jetbrains.kotlin.jvm") version ... }` here.
    // :bd-sms-parsers requests kotlin("jvm") without a version so it picks up the
    // version already on this build's classpath. Pinning one here would turn that
    // into a versioned request and fail with "already on the classpath".
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sms2wallet"

// Fail with something actionable instead of Gradle's opaque "no build file" error.
require(file("bd-sms-parsers/build.gradle.kts").exists()) {
    "The bd-sms-parsers submodule is not initialised. Run:\n" +
        "    git submodule update --init --recursive"
}

include(":app")
include(":bd-sms-parsers")
