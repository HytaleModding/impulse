plugins {
    id("com.azuredoom.hytale-tools")
}

version = rootProject.version
val impulsePhysicsEntityDependencies = listOf(
    "HytaleModding:Impulse=*",
    "Hytale:AssetModule=*",
    "Hytale:BlockTypeModule=*",
    "Hytale:EntityModule=*",
    "Hytale:LegacyModule=*"
).joinToString(",")

dependencies {
    compileOnly(project(":impulse-core"))
    compileOnly(project(":impulse-early-plugin"))
    testImplementation(project(":impulse-core"))
}

hytaleTools {
    modId = "ImpulsePhysicsEntity"
    mainClass = "dev.hytalemodding.impulse.physicsentity.ImpulsePhysicsEntityPlugin"
    modCredits = property("mod_credits") as String
    modUrl = property("mod_website") as String
    modDescription = "Impulse EntityStore projection integration"
    manifestServerVersion = property("hytale_version") as String
    manifestDependencies = impulsePhysicsEntityDependencies
}
