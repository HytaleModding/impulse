plugins {
    id("java-library")
    id("java-test-fixtures")
}

dependencies {
    api(libs.jsr305)
    api(libs.joml)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter.api)
}
