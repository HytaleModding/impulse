plugins {
    id("java-library")
    id("com.azuredoom.hytale-tools")
}

val rapierVersion = "0.32.0"
val rapierCargoLocked = providers.gradleProperty("rapierCargoLocked")
        .map { it.toBoolean() }
        .orElse(true)
val nativeProfile = providers.gradleProperty("rapierNativeProfile").orElse("release")
val nativeFeatures = providers.gradleProperty("rapierNativeFeatures").orElse("")
val rustDirectory = layout.projectDirectory.dir("src/main/rust")
val nativeResourceOs = detectNativeResourceOs()
val nativeResourceArch = detectNativeResourceArch(nativeResourceOs)
val nativeLibraryName = nativeLibraryNameFor(nativeResourceOs)
val nativeResourcePath = "native/$nativeResourceOs/$nativeResourceArch"
val nativeResourceEntry = "$nativeResourcePath/$nativeLibraryName"
val rapierPatchFile = rustDirectory.file("patches/rapier3d-$rapierVersion-simd-body-masks.patch")
val patchedRapierDirectory = rustDirectory.dir("target/impulse-patched/rapier3d-$rapierVersion-impulse")
val patchedCargoConfig = rustDirectory.file("target/impulse-patched/cargo-config.toml")
val nativeBuildDirectory = nativeProfile.map { profile ->
    if (profile == "release") {
        "release"
    } else {
        "debug"
    }
}



extensions.extraProperties["impulseRapierPackagedNativeResourceEntries"] = listOf(
        nativeResourceEntry)

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
    ProcessBuilder("bash", "-lc", "command -v cargo >/dev/null 2>&1")
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
            rapierPatchFile,
    )
    outputs.dir(patchedRapierDirectory)
    outputs.file(patchedCargoConfig)

    environment("RAPIER_VERSION", rapierVersion)
    environment("PATCHED_RAPIER_PATH", patchedRapierDirectory.asFile.absolutePath)
    environment("PATCHED_CARGO_CONFIG_PATH", patchedCargoConfig.asFile.absolutePath)
    environment("RAPIER_PATCH_PATH", rapierPatchFile.asFile.absolutePath)

    when (nativeResourceOs) {
        "linux" -> {
            inputs.file(layout.projectDirectory.file("src/main/rust/scripts/prepare-rapier-patched.sh"))
            commandLine(
                    "bash",
                    layout.projectDirectory.file("src/main/rust/scripts/prepare-rapier-patched.sh").asFile.absolutePath
            )
        }

        "windows" -> {
            inputs.file(layout.projectDirectory.file("src/main/rust/scripts/prepare-rapier-patched.ps1"))
            commandLine(
                    "pwsh",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    layout.projectDirectory.file("src/main/rust/scripts/prepare-rapier-patched.ps1").asFile.absolutePath
            )
        }

        else -> {
            throw GradleException(
                    "Unsupported Rapier native packaging OS: "
                            + System.getProperty("os.name")
            )
        }
    }
}

val cargoBuildRapierNative by tasks.registering(Exec::class) {
    description = "Builds the Rapier native library using Cargo"
    dependsOn(preparePatchedRapier)
    onlyIf { buildNative.get() }
    workingDir = rustDirectory.asFile

    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.toml"))
    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.lock"))
    inputs.dir(layout.projectDirectory.dir("src/main/rust/src"))
    inputs.file(rapierPatchFile)
    inputs.dir(patchedRapierDirectory)
    inputs.property("rapierNativeFeatures", nativeFeatures)
    outputs.file(nativeBuildDirectory.map { layout.projectDirectory.file("src/main/rust/target/$it/$nativeLibraryName") })

    doFirst {
        println("rapierCargoLocked = ${rapierCargoLocked.get()}")
        val command = mutableListOf("cargo",
                "--config",
                patchedCargoConfig.asFile.absolutePath,
                "build")

        if (rapierCargoLocked.get()) {
            command.add("--locked")
        }

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
        layout.projectDirectory.file("src/main/rust/target/$it/$nativeLibraryName")
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
    filesMatching("manifest.json") {
        expand(mapOf(
                "version" to project.version,
                "hytaleVersion" to project.property("hytale_version")
        ))
    }
}

tasks.compileJava {
    dependsOn(project(":impulse-core").tasks.named("downloadAssetsZip"))
}

tasks.named("updatePluginManifest").configure {
    enabled = false
}

tasks.named("validateManifest").configure {
    enabled = false
}

hytaleTools {
    modId = property("mod_name") as String + "Rapier"
    mainClass = "dev.hytalemodding.impulse.rapier.RapierBackendPlugin"
    modCredits = property("mod_credits") as String
    modUrl = property("mod_website") as String
    modDescription = "Rapier backend provider for Impulse"
}

dependencies {
    api(project(":impulse-api"))

    implementation(libs.snaploader)
    compileOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")

    testImplementation(testFixtures(project(":impulse-api")))
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
                .filter { file -> !file.name.startsWith("impulse-api-") }
                .map { file -> if (file.isDirectory) file else zipTree(file) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
