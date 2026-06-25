import org.gradle.api.tasks.testing.Test

plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version

dependencies {
    implementation(project(":impulse-backends:api"))
    compileOnly(project(":impulse-core"))
    compileOnly(project(":impulse-early-plugin"))
    testImplementation(project(":impulse-core"))
    testImplementation(project(":impulse-early-plugin"))
    testImplementation(testFixtures(project(":impulse-backends:api")))
    testImplementation(libs.objenesis)
    testRuntimeOnly(project(":impulse-early-plugin"))
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
    modId = property("mod_name") as String + "Control"
    mainClass = "dev.hytalemodding.impulse.builtin.control.ImpulseControlPlugin"
    modCredits = property("mod_credits") as String
    modUrl = property("mod_website") as String
    modDescription = "Official kinematic-control builtin for Impulse"
    manifestServerVersion = property("hytale_version") as String
    manifestDependencies = listOf(
        "HytaleModding:Impulse=*",
        "HytaleModding:ImpulsePhysicsEntity=*"
    ).joinToString(",")
}
