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
        // FFmpegKit (com.arthenica) is published here.
        maven {
            url = uri("https://api.arthenica.com/maven")
            content { includeGroupByRegex("com\\.arthenica.*") }
        }
    }
}

rootProject.name = "Lumira"
include(":app")
