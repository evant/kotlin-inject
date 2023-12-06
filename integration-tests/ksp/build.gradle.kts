import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    id("com.google.devtools.ksp")
}

dependencies {
    kspCommonMainMetadata(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
    kspJvmTest(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
    kspJsTest(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
    kspWasmJsTest(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))

    for (configuration in nativeKspTestConfigurations) {
        add(configuration, project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
    }
}

kotlin {
    jvm {
        withJava()
    }

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
    val test by sourceSets.existing {
        java.srcDir("../common-jvm/src/test/java")
    }
}

ksp {
    arg("me.tatarka.inject.enableJavaxAnnotations", "true")
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
tasks.withType<KotlinCompile>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
