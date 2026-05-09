plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version

dependencies {
    implementation(project(":impulse-api"))
    compileOnly(project(":impulse-core"))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks.compileJava {
    dependsOn(tasks.named("downloadAssetsZip"))
}

hytaleTools {
    modId = property("mod_name") as String + "Examples"
    mainClass = "dev.hytalemodding.impulse.examples.ImpulseExamplesPlugin"
    modCredits = property("mod_credits") as String
    modUrl = property("mod_website") as String
    modDescription = "Example plugins for Impulse"
    manifestDependencies = "HytaleModding:Impulse=*"
}
