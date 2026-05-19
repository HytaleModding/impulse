plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version

dependencies {
    implementation(project(":impulse-api"))
    compileOnly(project(":impulse-core"))
    runtimeOnly(project(":impulse-bullet"))
    runtimeOnly(project(":impulse-rapier"))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks.compileJava {
    dependsOn(tasks.named("downloadAssetsZip"))
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
    manifestDependencies = "HytaleModding:Impulse=*"
}
