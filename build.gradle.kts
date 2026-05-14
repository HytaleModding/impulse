plugins {
    alias(libs.plugins.hytale.workspace)
}

group = property("group") as String
version = property("version") as String

hytaleWorkspace {
    modProjects = listOf(":impulse-examples", ":impulse-core")
    hostProject = ":impulse-examples"

    manifestGroup = property("manifest_group") as String
    hytaleVersion = property("hytale_version") as String
    patchline = property("patchline") as String
}

subprojects {
    plugins.withId("java") {
        the<JavaPluginExtension>().toolchain {
            languageVersion.set(JavaLanguageVersion.of((property("java_version") as String).toInt()))
        }
    }
}

// fix to make runAllMods accept input
gradle.projectsEvaluated {
    tasks.named("runAllMods").configure {
        val runTask = this as JavaExec
        runTask.standardInput = System.`in`

        // FIXME: workaround until we have runtime based, backend swap
        providers.gradleProperty("impulse.backend")
            .orElse(providers.systemProperty("impulse.backend"))
            .orNull
            ?.let { runTask.systemProperty("impulse.backend", it) }
    }
}
