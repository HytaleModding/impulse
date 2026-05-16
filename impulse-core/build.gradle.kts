plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version

repositories {
    maven {
        url = uri("https://gitlab.com/api/v4/projects/82033924/packages/maven")
    }
}

dependencies {
    implementation(project(":impulse-api"))
    testCompileOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")
    testRuntimeOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")
    compileOnly(libs.crucible)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

hytaleTools {
    modId = property("mod_name") as String
    mainClass = "dev.hytalemodding.impulse.core.ImpulsePlugin"
    modCredits = property("mod_credits") as String
    modUrl = property("mod_website") as String
    modDescription = property("mod_description") as String
    manifestOptionalDependencies = "com.ionforgelabs:crucible=*"
}
