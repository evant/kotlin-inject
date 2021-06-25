import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {
    kspTest(project(":kotlin-inject-compiler:ksp"))
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":integration-tests:module"))

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("javax.inject:javax.inject:1")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.24")
}

sourceSets {
    val test by getting {
        kotlin.srcDir("../common-companion/src/test/kotlin")
    }
}

ksp {
    arg("me.tatarka.inject.generateCompanionExtensions", "true")
}

val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }