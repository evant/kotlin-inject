import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    id("com.google.devtools.ksp")
}

dependencies {
    kspCommonMainMetadata(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
    kspJvmTest(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
}

kotlin {
    jvm {
        withJava()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("../common/src/main/kotlin")
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":kotlin-inject-runtime"))
                implementation(project(":integration-tests:module"))
            }
        }
        commonTest {
            kotlin.srcDir("../common-test/src/test/kotlin")
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

tasks.withType(KotlinCompilationTask::class.java).configureEach {
    if(name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
