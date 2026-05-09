plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version

dependencies {
    implementation(project(":impulse-api"))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

hytaleTools {
    modId = property("mod_name") as String
    mainClass = "dev.hytalemodding.impulse.core.ImpulsePlugin"
    modCredits = property("mod_credits") as String
    modUrl = property("mod_website") as String
    modDescription = property("mod_description") as String
}
