plugins {
    id("java-library")
}

val nativeLibraryName = "libimpulse_rapier.so"
val nativeProfile = providers.gradleProperty("rapierNativeProfile").orElse("release")
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

val cargoBuildRapierNative by tasks.registering(Exec::class) {
    onlyIf { buildNative.get() }
    workingDir = layout.projectDirectory.dir("src/main/rust").asFile

    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.toml"))
    inputs.dir(layout.projectDirectory.dir("src/main/rust/src"))
    outputs.file(nativeBuildDirectory.map { layout.projectDirectory.file("src/main/rust/target/$it/$nativeLibraryName") })

    doFirst {
        val args = mutableListOf("cargo", "build")
        if (nativeProfile.get() == "release") {
            args.add("--release")
        }
        commandLine(args)
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
