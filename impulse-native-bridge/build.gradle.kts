import java.net.URI

plugins {
    java
}

val osName = detectNativeOs()
val arch = detectNativeResourceArch(osName)

fun detectNativeOs(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("linux") -> "linux"
        osName.contains("mac") || osName.contains("darwin") -> "osx"
        osName.contains("windows") -> "windows"
        else -> throw GradleException("Unsupported OS context for native bindings generation: ${System.getProperty("os.name")}")
    }
}

fun detectNativeResourceArch(resourceOs: String): String {
    val osArch = System.getProperty("os.arch").lowercase()
    val resourceArch = when (osArch) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        "arm", "arm32" -> "arm32"
        else -> throw GradleException("Unsupported architecture context for native bindings generation: ${System.getProperty("os.arch")}")
    }
    if (resourceOs == "windows" && resourceArch != "x86_64") {
        throw GradleException("Unsupported Windows architecture configuration (Only x86_64 supported): ${System.getProperty("os.arch")}")
    }
    return resourceArch
}

val platformClassifier = when {
    osName.contains("windows") -> "windows-x64"
    osName.contains("osx") -> if (arch.contains("aarch64") || arch.contains("arm64")) "macos-aarch64" else "macos-x64"
    else -> if (arch.contains("aarch64") || arch.contains("arm64")) "linux-aarch64" else "linux-x64"
}

val jextractUrl = "https://download.java.net/java/early_access/jextract/25/2/openjdk-25-jextract+2-4_${platformClassifier}_bin.tar.gz"

val downloadDir = layout.buildDirectory.dir("jextract-download")
val extractedToolDir = layout.buildDirectory.dir("jextract-tool")
val generatedSourcesDir = layout.buildDirectory.dir("generated/sources/jextract")

val downloadJextract by tasks.registering {
    group = "build"
    description = "Downloads the native jextract tar.gz from OpenJDK servers"

    val targetFile = downloadDir.get().file("jextract-bin.tar.gz").asFile

    inputs.property("jextractUrl", jextractUrl)
    outputs.file(targetFile)

    doLast {
        if (!targetFile.exists()) {
            targetFile.parentFile.mkdirs()
            println("Downloading generic jextract tool layout from: $jextractUrl")

            URI(jextractUrl).toURL().openStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

val extractJextract by tasks.registering(Copy::class) {
    group = "build"
    description = "Extracts the downloaded cross-platform jextract tarball"
    dependsOn(downloadJextract)

    val archiveFile = downloadDir.get().file("jextract-bin.tar.gz").asFile

    from(tarTree(resources.gzip(archiveFile)))
    into(extractedToolDir)
}

val generateApiBindings by tasks.registering(Exec::class) {
    group = "build"
    description = "Generates Panama Java bindings using the platform-specific extracted tool"
    dependsOn(extractJextract)

    val headerFile = file("src/main/c/bridge.h")

    inputs.file(headerFile)
    outputs.dir(generatedSourcesDir)

    val targetPackage = "dev.impulse.nativebridge.bindings"
    val className = "NativeBridge"
    val libraryLinkName = "impulse_bridge"

    if (osName.contains("win")) {
        val jextractScript = File(extractedToolDir.get().asFile, "jextract-25/bin/jextract.bat")

        commandLine(
                "cmd", "/c", jextractScript.absolutePath,
                "--output", generatedSourcesDir.get().asFile.absolutePath,
                "--target-package", targetPackage,
                "--header-class-name", className,
                "-l", libraryLinkName,
                headerFile.absolutePath
        )
    } else {
        val jextractBin = File(extractedToolDir.get().asFile, "jextract-25/bin/jextract")

        commandLine(
                jextractBin.absolutePath,
                "--output", generatedSourcesDir.get().asFile.absolutePath,
                "--target-package", targetPackage,
                "--header-class-name", className,
                "-l", libraryLinkName,
                headerFile.absolutePath
        )
    }

    if (osName.contains("mac")) {
        val jextractBin = File(extractedToolDir.get().asFile, "jextract-25/bin/jextract")
        doFirst {
            providers.exec {
                commandLine("xattr", "-r", "-d", "com.apple.quarantine", jextractBin.parentFile.parentFile.absolutePath)
                isIgnoreExitValue = true
            }
        }
    }
}

sourceSets {
    main {
        java.srcDir(generatedSourcesDir)
    }
}

tasks.compileJava {
    dependsOn(generateApiBindings)
}

dependencies {
    implementation(project(":impulse-api"))
}