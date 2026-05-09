plugins {
    id("java-library")
}

dependencies {
    implementation(libs.libbulletjme)
    runtimeOnly(variantOf(libs.libbulletjme.native) { classifier("SpRelease") })
    implementation(libs.snaploader)
}
