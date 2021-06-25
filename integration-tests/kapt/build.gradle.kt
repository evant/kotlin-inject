import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    kotlin("jvm")
    kotllin("kapt")
    id("me.champeau.gradle.jmh") version "0.5.1"
}

dependencies {
    kapt(project(":kotlin-inject-compiler:kapt"))
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":integration-tests:module"))

    kaptTest(project(":kotlin-inject-compiler:kapt"))
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    testImplementation("javax.inject:javax.inject:1")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.24")
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