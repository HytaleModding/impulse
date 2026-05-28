import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    id("java-library")
}

data class RapierBackendPlatform(
        val taskSuffix: String,
        val archiveClassifier: String,
        val resourceOs: String,
        val resourceArch: String,
        val libraryName: String
) {
    val nativeResourcePath: String = "native/$resourceOs/$resourceArch"
    val nativeResourceEntry: String = "$nativeResourcePath/$libraryName"
}

val rapierVersion = "0.32.0"
val rapierCargoLocked = providers.gradleProperty("rapierCargoLocked")
        .map { it.toBoolean() }
        .orElse(true)
val nativeProfile = providers.gradleProperty("rapierNativeProfile").orElse("release")
val nativeFeatures = providers.gradleProperty("rapierNativeFeatures").orElse("")
val rustDirectory = layout.projectDirectory.dir("src/main/rust")
val rapierBuildPlatform = providers.gradleProperty("rapierBuildPlatform")
        .orElse(detectRapierBuildPlatform())
        .get()
val nativeResourceOs = resourceOsForPlatform(rapierBuildPlatform)
val nativeResourceArch = resourceArchForPlatform(rapierBuildPlatform)
val nativeLibraryName = nativeLibraryNameFor(nativeResourceOs)
val nativeResourcePath = "native/$nativeResourceOs/$nativeResourceArch"
val cargoTarget = cargoTargetForPlatform(rapierBuildPlatform)
val rapierPatchFile = rustDirectory.file("patches/rapier3d-$rapierVersion-simd-body-masks.patch")
val patchedRapierDirectory = rustDirectory.dir("target/impulse-patched/rapier3d-$rapierVersion-impulse")
val patchedInteractionGroupsFile = patchedRapierDirectory.file("src/dynamics/solver/interaction_groups.rs")
val patchedInteractionGroupsMarker = "fn include_active_set_id"
val checkedInCargoConfig = rootProject.layout.projectDirectory.file(".cargo/config.toml")
val nativeBuildDirectory = nativeProfile.map { profile ->
    if (profile == "release") {
        "release"
    } else {
        "debug"
    }
}
val providedRapierNativeResourceRoot = providers.gradleProperty("impulse.rapierNativeResourceRoot")
val rapierNativeResourceRoot = providedRapierNativeResourceRoot
        .map { path -> file(path) }
        .orElse(layout.buildDirectory.dir("generated/rapier-native").map { directory -> directory.asFile })
val rapierBackendPlatforms = listOf(
        RapierBackendPlatform("LinuxX64", "linux-x86_64", "linux", "x86_64", "libimpulse_rapier.so"),
        RapierBackendPlatform("LinuxArm64", "linux-arm64", "linux", "arm64", "libimpulse_rapier.so"),
        RapierBackendPlatform("OsxArm64", "osx-arm64", "osx", "arm64", "libimpulse_rapier.dylib"),
        RapierBackendPlatform("WindowsX64", "windows-x86_64", "windows", "x86_64", "impulse_rapier.dll")
)
val impulseLicenseFile = rootProject.layout.projectDirectory.file("LICENSE")
val rapierRustBackendLicenseFile = rootProject.layout.projectDirectory.file(
        "licenses/RAPIER_RUST_BACKEND_LICENSES")

fun detectNativeResourceOs(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("linux") -> "linux"
        osName.contains("mac") || osName.contains("darwin") -> "osx"
        osName.contains("windows") -> "windows"
        else -> throw GradleException("Unsupported Rapier native packaging OS: "
                + System.getProperty("os.name"))
    }
}

fun detectRapierBuildPlatform(): String {
    val resourceOs = detectNativeResourceOs()
    val resourceArch = detectNativeResourceArch(resourceOs)
    return "$resourceOs-$resourceArch"
}

fun resourceOsForPlatform(platform: String): String {
    return when (platform) {
        "linux-x86_64", "linux-arm64" -> "linux"
        "osx-arm64" -> "osx"
        "windows-x86_64" -> "windows"
        else -> throw GradleException("Unsupported Rapier build platform: $platform")
    }
}

