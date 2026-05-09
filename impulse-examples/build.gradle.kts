plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version

dependencies {
    implementation(project(":impulse-api"))
}

hytaleTools {
    modId = "impulse-examples"
    mainClass = "dev.hytalemodding.impulse.examples.ImpulseExamplesPlugin"
    modCredits = property("mod_credits") as String
    modDescription = "Example plugins for Impulse"
}

tasks.compileJava {
    dependsOn(":impulse-core:downloadAssetsZip")
}
