plugins {
    id("java-library")
}

dependencies {
    api(libs.jsr305)
    api(libs.joml)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
