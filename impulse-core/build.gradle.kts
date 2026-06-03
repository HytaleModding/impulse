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
    testImplementation(testFixtures(project(":impulse-api")))
    testCompileOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")
    testRuntimeOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")
    compileOnly(libs.crucible)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Djava.util.logging.manager=com.hypixel.hytale.logger.backend.HytaleLogManager")
}

hytaleTools {
    modId = property("mod_name") as String
    mainClass = "dev.hytalemodding.impulse.core.ImpulsePlugin"
    modCredits = property("mod_credits") as String
    modUrl = property("mod_website") as String
    modDescription = property("mod_description") as String
    manifestServerVersion = property("hytale_version") as String
    manifestOptionalDependencies = "com.ionforgelabs:crucible=*"

    subPlugin (
        "ImpulseWorldCollision",
        "dev.hytalemodding.impulse.core.plugin.modules.worldcollision.ImpulseWorldCollisionPlugin",
        false, /* disabledByDefault */
        false  /* includeAssetPack */
    )
    subPlugin (
        "ImpulseControl",
        "dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControlPlugin",
        false, /* disabledByDefault */
        false  /* includeAssetPack */
    )
}
