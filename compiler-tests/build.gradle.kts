plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":kotlin-inject-compiler:core"))
    implementation(project(":kotlin-inject-compiler:kapt"))
    implementation(project(":kotlin-inject-compiler:ksp"))

    implementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.4.0")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    implementation("org.jetbrains.kotlin:kotlin-annotation-processing-embeddable:${Versions.kotlin}")
    implementation("com.google.devtools.ksp:symbol-processing:${Versions.ksp}")
    implementation("com.google.devtools.ksp:symbol-processing-api:${Versions.ksp}")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.24")
    testImplementation("com.squareup.burst:burst-junit4:1.2.0")
}