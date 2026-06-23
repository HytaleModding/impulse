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
    }
}

rootProject.name = "impulse"

include("impulse-backend-api")
include("impulse-native-loader")
include("impulse-jolt")
include("impulse-rapier")
include("impulse-core")
include("impulse-examples")
include("impulse-early-plugin")
