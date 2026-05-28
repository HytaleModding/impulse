import org.gradle.api.GradleException
import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("java-library")
}

val rapierVersion = "0.32.0"
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
    onlyIf { buildNative.get() }
    workingDir = rustDirectory.asFile

    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.toml"))
    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.lock"))
    inputs.file(rapierPatchFile)
    outputs.dir(patchedRapierDirectory)
    outputs.file(patchedCargoConfig)

    doFirst {
        val patchedRapierPath = patchedRapierDirectory.asFile.absolutePath
        val patchedCargoConfigPath = patchedCargoConfig.asFile.absolutePath
        val rapierPatchPath = rapierPatchFile.asFile.absolutePath
        val dollar = "$"
        val script = """
            set -euo pipefail

            registry_root="${dollar}{CARGO_HOME:-${dollar}{HOME}/.cargo}/registry/src"
            find_rapier_source() {
                if [ -d "${dollar}registry_root" ]; then
                    find "${dollar}registry_root" -path "*/rapier3d-$rapierVersion" -type d | sort | sed -n '1p'
                fi
                return 0
            }

            base_dir="${dollar}(find_rapier_source)"
            if [ -z "${dollar}base_dir" ]; then
                fetch_dir="${dollar}(dirname "$patchedCargoConfigPath")/fetch"
                fetch_manifest="${dollar}fetch_dir/Cargo.toml"
                mkdir -p "${dollar}fetch_dir/src"
                touch "${dollar}fetch_dir/src/lib.rs"
                cat > "${dollar}fetch_manifest" <<EOF
[package]
name = "impulse-rapier-fetch"
version = "0.0.0"
edition = "2021"
publish = false

[dependencies]
rapier3d = { version = "=$rapierVersion", default-features = false, features = ["dim3", "f32", "parallel", "profiler"] }
EOF
                cargo fetch --manifest-path "${dollar}fetch_manifest"
                base_dir="${dollar}(find_rapier_source)"
            fi
            if [ -z "${dollar}base_dir" ]; then
                echo "Unable to locate rapier3d-$rapierVersion in ${dollar}registry_root" >&2
                exit 1
            fi

            rm -rf "$patchedRapierPath"
            mkdir -p "${dollar}(dirname "$patchedRapierPath")"
            cp -a "${dollar}base_dir" "$patchedRapierPath"
            patch -d "$patchedRapierPath" -p1 < "$rapierPatchPath"

            mkdir -p "${dollar}(dirname "$patchedCargoConfigPath")"
            cat > "$patchedCargoConfigPath" <<EOF
[patch.crates-io]
rapier3d = { path = "$patchedRapierPath" }
EOF
        """.trimIndent()
        commandLine("bash", "-lc", script)
    }
}

val cargoBuildRapierNative by tasks.registering(Exec::class) {
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
        val command = mutableListOf("cargo",
            "--config",
            patchedCargoConfig.asFile.absolutePath,
            "build",
            "--locked")
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
}

dependencies {
    api(project(":impulse-api"))

    implementation(project(":impulse-native-loader"))

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
