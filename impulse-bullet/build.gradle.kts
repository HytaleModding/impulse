plugins {
    id("java-library")
}

dependencies {
    api(project(":impulse-api"))

    implementation(libs.libbulletjme)
    runtimeOnly(variantOf(libs.libbulletjme.native) { classifier("SpRelease") })
    implementation(libs.snaploader)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(testFixtures(project(":impulse-api")))
}
