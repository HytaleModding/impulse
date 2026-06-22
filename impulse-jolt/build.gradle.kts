import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.PathSensitivity
import org.gradle.jvm.tasks.Jar

plugins {
    id("java-library")
}

data class JoltBackendPlatform(
    val taskSuffix: String,
    val archiveClassifier: String,
    val resourceOs: String,
    val resourceArch: String,
    val libraryName: String
) {
    val nativeResourcePath: String = "native/$resourceOs/$resourceArch"
    val nativeResourceEntry: String = "$nativeResourcePath/$libraryName"
}

val joltBackendPlatforms = listOf(
    JoltBackendPlatform("LinuxX64", "linux-x86_64", "linux", "x86_64", "libimpulse_jolt.so"),
    JoltBackendPlatform("LinuxArm64", "linux-arm64", "linux", "arm64", "libimpulse_jolt.so"),
    JoltBackendPlatform("OsxArm64", "osx-arm64", "osx", "arm64", "libimpulse_jolt.dylib"),
    JoltBackendPlatform("WindowsX64", "windows-x86_64", "windows", "x86_64", "impulse_jolt.dll")
)
val impulseLicenseFile = rootProject.layout.projectDirectory.file("LICENSE")
val joltPhysicsLicenseFile = rootProject.layout.projectDirectory.file("licenses/JOLT_PHYSICS_LICENSE")
val nativeResourceOs = detectNativeResourceOs()
val nativeResourceArch = detectNativeResourceArch(nativeResourceOs)
val nativeResourcePath = "native/$nativeResourceOs/$nativeResourceArch"
val nativeLibraryName = nativeLibraryNameFor(nativeResourceOs)
val nativeSourceDirectory = layout.projectDirectory.dir("src/main/cpp")
val nativeCmakeFile = nativeSourceDirectory.file("CMakeLists.txt")
val nativeSourceFiles = nativeSourceDirectory.asFileTree.matching {
    include("**/*.cpp", "**/*.h")
}
val cmakeBuildDirectory = layout.buildDirectory.dir("cmake/jolt")
val nativeOutputDirectory = layout.buildDirectory.dir("native/jolt")
val nativeOutputFile = nativeOutputDirectory.map { directory -> directory.file(nativeLibraryName) }
val generatedJoltNativeResourceRoot = layout.buildDirectory.dir("generated/jolt-native")
val providedJoltNativeResourceRoot = providers.gradleProperty("impulse.joltNativeResourceRoot")
val joltNativeResourceRoot = providedJoltNativeResourceRoot
    .map { path -> file(path) }
    .orElse(generatedJoltNativeResourceRoot.map { directory -> directory.asFile })
val cmakeExecutable = providers.gradleProperty("joltCmake").orElse("cmake")
val cxxCompiler = providers.gradleProperty("joltCxx")
val joltPhysicsGitTag = providers.gradleProperty("joltPhysicsGitTag").orElse("v5.5.0")
val ninjaAvailable = commandAvailable("ninja")
val cmakeGenerator = providers.gradleProperty("joltCmakeGenerator")
    .orElse(if (ninjaAvailable) "Ninja" else "")
val cmakeAvailable = commandAvailable(cmakeExecutable.get())
val gitAvailable = commandAvailable("git")
val buildNative = providers.gradleProperty("buildJoltNative")
    .map { it.toBoolean() }
    .orElse(cmakeAvailable && gitAvailable)

fun commandAvailable(command: String): Boolean {
    return try {
        ProcessBuilder(command, "--version")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor() == 0
    } catch (_: Exception) {
        false
    }
}

fun detectNativeResourceOs(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("linux") -> "linux"
        osName.contains("mac") || osName.contains("darwin") -> "osx"
        osName.contains("windows") -> "windows"
        else -> throw GradleException("Unsupported Jolt native packaging OS: "
            + System.getProperty("os.name"))
    }
}

fun detectNativeResourceArch(resourceOs: String): String {
    val osArch = System.getProperty("os.arch").lowercase()
    val resourceArch = when (osArch) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        else -> throw GradleException("Unsupported Jolt native packaging architecture: "
            + System.getProperty("os.arch"))
    }
    if (resourceOs == "windows" && resourceArch != "x86_64") {
        throw GradleException("Unsupported Jolt native packaging platform: "
            + System.getProperty("os.name") + " " + System.getProperty("os.arch"))
    }
    return resourceArch
}

fun nativeLibraryNameFor(resourceOs: String): String {
    return when (resourceOs) {
        "windows" -> "impulse_jolt.dll"
        "osx" -> "libimpulse_jolt.dylib"
        else -> "libimpulse_jolt.so"
    }
}

fun runtimeClasspathWithoutBundledApi(): FileCollection {
    return configurations.runtimeClasspath.get()
        .filter { file -> !file.name.startsWith("impulse-backend-api-") }
}

fun Jar.expandRuntimeClasspath(runtimeClasspath: FileCollection) {
    dependsOn(runtimeClasspath.buildDependencies)
    from({
        runtimeClasspath.map { file -> if (file.isDirectory) file else zipTree(file) }
    })
}

