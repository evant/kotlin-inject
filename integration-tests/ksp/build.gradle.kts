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
    jvm { withJava() }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":kotlin-inject-runtime"))
                implementation(project(":integration-tests:module"))
            }
        }
        commonTest {
            kotlin.srcDir("../common/src/test/kotlin")
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines)
                implementation(libs.assertk)
            }
        }
        jvmTest {
            kotlin.srcDir("../common-jvm/src/test/kotlin")
            dependencies {
                implementation(libs.kotlin.reflect)
                implementation(libs.javax.inject)
            }
        }
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                allWarningsAsErrors = true
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8

    sourceSets {
        val test by getting {
            java.srcDir("../common-jvm/src/test/java")
        }
    }
}

ksp {
    arg("me.tatarka.inject.enableJavaxAnnotations", "true")
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
