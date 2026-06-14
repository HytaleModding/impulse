import org.gradle.api.tasks.testing.Test

plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version

dependencies {
    implementation(project(":impulse-api"))
    compileOnly(project(":impulse-core"))
    compileOnly(project(":impulse-early-plugin"))
    testImplementation(project(":impulse-core"))
    testCompileOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")
    testRuntimeOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks.compileJava {
    dependsOn(tasks.named("downloadAssetsZip"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Djava.util.logging.manager=com.hypixel.hytale.logger.backend.HytaleLogManager")
}

val downloadAssetsZip = tasks.named("downloadAssetsZip")

project(":impulse-core").tasks.named("compileJava") {
    mustRunAfter(downloadAssetsZip)
}

hytaleTools {
    modId = property("mod_name") as String + "Examples"
    mainClass = "dev.hytalemodding.impulse.examples.ImpulseExamplesPlugin"
    modCredits = property("mod_credits") as String
    modUrl = property("mod_website") as String
    modDescription = "Example plugins for Impulse"
    manifestServerVersion = property("hytale_version") as String
    manifestDependencies = "HytaleModding:Impulse=*"
}
