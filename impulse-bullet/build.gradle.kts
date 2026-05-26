import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("java-library")
}

dependencies {
    api(project(":impulse-api"))

    implementation(libs.libbulletjme)
    runtimeOnly(variantOf(libs.libbulletjme.native) { classifier("SpRelease") })
    implementation(libs.snaploader)
    compileOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(testFixtures(project(":impulse-api")))
}

tasks.processResources {
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

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { file -> !file.name.startsWith("impulse-api-") }
            .map { file -> if (file.isDirectory) file else zipTree(file) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
