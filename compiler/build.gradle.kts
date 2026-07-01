plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":annotations"))
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet.ksp)
}