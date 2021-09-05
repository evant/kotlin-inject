import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    kotlin("jvm")
    kotlin("kapt")
    alias(libs.plugins.jmh)
}

dependencies {
    kapt(project(":kotlin-inject-compiler:kapt"))
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":integration-tests:module"))

    kaptTest(project(":kotlin-inject-compiler:kapt"))
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.javax.inject)

    testImplementation(libs.assertk)
}

sourceSets {
    val test by getting {
        kotlin.srcDir("../common/src/test/kotlin")
        kotlin.srcDir("../common-jvm/src/test/kotlin")
        java.srcDir("../common-jvm/src/test/java")
    }
}

kapt {
    arguments {
        arg("me.tatarka.inject.enableJavaxAnnotations", "true")
    }
}

val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }