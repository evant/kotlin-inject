import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    `java-library`
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    kapt(project(":kotlin-inject-compiler:kapt"))
    implementation(project(":kotlin-inject-runtime"))
    implementation(project(":integration-tests:module"))

    kaptTest(project(":kotlin-inject-compiler:kapt"))
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation(libs.javax.inject)

    testImplementation(libs.assertk)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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