fun Jar.includeBackendRuntime() {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    expandRuntimeClasspath(runtimeClasspathWithoutBundledApi())
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

fun Jar.includeBackendLicenseNotices() {
    from(impulseLicenseFile) {
        into("META-INF/licenses/impulse")
        rename { "LICENSE" }
    }
    from(joltPhysicsLicenseFile) {
        into("META-INF/licenses/jolt-physics")
        rename { "LICENSE" }
    }
}

fun Jar.includeJoltNativeResource(platform: JoltBackendPlatform) {
    val archiveClassifier = platform.archiveClassifier
    val nativeResourcePath = platform.nativeResourcePath
    val nativeResourceEntry = platform.nativeResourceEntry

    from(joltNativeResourceRoot.map { root ->
        root.resolve(nativeResourcePath)
    }) {
        into(nativeResourcePath)
    }
    doFirst {
        val nativeResource = joltNativeResourceRoot.get().resolve(nativeResourceEntry)
        if (!nativeResource.isFile) {
            throw GradleException("Missing Jolt native resource for "
                + archiveClassifier + ": " + nativeResource.absolutePath
                + ". Build it on a matching runner or provide -Pimpulse.joltNativeResourceRoot.")
        }
    }
}

val configureJoltNative by tasks.registering(Exec::class) {
    onlyIf { buildNative.get() }

    inputs.file(nativeCmakeFile)
    inputs.files(nativeSourceFiles)
        .withPropertyName("nativeSourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("joltPhysicsGitTag", joltPhysicsGitTag)
    inputs.property("joltCmake", cmakeExecutable)
    inputs.property("joltCmakeGenerator", cmakeGenerator)
    inputs.property("joltCxx", cxxCompiler.orNull ?: "")
    outputs.file(cmakeBuildDirectory.map { directory -> directory.file("CMakeCache.txt") })

    doFirst {
        cmakeBuildDirectory.get().asFile.mkdirs()
        nativeOutputDirectory.get().asFile.mkdirs()
        val command = mutableListOf(
            cmakeExecutable.get(),
            "-S",
            nativeSourceDirectory.asFile.absolutePath,
            "-B",
            cmakeBuildDirectory.get().asFile.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${nativeOutputDirectory.get().asFile.absolutePath}",
            "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${nativeOutputDirectory.get().asFile.absolutePath}",
            "-DJOLT_PHYSICS_GIT_TAG=${joltPhysicsGitTag.get()}"
        )
        val generator = cmakeGenerator.get().trim()
        if (generator.isNotEmpty()) {
            command.addAll(listOf("-G", generator))
        }
        cxxCompiler.orNull?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { command.add("-DCMAKE_CXX_COMPILER=$it") }
        commandLine(command)
    }
}

val compileJoltNative by tasks.registering(Exec::class) {
    dependsOn(configureJoltNative)
    onlyIf { buildNative.get() }

    inputs.file(nativeCmakeFile)
    inputs.files(nativeSourceFiles)
        .withPropertyName("nativeSourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("joltPhysicsGitTag", joltPhysicsGitTag)
    inputs.property("joltCmake", cmakeExecutable)
    inputs.property("joltCmakeGenerator", cmakeGenerator)
    inputs.property("joltCxx", cxxCompiler.orNull ?: "")
    outputs.file(nativeOutputFile)

    doFirst {
        val command = mutableListOf(
            cmakeExecutable.get(),
            "--build",
            cmakeBuildDirectory.get().asFile.absolutePath,
            "--config",
            "Release",
            "--target",
            "impulse_jolt"
        )
        commandLine(command)
    }
}

val stageJoltNativeResource by tasks.registering(Copy::class) {
    dependsOn(compileJoltNative)
    onlyIf { buildNative.get() }

    from(nativeOutputFile)
    into(generatedJoltNativeResourceRoot.map { it.dir(nativeResourcePath) })
}

sourceSets {
    main {
        resources.srcDir(joltNativeResourceRoot)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    expandRuntimeClasspath(runtimeClasspathWithoutBundledApi())
    includeBackendLicenseNotices()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.processResources {
    if (!providedJoltNativeResourceRoot.isPresent) {
        dependsOn(stageJoltNativeResource)
    }
}

val platformJarTasks = joltBackendPlatforms.map { platform ->
    tasks.register<Jar>("packageJoltBackend${platform.taskSuffix}") {
        group = "build"
        description = "Packages the Jolt backend provider jar for ${platform.archiveClassifier}"
        archiveClassifier.set(platform.archiveClassifier)

        if (!providedJoltNativeResourceRoot.isPresent
                && platform.resourceOs == nativeResourceOs
                && platform.resourceArch == nativeResourceArch) {
            dependsOn(stageJoltNativeResource)
        }

        from(sourceSets.main.get().output) {
            exclude("native/**")
        }
        includeBackendRuntime()
        includeBackendLicenseNotices()
        includeJoltNativeResource(platform)
    }
}

tasks.register<Jar>("packageJoltBackendUniversal") {
    group = "build"
    description = "Packages the Jolt backend provider jar with every configured native library"
    archiveClassifier.set("universal")

    from(sourceSets.main.get().output) {
        exclude("native/**")
    }
    includeBackendRuntime()
    includeBackendLicenseNotices()
    joltBackendPlatforms.forEach { platform ->
        includeJoltNativeResource(platform)
    }
}

tasks.register("packageJoltBackendPlatformJars") {
    group = "build"
    description = "Packages all Jolt per-platform backend jars plus the universal jar"
    dependsOn(platformJarTasks)
    dependsOn(tasks.named("packageJoltBackendUniversal"))
}

dependencies {
    api(project(":impulse-backend-api"))

    implementation(project(":impulse-native-loader"))
}
