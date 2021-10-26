plugins {
    kotlin("jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
}

dependencies {
    implementation(project(":kotlin-inject-compiler:core"))
    implementation(project(":kotlin-inject-compiler:kapt"))
    implementation(project(":kotlin-inject-compiler:ksp"))

    implementation(libs.bundles.kotlin.compile.testing)

    testImplementation(libs.bundles.kotlin.test.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.assertk)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    test {
        useJUnitPlatform()
    }
}
