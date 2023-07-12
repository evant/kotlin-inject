import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    id("kotlin-inject.jvm")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    kotlin("kapt")
}

dependencies {
    kapt(project(":kotlin-inject-compiler:kotlin-inject-compiler-kapt"))
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":integration-tests:module"))

    kaptTest(project(":kotlin-inject-compiler:kotlin-inject-compiler-kapt"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.javax.inject)
    testImplementation(libs.assertk)
}

sourceSets {
    val test by getting {
        kotlin.srcDir("../common-companion/src/test/kotlin")
    }
}

kapt {
    arguments {
        arg("me.tatarka.inject.generateCompanionExtensions", "true")
    }
}

val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }

kotlin.compilerOptions.jvmTarget = JvmTarget.JVM_17

java {
    targetCompatibility = JavaVersion.VERSION_17
}
