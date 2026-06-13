import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.process.CommandLineArgumentProvider

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

val backendProjectPaths = setOf(":impulse-bullet", ":impulse-rapier")
val stagedBackendJarDirectory = layout.projectDirectory.dir("run/mods/impulse-backends")
val stagedEarlyPluginJarDirectory = layout.projectDirectory.dir("run/earlyplugins")
val physicsStoreEarlyPluginEnabled = providers.gradleProperty("impulse.physicsStoreEarlyPlugin")
    .map(String::toBoolean)
    .orElse(false)
val hytaleToolProjectPaths = listOf(
    ":impulse-core",
    ":impulse-examples")

gradle.projectsEvaluated {
    val hytaleAssetDownloads = hytaleToolProjectPaths
        .map { path -> project(path).tasks.named("downloadAssetsZip") }

    hytaleToolProjectPaths.forEach { path ->
        project(path).tasks.withType<JavaCompile>().configureEach {
            hytaleAssetDownloads.forEach { dependsOn(it) }
        }
    }
}

gradle.taskGraph.whenReady {
    if (hasTask(":headlessTest")) {
        hytaleToolProjectPaths.forEach { path ->
            project(path).tasks.named("downloadAssetsZip").configure {
                enabled = false
            }
        }
    }
}

val cleanStagedBackendJars by tasks.registering(Delete::class) {
    delete(stagedBackendJarDirectory)
}

val cleanStagedPhysicsStoreEarlyPluginJar by tasks.registering(Delete::class) {
    delete(fileTree(stagedEarlyPluginJarDirectory) {
        include("impulse-early-plugin-*.jar")
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
    into(stagedBackendJarDirectory)
}

val stagePhysicsStoreEarlyPluginJar by tasks.registering(Copy::class) {
    group = "hytale"
    description = "Stages the PhysicsStore early plugin for runAllMods"

    onlyIf("PhysicsStore early plugin opt-in is enabled") {
        physicsStoreEarlyPluginEnabled.get()
    }
    dependsOn(cleanStagedPhysicsStoreEarlyPluginJar)
    val earlyJar = project(":impulse-early-plugin").tasks.named("jar")
    dependsOn(earlyJar)
    from(earlyJar)
    into(stagedEarlyPluginJarDirectory)
}

tasks.register("packageBackendPlatformJars") {
    group = "build"
    description = "Packages all per-platform and universal backend provider jars"
    dependsOn(
        ":impulse-bullet:packageBulletBackendPlatformJars",
        ":impulse-rapier:packageRapierBackendPlatformJars"
    )
}

tasks.register("headlessTest") {
    group = "verification"
    description = "Runs automated headless/serverless tests without booting the Hytale server"
    dependsOn(
        ":impulse-api:test",
        ":impulse-native-loader:test",
        ":impulse-bullet:test",
        ":impulse-rapier:test",
        ":impulse-core:test",
        ":impulse-early-plugin:test"
    )
}

// fix to make runAllMods accept input
gradle.projectsEvaluated {
    tasks.named("runAllMods").configure {
        dependsOn(stageBackendJarsForRunAllMods)
        if (physicsStoreEarlyPluginEnabled.get()) {
            dependsOn(stagePhysicsStoreEarlyPluginJar)
        } else {
            dependsOn(cleanStagedPhysicsStoreEarlyPluginJar)
        }

        val runTask = this as JavaExec
        runTask.standardInput = System.`in`

        // hytale-gradle 1.0.37 can omit project resources from run task classpaths.
        val toolRuntimeClasspaths = hytaleToolProjectPaths.map { path ->
            val sourceSets = project(path).extensions.getByType<SourceSetContainer>()
            sourceSets.named("main").get().runtimeClasspath
        }.toMutableList()
        if (physicsStoreEarlyPluginEnabled.get()) {
            val sourceSets = project(":impulse-early-plugin")
                .extensions.getByType<SourceSetContainer>()
            toolRuntimeClasspaths.add(sourceSets.named("main").get().runtimeClasspath)
        }
        runTask.classpath = files(
            toolRuntimeClasspaths,
            project(if (coreOnlyWorkspace.get()) ":impulse-core" else ":impulse-examples")
                .configurations.named("vineServerJar")
        )

        providers.gradleProperty("impulse.runAllModsJvmArgs")
            .orElse(providers.systemProperty("impulse.runAllModsJvmArgs"))
            .map { args -> args.split(Regex("\\s+")).filter { it.isNotBlank() } }
            .orNull
            ?.let { runTask.jvmArgs(it) }

        if (physicsStoreEarlyPluginEnabled.get()) {
            runTask.argumentProviders.add(CommandLineArgumentProvider {
                listOf("--accept-early-plugins")
            })
        }
    }
}
