plugins {
    id("java-library")
}

val targetPlatform = providers.gradleProperty("rapierBuildPlatform").orElse("linux-x86_64")
val nativeLibraryName = targetPlatform.map { platform ->
    when (platform) {
        "windows-x86_64" -> "impulse_rapier.dll"
        "osx-x86_64", "osx-arm64" -> "libimpulse_rapier.dylib"
        else -> "libimpulse_rapier.so"
    }
}
val outputDirectory = targetPlatform.map { platform ->
    when (platform) {
        "linux-x86_64" -> "linux/x86_64"
        "linux-arm64" -> "linux/arm64"
        "windows-x86_64" -> "windows/x86_64"
        "osx-x86_64" -> "osx/x86_64"
        "osx-arm64" -> "osx/arm64"
        else -> "linux/x86_64"
    }
}
val cargoTarget = targetPlatform.map { platform ->
    when (platform) {
        "linux-arm64" -> "aarch64-unknown-linux-gnu"
        "windows-x86_64" -> "x86_64-pc-windows-msvc"
        "osx-x86_64" -> "x86_64-apple-darwin"
        "osx-arm64" -> "aarch64-apple-darwin"
        else -> "x86_64-unknown-linux-gnu"
    }
}

val rapierVersion = "0.32.0"
val nativeProfile = providers.gradleProperty("rapierNativeProfile").orElse("release")
val nativeFeatures = providers.gradleProperty("rapierNativeFeatures").orElse("")
val rustDirectory = layout.projectDirectory.dir("src/main/rust")
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
    outputs.file(cargoTarget.flatMap { target ->
        nativeBuildDirectory.flatMap { buildDir ->
            nativeLibraryName.map { libName ->
                layout.projectDirectory.file("src/main/rust/target/$target/$buildDir/$libName")
            }
        }
    })

    doFirst {
        val useCross = System.getenv("USE_CROSS")?.lowercase() == "true"
        val cargoCmd = if (useCross) "cross" else "cargo"

        val command = mutableListOf(cargoCmd,
            "--config",
            patchedCargoConfig.asFile.absolutePath,
            "build",
            "--locked",
            "--target", cargoTarget.get())
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

val copyRapierNative by tasks.registering {
    // Use a simple task and perform the copy at execution time where provider values can be resolved
    dependsOn(cargoBuildRapierNative)
    onlyIf { buildNative.get() }

    doLast {
        val target = cargoTarget.get()
        val buildDir = nativeBuildDirectory.get()
        val libName = nativeLibraryName.get()
        val outDir = outputDirectory.get()

        val src = layout.projectDirectory.file("src/main/rust/target/$target/$buildDir/$libName").asFile
        val destDir = layout.buildDirectory.dir("generated/rapier-native/native/$outDir").get().asFile
        destDir.mkdirs()

        if (!src.exists()) {
            throw GradleException("Rapier native not found: ${src.absolutePath}")
        }

        copy {
            from(src)
            into(destDir)
        }
    }
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated/rapier-native"))
    }
}

tasks.processResources {
    dependsOn(copyRapierNative)
}

dependencies {
    api(project(":impulse-api"))

    implementation(libs.snaploader)

    testImplementation(testFixtures(project(":impulse-api")))
}
