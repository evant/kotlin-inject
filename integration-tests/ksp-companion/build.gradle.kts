plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    id("com.google.devtools.ksp")
}

dependencies {
    addAllKspTargets(kotlin, project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir("../common-companion/src/main/kotlin")
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":kotlin-inject-runtime-kmp"))
                implementation(project(":integration-tests:module"))
            }
        }
        commonTest {
            kotlin.srcDir("../common-companion-test/src/test/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.assertk)
            }
        }
        nativeMain {
            kotlin.srcDir("../common-native/src/main/kotlin")
        }
        nativeTest {
            kotlin.srcDir("../common-native/src/test/kotlin")
        }
    }
}

ksp {
    arg("me.tatarka.inject.generateCompanionExtensions", "true")
}
