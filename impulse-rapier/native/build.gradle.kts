plugins {
    id("java-library")
    id("maven-publish")
}

val rapierVersion = "0.32.0"
val nativeProfile = providers.gradleProperty("rapierNativeProfile").orElse("release")
val nativeFeatures = providers.gradleProperty("rapierNativeFeatures").orElse("")
val buildNative = providers.gradleProperty("buildRapierNative")
        .map { it.toBoolean() }
        .orElse(true)

val rustDirectory = layout.projectDirectory.dir("src/main/rust")
val targetDirectory = rustDirectory.dir("target")

val nativeResourceOs = detectNativeResourceOs()
val nativeResourceArch = detectNativeResourceArch(nativeResourceOs)
val nativeLibraryName = nativeLibraryNameFor(nativeResourceOs)
val nativeResourcePath = "native/$nativeResourceOs/$nativeResourceArch"

val nativeBuildDirectory = nativeProfile.map { profile ->
    if (profile == "release") "release" else "debug"
}

fun detectNativeResourceOs(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("linux") -> "linux"
        osName.contains("mac") || osName.contains("darwin") -> "osx"
        osName.contains("windows") -> "windows"
        else -> throw GradleException("Unsupported Rapier native packaging OS: ${System.getProperty("os.name")}")
    }
}

fun detectNativeResourceArch(resourceOs: String): String {
    val osArch = System.getProperty("os.arch").lowercase()
    val resourceArch = when (osArch) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        "arm", "arm32" -> "arm32"
        else -> throw GradleException("Unsupported Rapier native packaging architecture: ${System.getProperty("os.arch")}")
    }
    if (resourceOs == "windows" && resourceArch != "x86_64") {
        throw GradleException("Unsupported Rapier native packaging platform: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
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

val preparePatchedRapier by tasks.registering(Exec::class) {
    description = "Prepares the patched Rapier source tree."
    onlyIf { buildNative.get() }
    workingDir = rustDirectory.asFile

    inputs.files(
            layout.projectDirectory.file("src/main/rust/Cargo.toml"),
            layout.projectDirectory.file("src/main/rust/Cargo.lock"),
            layout.projectDirectory.file("src/main/rust/patches/rapier3d-$rapierVersion-simd-body-masks.patch")
    )
    outputs.dir(targetDirectory)

    environment("RAPIER_VERSION", rapierVersion)
    environment("PATCHED_RAPIER_PATH", rustDirectory.file("target/impulse-patched/rapier3d-$rapierVersion-impulse").asFile.absolutePath)
    environment("RAPIER_PATCH_PATH", rustDirectory.file("patches/rapier3d-$rapierVersion-simd-body-masks.patch").asFile.absolutePath)

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

        else -> throw GradleException("Unsupported Rapier native packaging OS: ${System.getProperty("os.name")}")
    }
}

val cargoBuildRapierNative by tasks.registering(Exec::class) {
    description = "Builds the Rapier native library using Cargo."
    dependsOn(preparePatchedRapier)
    onlyIf { buildNative.get() }
    workingDir = rustDirectory.asFile

    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.toml"))
    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.lock"))
    inputs.dir(layout.projectDirectory.dir("src/main/rust/src"))
    inputs.dir(layout.projectDirectory.dir("src/main/rust/patches"))
    inputs.dir(layout.projectDirectory.dir("src/main/rust/scripts"))
    inputs.property("rapierNativeFeatures", nativeFeatures)

    val nativeBuiltFile = layout.projectDirectory.file(
            "src/main/rust/target/${nativeBuildDirectory.get()}/$nativeLibraryName"
    )
    outputs.file(nativeBuiltFile)

    doFirst {
        val command = mutableListOf(
                "cargo",
                "build",
                "--target-dir",
                targetDirectory.asFile.absolutePath
        )

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

    from(layout.projectDirectory.file("src/main/rust/target/${nativeBuildDirectory.get()}/$nativeLibraryName"))
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

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}