fun resourceArchForPlatform(platform: String): String {
    return when (platform) {
        "linux-x86_64", "windows-x86_64" -> "x86_64"
        "linux-arm64", "osx-arm64" -> "arm64"
        else -> throw GradleException("Unsupported Rapier build platform: $platform")
    }
}

fun cargoTargetForPlatform(platform: String): String {
    return when (platform) {
        "linux-x86_64" -> "x86_64-unknown-linux-gnu"
        "linux-arm64" -> "aarch64-unknown-linux-gnu"
        "osx-arm64" -> "aarch64-apple-darwin"
        "windows-x86_64" -> "x86_64-pc-windows-msvc"
        else -> throw GradleException("Unsupported Rapier build platform: $platform")
    }
}

fun detectNativeResourceArch(resourceOs: String): String {
    val osArch = System.getProperty("os.arch").lowercase()
    val resourceArch = when (osArch) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        "arm", "arm32" -> "arm32"
        else -> throw GradleException("Unsupported Rapier native packaging architecture: "
                + System.getProperty("os.arch"))
    }
    if (resourceOs == "windows" && resourceArch != "x86_64") {
        throw GradleException("Unsupported Rapier native packaging platform: "
                + System.getProperty("os.name") + " " + System.getProperty("os.arch"))
    }
    return resourceArch
}

fun nativeLibraryNameFor(resourceOs: String): String {
    return when (resourceOs) {
        "windows" -> "impulse_rapier.dll"
        "osx" -> "libimpulse_rapier.dylib"
        else -> "libimpulse_rapier.so"
    }
}

val cargoAvailable = try {
    ProcessBuilder("cargo", "--version")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor() == 0
} catch (_: Exception) {
    false
}

val buildNative = providers.gradleProperty("buildRapierNative")
        .map { it.toBoolean() }
        .orElse(cargoAvailable)

val preparePatchedRapier by tasks.registering(Exec::class) {
    description = "Prepares a patched version of Rapier for building the native library."
    onlyIf { buildNative.get() }
    workingDir = rustDirectory.asFile

    inputs.files(
            layout.projectDirectory.file("src/main/rust/Cargo.toml"),
            layout.projectDirectory.file("src/main/rust/Cargo.lock"),
            rapierPatchFile)
    outputs.dir(patchedRapierDirectory)
    outputs.upToDateWhen {
        val interactionGroups = patchedInteractionGroupsFile.asFile
        interactionGroups.isFile
                && interactionGroups.readText().contains(patchedInteractionGroupsMarker)
    }

    environment("RAPIER_VERSION", rapierVersion)
    environment("PATCHED_RAPIER_PATH", patchedRapierDirectory.asFile.absolutePath)
    environment("RAPIER_PATCH_PATH", rapierPatchFile.asFile.absolutePath)

    if (nativeResourceOs == "windows") {
        val script = layout.projectDirectory.file("src/main/rust/scripts/prepare-rapier-patched.ps1")
        inputs.file(script)
        commandLine("pwsh",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                script.asFile.absolutePath)
        isIgnoreExitValue = false
        standardOutput = System.out
        errorOutput = System.err
    } else {
        val script = layout.projectDirectory.file("src/main/rust/scripts/prepare-rapier-patched.sh")
        inputs.file(script)
        commandLine("bash", script.asFile.absolutePath)
    }

    doLast {
        val interactionGroups = patchedInteractionGroupsFile.asFile
        if (!interactionGroups.isFile
                || !interactionGroups.readText().contains(patchedInteractionGroupsMarker)
        ) {
            throw GradleException("Patched Rapier source is missing the SIMD body-mask guard.")
        }
    }
}

