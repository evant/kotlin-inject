plugins {
    id("kotlin-inject.multiplatform")
    id("com.google.devtools.ksp")
}

kotlin {
    jvm { withJava() }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kotlin-inject-runtime"))
                implementation(project(":integration-tests:module"))
            }
        }
        val commonTest by getting {
            kotlin.srcDir("../common/src/test/kotlin")
            dependencies {
                implementation(kotlin("test"))
                configurations["ksp"].dependencies.add(project(":kotlin-inject-compiler:ksp"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
                implementation("com.willowtreeapps.assertk:assertk:0.24")
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("../common-jvm/src/test/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-reflect")
                implementation("javax.inject:javax.inject:1")
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