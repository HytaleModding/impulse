plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version

repositories {
    maven {
        url = uri("https://gitlab.com/api/v4/projects/82033924/packages/maven")
    }
}

val coreModuleName = "dev.hytalemodding.impulse.core"
// These parent dependencies are shared by the core plugin and inherited by bundled subplugins.
val impulseManifestDependencies = listOf(
    "Hytale:AssetModule=*",
    "Hytale:BlockTypeModule=*",
    "Hytale:EntityModule=*",
    "Hytale:LegacyModule=*"
).joinToString(",")

val moduleInfoModulePath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(project(":impulse-backend-api"))
    compileOnly(project(":impulse-early-plugin"))
    compileOnly(libs.crucible)
    compileOnly(libs.lombok)

    moduleInfoModulePath(libs.joml)
    moduleInfoModulePath(libs.jsr305)
    moduleInfoModulePath(libs.crucible)

    annotationProcessor(libs.lombok)

    testImplementation(testFixtures(project(":impulse-backend-api")))
    testImplementation(libs.objenesis)
    testCompileOnly(project(":impulse-early-plugin"))
    testRuntimeOnly(project(":impulse-early-plugin"))
    testCompileOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")
    testRuntimeOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")

}

val impulseApiJar = project(":impulse-backend-api").tasks.named<org.gradle.jvm.tasks.Jar>("jar")

tasks.named<JavaCompile>("compileJava") {
    doFirst {
        destinationDirectory.file("module-info.class").get().asFile.delete()
    }
}

val compileCoreModuleInfo by tasks.registering(org.gradle.api.tasks.compile.JavaCompile::class) {
    description = "Compiles the impulse-core Java module descriptor."

    dependsOn(tasks.named("compileJava"))
    dependsOn(impulseApiJar)

    source = fileTree("src/module-info") {
        include("module-info.java")
    }
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("classes/java/module-info"))
    options.release.set((project.property("java_version") as String).toInt())
    inputs.files(moduleInfoModulePath)
    inputs.file(impulseApiJar.flatMap { it.archiveFile })

    doFirst {
        options.compilerArgs = listOf(
            "-Xlint:-requires-transitive-automatic",
            "--module-path",
            files(impulseApiJar.get().archiveFile.get().asFile, moduleInfoModulePath).asPath,
            "--patch-module",
            "$coreModuleName=${sourceSets.main.get().output.classesDirs.asPath}"
        )
    }
}

tasks.named("classes") {
    dependsOn(compileCoreModuleInfo)
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    dependsOn(compileCoreModuleInfo)
    from(compileCoreModuleInfo.flatMap { it.destinationDirectory }) {
        include("module-info.class")
    }
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
    manifestDependencies = impulseManifestDependencies
    manifestOptionalDependencies = "com.ionforgelabs:crucible=*"

    subPlugin (
        "ImpulseControl",
        "dev.hytalemodding.impulse.core.internal.modules.control.ControlModule",
        false, /* disabledByDefault */
        false  /* includeAssetPack */
    )

    subPlugin (
        "ImpulsePhysicsEntity",
        "dev.hytalemodding.impulse.core.internal.modules.physicsentity.PhysicsEntitySubPlugin",
        false, /* disabledByDefault */
        false  /* includeAssetPack */
    )

    subPlugin (
        "ImpulsePhysicsChunk",
        "dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkSubPlugin",
        false, /* disabledByDefault */
        false  /* includeAssetPack */
    )
}

tasks.named("updatePluginManifest") {
    doLast {
        val manifestFile = file("src/main/resources/manifest.json")

        @Suppress("UNCHECKED_CAST")
        val manifestJson = groovy.json.JsonSlurper().parse(manifestFile)
            as MutableMap<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val subPlugins = manifestJson["SubPlugins"] as? List<MutableMap<String, Any?>>
            ?: return@doLast

        fun MutableMap<String, Any?>.mergeLoadBefore(loadBefore: Map<String, String>) {
            @Suppress("UNCHECKED_CAST")
            val existingLoadBefore = (this["LoadBefore"] as? Map<String, String>)
                ?.toMutableMap()
                ?: linkedMapOf()
            existingLoadBefore.putAll(loadBefore)
            this["LoadBefore"] = existingLoadBefore
        }

        subPlugins.firstOrNull { it["Name"] == "ImpulsePhysicsEntity" }
            ?.mergeLoadBefore(mapOf(
                "HytaleModding:ImpulseControl" to "*",
                "HytaleModding:ImpulsePhysicsChunk" to "*"
            ))

        manifestFile.writeText(
            groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(manifestJson))
                + System.lineSeparator()
        )
    }
}
