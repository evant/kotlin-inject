plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    id("com.google.devtools.ksp")
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
                configurations["ksp"].dependencies.add(project(":kotlin-inject-compiler:ksp"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.assertk)
            }
        }
        jvmTest {
            kotlin.srcDir("../common-jvm/src/test/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-reflect")
                implementation(libs.javax.inject)
            }
        }
    }
}

java {
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