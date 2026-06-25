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

include(":impulse-backends:api")
include(":impulse-backends:native-loader")
include(":impulse-backends:jolt")
include(":impulse-backends:rapier")
include(":impulse-builtins:control")
include("impulse-core")
include("impulse-examples")
include("impulse-early-plugin")
