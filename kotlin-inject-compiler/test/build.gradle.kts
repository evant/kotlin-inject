plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
}

dependencies {
    implementation(project(":kotlin-inject-compiler:kotlin-inject-compiler-core"))
    implementation(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
    implementation(libs.bundles.kotlin.compile.testing)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.bundles.kotlin.test.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.launcher)
    testImplementation(libs.assertk)
}

tasks {
    test {
        useJUnitPlatform()
    }
}
