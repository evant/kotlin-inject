plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    id("com.google.devtools.ksp")
}

dependencies {
    ksp(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":kotlin-inject-runtime"))
                implementation(project(":integration-tests:module"))
            }
        }
        commonTest {
            kotlin.srcDir("../common-companion/src/test/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.assertk)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlin.reflect)
                implementation(libs.javax.inject)
            }
        }
    }
}

ksp {
    arg("me.tatarka.inject.generateCompanionExtensions", "true")
}

// Fix gradle warning of execution optimizations have been disabled for task
// https://github.com/google/ksp/issues/975
tasks {
    metadataJar.configure {
        dependsOn("kspCommonMainKotlinMetadata")
    }
    jsLegacyJar.configure {
        dependsOn("kspKotlinJsIr")
    }
    jsIrJar.configure {
        dependsOn("kspKotlinJsLegacy")
    }
}

