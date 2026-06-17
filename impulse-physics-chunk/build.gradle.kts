plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version
val impulsePhysicsChunkDependencies = listOf(
    "HytaleModding:Impulse=*",
    "Hytale:AssetModule=*",
    "Hytale:BlockTypeModule=*",
    "Hytale:EntityModule=*",
    "Hytale:LegacyModule=*"
).joinToString(",")

dependencies {
    compileOnly(project(":impulse-api"))
    compileOnly(project(":impulse-core"))
    compileOnly(project(":impulse-early-plugin"))
    testImplementation(project(":impulse-core"))
}

hytaleTools {
    modId = "ImpulsePhysicsChunk"
    mainClass = "dev.hytalemodding.impulse.physicschunk.ImpulsePhysicsChunkPlugin"
    modCredits = property("mod_credits") as String
    modUrl = property("mod_website") as String
    modDescription = "Impulse ChunkStore world-collision integration"
    manifestServerVersion = property("hytale_version") as String
    manifestDependencies = impulsePhysicsChunkDependencies
}
