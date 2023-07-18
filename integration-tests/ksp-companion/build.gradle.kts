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

java {
    targetCompatibility = JavaVersion.VERSION_17
}