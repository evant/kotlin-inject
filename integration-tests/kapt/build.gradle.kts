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
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines)
    testImplementation(libs.javax.inject)
    testImplementation(libs.assertk)
}

sourceSets {
    test {
        kotlin.srcDir("../common/src/test/kotlin")
        kotlin.srcDir("../common-jvm/src/test/kotlin")
        java.srcDir("../common-jvm/src/test/java")
    }
}

kapt {
    arguments {
        arg("me.tatarka.inject.enableJavaxAnnotations", "true")
        arg("me.tatarka.inject.useClassReferenceForScopeAccess", "true")
    }
}
