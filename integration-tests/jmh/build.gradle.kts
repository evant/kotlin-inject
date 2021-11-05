plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    alias(libs.plugins.jmh)
}

dependencies {
    implementation(project(":kotlin-inject-runtime"))

    jmhImplementation(kotlin("stdlib"))
}

jmh {
    // https://github.com/melix/jmh-gradle-plugin/issues/159
    duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE)
}

