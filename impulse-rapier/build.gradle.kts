plugins {
    id("java-library")
}

val nativeLibraryName = "libimpulse_rapier.so"
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

val copyRapierNative by tasks.registering(Copy::class) {
    dependsOn(cargoBuildRapierNative)
    onlyIf { buildNative.get() }

    from(nativeBuildDirectory.map {
        layout.projectDirectory.file("src/main/rust/target/$it/$nativeLibraryName")
    })
    into(layout.buildDirectory.dir("generated/rapier-native/native/linux/x86_64"))
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