val cargoBuildRapierNative by tasks.registering(Exec::class) {
    dependsOn(preparePatchedRapier)
    onlyIf { buildNative.get() }
    workingDir = rustDirectory.asFile

    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.toml"))
    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.lock"))
    inputs.file(checkedInCargoConfig)
    inputs.dir(layout.projectDirectory.dir("src/main/rust/src"))
    inputs.file(rapierPatchFile)
    inputs.dir(patchedRapierDirectory)
    inputs.property("rapierNativeFeatures", nativeFeatures)
    outputs.file(nativeBuildDirectory.map {
        layout.projectDirectory.file("src/main/rust/target/$cargoTarget/$it/$nativeLibraryName")
    })

    doFirst {
        val command = mutableListOf("cargo",
                "build")
        if (rapierCargoLocked.get()) {
            command.add("--locked")
        }
        command.add("--target")
        command.add(cargoTarget)
        if (nativeProfile.get() == "release") {
            command.add("--release")
        }
        val features = nativeFeatures.get().trim()
        if (features.isNotEmpty()) {
            command.add("--features")
            command.add(features)
        }
        commandLine(command)
    }
}

val stageRapierNativeResource by tasks.registering(Copy::class) {
    dependsOn(cargoBuildRapierNative)
    onlyIf { buildNative.get() }

    from(nativeBuildDirectory.map {
        layout.projectDirectory.file("src/main/rust/target/$cargoTarget/$it/$nativeLibraryName")
    })
    into(layout.buildDirectory.dir("generated/rapier-native/$nativeResourcePath"))
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated/rapier-native"))
    }
}

tasks.processResources {
    dependsOn(stageRapierNativeResource)
}

dependencies {
    api(project(":impulse-api"))

    implementation(project(":impulse-native-loader"))

    testImplementation(testFixtures(project(":impulse-api")))
}

fun runtimeClasspathWithoutBundledApi(): FileCollection {
    return configurations.runtimeClasspath.get()
            .filter { file -> !file.name.startsWith("impulse-api-") }
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
    from(rapierRustBackendLicenseFile) {
        into("META-INF/licenses/rapier-native")
        rename { "LICENSES" }
    }
}

fun Jar.includeRapierNativeResource(platform: RapierBackendPlatform) {
    val archiveClassifier = platform.archiveClassifier
    val nativeResourcePath = platform.nativeResourcePath
    val nativeResourceEntry = platform.nativeResourceEntry

    from(rapierNativeResourceRoot.map { root ->
        root.resolve(nativeResourcePath)
    }) {
        into(nativeResourcePath)
    }
    doFirst {
        val nativeResource = rapierNativeResourceRoot.get().resolve(nativeResourceEntry)
        if (!nativeResource.isFile) {
            throw GradleException("Missing Rapier native resource for "
                    + archiveClassifier + ": " + nativeResource.absolutePath
                    + ". Build it on a matching runner or provide -Pimpulse.rapierNativeResourceRoot.")
        }
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    expandRuntimeClasspath(runtimeClasspathWithoutBundledApi())
    includeBackendLicenseNotices()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

val platformJarTasks = rapierBackendPlatforms.map { platform ->
    tasks.register<Jar>("packageRapierBackend${platform.taskSuffix}") {
        group = "build"
        description = "Packages the Rapier backend provider jar for ${platform.archiveClassifier}"
        archiveClassifier.set(platform.archiveClassifier)

        if (!providedRapierNativeResourceRoot.isPresent
                && platform.resourceOs == nativeResourceOs
                && platform.resourceArch == nativeResourceArch) {
            dependsOn(stageRapierNativeResource)
        }

        from(sourceSets.main.get().output) {
            exclude("native/**")
        }
        includeBackendRuntime()
        includeBackendLicenseNotices()
        includeRapierNativeResource(platform)
    }
}

tasks.register<Jar>("packageRapierBackendUniversal") {
    group = "build"
    description = "Packages the Rapier backend provider jar with every configured native library"
    archiveClassifier.set("universal")

    from(sourceSets.main.get().output) {
        exclude("native/**")
    }
    includeBackendRuntime()
    includeBackendLicenseNotices()
    rapierBackendPlatforms.forEach { platform ->
        includeRapierNativeResource(platform)
    }
}

tasks.register("packageRapierBackendPlatformJars") {
    group = "build"
    description = "Packages all Rapier per-platform backend jars plus the universal jar"
    dependsOn(platformJarTasks)
    dependsOn(tasks.named("packageRapierBackendUniversal"))
}
