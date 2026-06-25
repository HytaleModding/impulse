import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    id("java-library")
}

version = rootProject.version

evaluationDependsOn(":impulse-core")

val coreMain = project(":impulse-core")
    .extensions
    .getByType<SourceSetContainer>()
    .named("main")
    .get()

dependencies {
    api(project(":impulse-backends:api"))
    compileOnly(coreMain.output)
    compileOnly(files(coreMain.compileClasspath))
    compileOnly(project(":impulse-early-plugin"))
    testImplementation(project(":impulse-core"))
    testImplementation(project(":impulse-early-plugin"))
    testImplementation(testFixtures(project(":impulse-backends:api")))
    testImplementation(libs.objenesis)
    testRuntimeOnly(project(":impulse-early-plugin"))
    testCompileOnly(files(coreMain.compileClasspath))
    testRuntimeOnly(files(coreMain.compileClasspath))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(":impulse-core:compileJava")
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Djava.util.logging.manager=com.hypixel.hytale.logger.backend.HytaleLogManager")
}

val removeStandaloneManifest by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.file("resources/main/manifest.json"))
}

tasks.named("processResources") {
    dependsOn(removeStandaloneManifest)
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    exclude("manifest.json")
}
