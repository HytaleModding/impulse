import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.hytale.workspace)
}

group = property("group") as String
version = property("version") as String

val coreOnlyWorkspace = providers.gradleProperty("impulse.coreOnlyWorkspace")
    .map(String::toBoolean)
    .orElse(false)

hytaleWorkspace {
    modProjects = if (coreOnlyWorkspace.get()) {
        listOf(":impulse-core")
    } else {
        listOf(":impulse-examples", ":impulse-core")
    }
    hostProject = if (coreOnlyWorkspace.get()) {
        ":impulse-core"
    } else {
        ":impulse-examples"
    }

    manifestGroup = property("manifest_group") as String
    hytaleVersion = property("hytale_version") as String
    patchline = property("patchline") as String
}

subprojects {
    plugins.withId("java") {
        the<JavaPluginExtension>().toolchain {
            languageVersion.set(JavaLanguageVersion.of((property("java_version") as String).toInt()))
        }

        dependencies {
            add("testImplementation", platform(libs.junit.bom))
            add("testImplementation", libs.junit.jupiter)
            add("testRuntimeOnly", libs.junit.platform.launcher)
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            jvmArgs("--enable-native-access=ALL-UNNAMED")
            testLogging {
                events = setOf(
                    TestLogEvent.PASSED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.FAILED
                )
                exceptionFormat = TestExceptionFormat.FULL
                showCauses = true
                showExceptions = true
                showStackTraces = true
            }
        }
    }
}

val examplesSourceDir = layout.projectDirectory.dir("impulse-examples/src/main/java").asFile

tasks.register("checkExampleImportBoundaries") {
    group = "verification"
    description = "Fails if impulse-examples imports unsupported impulse-core internals"
    inputs.dir(examplesSourceDir)

    doLast {
        val offenders = examplesSourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (line.contains("dev.hytalemodding.impulse.core.internal")) {
                        "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            throw GradleException("impulse-examples must not import impulse-core internal packages:\n"
                + offenders.joinToString("\n"))
        }
    }
}

tasks.register("headlessTest") {
    group = "verification"
    description = "Runs automated headless/serverless tests without booting the Hytale server"
    dependsOn(
        "checkExampleImportBoundaries",
        ":impulse-api:test",
        ":impulse-bullet:test",
        ":impulse-rapier:test",
        ":impulse-core:test"
    )
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

        providers.gradleProperty("impulse.runAllModsJvmArgs")
            .orElse(providers.systemProperty("impulse.runAllModsJvmArgs"))
            .map { args -> args.split(Regex("\\s+")).filter { it.isNotBlank() } }
            .orNull
            ?.let { runTask.jvmArgs(it) }
    }
}
