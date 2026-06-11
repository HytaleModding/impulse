import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException

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
    "Hytale:EntityModule=*",
    "Hytale:LegacyModule=*"
).joinToString(",")
// hytale-tools does not expose subplugin dependency maps, so child-only deps are patched below.
val impulseWorldCollisionDependencies = linkedMapOf(
    "Hytale:BlockTypeModule" to "*"
)

val moduleInfoModulePath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(project(":impulse-api"))
    testImplementation(testFixtures(project(":impulse-api")))
    testImplementation(libs.objenesis)
    testCompileOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")
    testRuntimeOnly("com.hypixel.hytale:Server:${property("hytale_version") as String}")
    compileOnly(libs.crucible)

    moduleInfoModulePath(libs.joml)
    moduleInfoModulePath(libs.jsr305)
    moduleInfoModulePath(libs.crucible)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

val impulseApiJar = project(":impulse-api").tasks.named<org.gradle.jvm.tasks.Jar>("jar")

tasks.named<org.gradle.api.tasks.compile.JavaCompile>("compileJava") {
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

tasks.named("updatePluginManifest").configure {
    // Keep Gradle's up-to-date checks sensitive to the post-generation child dependency patch.
    inputs.property(
        "impulseWorldCollisionDependencies",
        impulseWorldCollisionDependencies.entries.joinToString(",") { "${it.key}=${it.value}" }
    )

    doLast {
        val manifestFile = layout.projectDirectory.file("src/main/resources/manifest.json").asFile
        @Suppress("UNCHECKED_CAST")
        val manifestJson = JsonSlurper().parseText(manifestFile.readText()) as MutableMap<String, Any?>
        val subPlugins = manifestJson["SubPlugins"] as? List<*>
            ?: throw GradleException("Generated manifest is missing SubPlugins; cannot patch ImpulseWorldCollision dependencies.")

        // Fail fast if the hytale-tools generated subplugin list no longer matches this patch.
        @Suppress("UNCHECKED_CAST")
        val worldCollision = subPlugins
            .filterIsInstance<MutableMap<String, Any?>>()
            .firstOrNull { it["Name"] == "ImpulseWorldCollision" }
            ?: throw GradleException("Generated manifest is missing ImpulseWorldCollision; cannot patch its dependencies.")

        worldCollision["Dependencies"] = impulseWorldCollisionDependencies

        manifestFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifestJson)))
    }
}
