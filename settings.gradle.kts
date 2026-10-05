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

rootProject.name = "Tripwire"

// :core is plain Kotlin/JVM: the detection brain (entities, tactics, engine, checks, explanations).
// It has no Android dependency so it can be unit-tested and replayed on a laptop.
include(":core")
// :app is the Android app: signal collectors, encrypted ledger, on-device LLM, warnings, UI.
include(":app")
include(":demoapp")
