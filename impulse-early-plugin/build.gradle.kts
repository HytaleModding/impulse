plugins {
    `java-library`
}

version = rootProject.version

val coreVineServerJar = project(":impulse-core").configurations.named("vineServerJar")
val coreVineServerJarFiles = files({ coreVineServerJar.get().files })

dependencies {
    compileOnly(coreVineServerJarFiles)
    compileOnly(libs.jsr305)
    testCompileOnly(coreVineServerJarFiles)
    testRuntimeOnly(coreVineServerJarFiles)
}
