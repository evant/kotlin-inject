import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    kotlin("jvm")
    kotlin("kapt")
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
    testImplementation("javax.inject:javax.inject:1")

    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.24")
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

jmh {
    // https://github.com/melix/jmh-gradle-plugin/issues/159
    duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE
}

val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }