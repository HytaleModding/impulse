plugins {
    id("java-library")
    id("com.azuredoom.hytale-tools")
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

    runtimeOnly(project("native"))

    implementation(libs.snaploader)
    compileOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")

    testImplementation(testFixtures(project(":impulse-api")))
}

tasks.jar {
    dependsOn(project(":impulse-rapier:native").tasks.named("jar"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        configurations.runtimeClasspath.get()
                .filter { file -> !file.name.startsWith("impulse-api-") }
                .flatMap { file ->
                    if (file.isDirectory) {
                        listOf(file)
                    } else {
                        listOf(zipTree(file).matching { exclude("manifest.json") })
                    }
                }
    })

    from(layout.buildDirectory.dir("resources/main")) {
        include("manifest.json")
    }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.processResources {
    doFirst {
        println("DEBUG: processResources for ${project.path} - project.version='${project.version}', hytale_version='${project.findProperty("hytale_version") ?: "null"}'")
    }
    filesMatching("manifest.json") {
        expand(
                "version" to project.version.toString(),
                "hytaleVersion" to (project.findProperty("hytale_version") ?: "").toString()
        )
    }
}