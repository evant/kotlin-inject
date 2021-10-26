plugins {
    kotlin("jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":kotlin-inject-compiler:core"))
    implementation(project(":kotlin-inject-compiler:kapt"))
    implementation(project(":kotlin-inject-compiler:ksp"))

    implementation(libs.bundles.kotlin.compile.testing)

    testImplementation(libs.bundles.kotlin.test.junit)
    testImplementation(libs.assertk)
    testImplementation(libs.burst.junit4)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}