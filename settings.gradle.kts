pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.azuredoom.com/mods")
        }
        maven {
            url = uri("impulse-core/build/generated-sources-m2")
        }
        // Crucible Maven repository.
        maven {
            url = uri("https://gitlab.com/api/v4/projects/82033924/packages/maven")
        }
    }
}

rootProject.name = "impulse"

include("impulse-api")
include("impulse-native-loader")
include("impulse-bullet")
include("impulse-rapier")
include("impulse-core")
include("impulse-examples")
