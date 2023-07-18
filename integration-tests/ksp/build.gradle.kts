import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

    for (configuration in nativeKspTestConfigurations) {
        add(configuration, project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
    }
}

kotlin {
    jvm {
        withJava()
        compilations.configureEach {
            compilerOptions.options.jvmTarget = JvmTarget.JVM_17
        }
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
    sourceSets {
        val test by getting {
            java.srcDir("../common-jvm/src/test/java")
        }
    }
}

ksp {
    arg("me.tatarka.inject.enableJavaxAnnotations", "true")
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}
