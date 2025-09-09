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

        // Mapbox Maven
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                username = "mapbox"
                password =
                    (settings.providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull) ?: ""
            }
            authentication {
                create<org.gradle.authentication.http.BasicAuthentication>("basic")
            }
        }
    }
}

rootProject.name = "Pet Pal Finder"
include(":app")
 