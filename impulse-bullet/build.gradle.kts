import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("java-library")
}

dependencies {
    api(project(":impulse-api"))

    implementation(project(":impulse-native-loader"))
    implementation(libs.libbulletjme)
    runtimeOnly(variantOf(libs.libbulletjme.native) { classifier("SpRelease") })

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

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
