import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.util.zip.ZipFile

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
val backendProjectPaths = setOf(":impulse-bullet", ":impulse-rapier")
val backendJarIncludes = listOf("impulse-bullet-*.jar", "impulse-rapier-*.jar")
val hytaleToolProjectPaths = listOf(
        ":impulse-core",
        ":impulse-examples",
        ":impulse-bullet",
        ":impulse-rapier")

gradle.projectsEvaluated {
    val hytaleAssetDownloads = hytaleToolProjectPaths
            .map { path -> project(path).tasks.named("downloadAssetsZip") }

    hytaleToolProjectPaths.forEach { path ->
        project(path).tasks.withType<JavaCompile>().configureEach {
            hytaleAssetDownloads.forEach { dependsOn(it) }
        }
    }
}

val cleanStagedBackendJars by tasks.registering(Delete::class) {
    description = "Cleans staged backend provider jars from run/mods for runAllMods"
    delete(fileTree("run/mods") {
        backendJarIncludes.forEach(::include)
    })
}

val stageBackendJarsForRunAllMods by tasks.registering(Copy::class) {
    group = "hytale"
    description = "Stages backend provider jars beside Hytale mods for runAllMods"

    dependsOn(cleanStagedBackendJars)
    backendProjectPaths.forEach { backendPath ->
        val backendJar = project(backendPath).tasks.named("jar")
        dependsOn(backendJar)
        from(backendJar)
    }
    into(layout.projectDirectory.dir("run/mods"))
}

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

tasks.register("checkBackendJarStaging") {
    group = "verification"
    description = "Fails if examples carry backend jars instead of staging them as run/mods jars"
    dependsOn(stageBackendJarsForRunAllMods)

    doLast {
        val exampleRuntimeBackends = listOf("implementation", "runtimeOnly", "runtimeClasspath")
                .flatMap { configurationName ->
                    project(":impulse-examples")
                            .configurations
                            .getByName(configurationName)
                            .allDependencies
                            .filterIsInstance<ProjectDependency>()
                            .map { it.path }
                }
                .filter { it in backendProjectPaths }
                .distinct()

        if (exampleRuntimeBackends.isNotEmpty()) {
            throw GradleException("impulse-examples must not carry backend runtime project "
                    + "dependencies: " + exampleRuntimeBackends.joinToString(", "))
        }

        val stagedBackendNames = fileTree("run/mods") {
            include("impulse-bullet-*.jar")
            include("impulse-rapier-*.jar")
        }.files.associateBy { file ->
            backendProjectPaths.first { backendPath ->
                file.name.startsWith(backendPath.removePrefix(":") + "-")
            }
        }

        val missingBackends = listOf("impulse-bullet", "impulse-rapier")
                .filter { backendName ->
                    stagedBackendNames.keys.none { it.removePrefix(":") == backendName }
                }
        if (missingBackends.isNotEmpty()) {
            throw GradleException("Missing staged backend jars in run/mods: "
                    + missingBackends.joinToString(", "))
        }

        stagedBackendNames.forEach { (backendPath, backendJar) ->
            ZipFile(backendJar).use { zip ->
                if (zip.getEntry("manifest.json") == null) {
                    throw GradleException("${backendJar.name} for $backendPath is missing "
                            + "Hytale manifest.json")
                }
                if (zip.getEntry("META-INF/services/dev.hytalemodding.impulse.api.PhysicsBackend")
                        == null) {
                    throw GradleException("${backendJar.name} for $backendPath is missing "
                            + "PhysicsBackend service metadata")
                }
                if (backendPath == ":impulse-rapier") {
                    val nativeEntries = project(":impulse-rapier")
                            .extensions
                            .extraProperties["impulseRapierPackagedNativeResourceEntries"]
                    val requiredEntries = when (nativeEntries) {
                        is Iterable<*> -> nativeEntries.map { it.toString() }
                        else -> listOf(nativeEntries.toString())
                    }
                    val missingEntries = requiredEntries
                            .filter { entry -> zip.getEntry(entry) == null }
                    if (missingEntries.isNotEmpty()) {
                        throw GradleException("${backendJar.name} for $backendPath is missing "
                                + "packaged Rapier native resources: "
                                + missingEntries.joinToString(", "))
                    }
                }
            }
        }
    }
}

tasks.register("headlessTest") {
    group = "verification"
    description = "Runs automated headless/serverless tests without booting the Hytale server"
    dependsOn(
            "checkExampleImportBoundaries",
            "checkBackendJarStaging",
            ":impulse-api:test",
            ":impulse-bullet:test",
            ":impulse-rapier:test",
            ":impulse-core:test"
    )
}

// fix to make runAllMods accept input
gradle.projectsEvaluated {
    tasks.named("runAllMods").configure {
        dependsOn(stageBackendJarsForRunAllMods)

        val runTask = this as JavaExec
        runTask.standardInput = System.`in`

        providers.gradleProperty("impulse.runAllModsJvmArgs")
                .orElse(providers.systemProperty("impulse.runAllModsJvmArgs"))
                .map { args -> args.split(Regex("\\s+")).filter { it.isNotBlank() } }
                .orNull
                ?.let { runTask.jvmArgs(it) }
    }
}
