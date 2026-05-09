plugins {
    id("java-library")
}

dependencies {
    api(libs.libbulletjme)
    runtimeOnly(variantOf(libs.libbulletjme.native) { classifier("SpRelease") })
    implementation(libs.snaploader)
    api(libs.jsr305)
    api(libs.joml)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
