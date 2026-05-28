import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import java.io.File

plugins {
    id("java-library")
}

data class BulletBackendPlatform(
    val taskSuffix: String,
    val archiveClassifier: String,
    val nativeArtifact: String
)

val bulletBackendPlatforms = listOf(
    BulletBackendPlatform("LinuxX64", "linux-x86_64", "Libbulletjme-Linux64"),
    BulletBackendPlatform("LinuxArm64", "linux-arm64", "Libbulletjme-Linux_ARM64"),
    BulletBackendPlatform("OsxArm64", "osx-arm64", "Libbulletjme-MacOSX_ARM64"),
    BulletBackendPlatform("WindowsX64", "windows-x86_64", "Libbulletjme-Windows64")
)
val impulseLicenseFile = rootProject.layout.projectDirectory.file("LICENSE")
val libbulletjmeLicenseFile = rootProject.layout.projectDirectory.file("licenses/LIBBULLETJME_LICENSE")

dependencies {
    api(project(":impulse-api"))

    implementation(project(":impulse-native-loader"))
    implementation(libs.libbulletjme)
    runtimeOnly(variantOf(libs.libbulletjme.native) { classifier("SpRelease") })

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(testFixtures(project(":impulse-api")))
}

val bulletNativeConfigurations = bulletBackendPlatforms.associateWith { platform ->
    configurations.create("bulletNative${platform.taskSuffix}") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
}

bulletBackendPlatforms.forEach { platform ->
    dependencies.add(bulletNativeConfigurations.getValue(platform).name,
        "com.github.stephengold:${platform.nativeArtifact}:${libs.versions.libbulletjme.get()}:SpRelease")
}

fun runtimeClasspathWithoutBundledApiAndHostNative(): List<Any> {
    return configurations.runtimeClasspath.get()
        .filter { file -> !file.name.startsWith("impulse-api-") }
        .filterNot(::isBulletNativeJar)
        .map { file -> if (file.isDirectory) file else zipTree(file) }
}

fun isBulletNativeJar(file: File): Boolean {
    return file.name.startsWith("Libbulletjme-") && file.name.contains("-Sp")
}

fun Jar.includeBackendRuntime() {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        runtimeClasspathWithoutBundledApiAndHostNative()
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

fun Jar.includeBackendLicenseNotices() {
    from(impulseLicenseFile) {
        into("META-INF/licenses/impulse")
        rename { "LICENSE" }
    }
    from(libbulletjmeLicenseFile) {
        into("META-INF/licenses/libbulletjme")
        rename { "LICENSE" }
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { file -> !file.name.startsWith("impulse-api-") }
            .map { file -> if (file.isDirectory) file else zipTree(file) }
    })
    includeBackendLicenseNotices()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

val platformJarTasks = bulletBackendPlatforms.map { platform ->
    tasks.register<Jar>("packageBulletBackend${platform.taskSuffix}") {
        group = "build"
        description = "Packages the Bullet backend provider jar for ${platform.archiveClassifier}"
        archiveClassifier.set(platform.archiveClassifier)

        from(sourceSets.main.get().output)
        includeBackendRuntime()
        includeBackendLicenseNotices()
        from({
            bulletNativeConfigurations.getValue(platform).files
                .map { file -> if (file.isDirectory) file else zipTree(file) }
        })
    }
}

tasks.register<Jar>("packageBulletBackendUniversal") {
    group = "build"
    description = "Packages the Bullet backend provider jar with every configured native library"
    archiveClassifier.set("universal")

    from(sourceSets.main.get().output)
    includeBackendRuntime()
    includeBackendLicenseNotices()
    from({
        bulletNativeConfigurations.values
            .flatMap { configuration -> configuration.files }
            .map { file -> if (file.isDirectory) file else zipTree(file) }
    })
}

tasks.register("packageBulletBackendPlatformJars") {
    group = "build"
    description = "Packages all Bullet per-platform backend jars plus the universal jar"
    dependsOn(platformJarTasks)
    dependsOn(tasks.named("packageBulletBackendUniversal"))
}
