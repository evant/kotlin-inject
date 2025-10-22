import com.google.devtools.ksp.KspExperimental

plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    id("com.google.devtools.ksp")
}

dependencies {
    addAllKspTargets(kotlin, project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
    kspJvmTest(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir("../common/src/main/kotlin")
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":kotlin-inject-runtime-kmp"))
                implementation(project(":integration-tests:module"))
            }
        }
        commonTest {
            kotlin.srcDir("../common-test/src/test/kotlin")
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.coroutinesTest)
                implementation(libs.assertk)
            }
        }
        nativeMain {
            kotlin.srcDir("../common-native/src/main/kotlin")
        }
        nativeTest {
            kotlin.srcDir("../common-native/src/test/kotlin")
        }
        jvmMain {
            kotlin.srcDir("../common-jvm/src/main/kotlin")
            dependencies {
                api(libs.javax.inject)
            }
        }
        jvmTest {
            kotlin.srcDir("../common-jvm/src/test/kotlin")
            dependencies {
                implementation(libs.kotlin.reflect)
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.wasm.browser)
            }
        }
    }

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.allWarningsAsErrors = true
            }
        }
    }
}

java {
    sourceSets.named("jvmTest").configure {
        java.srcDir("../common-jvm/src/test/java")
    }
}

ksp {
    arg("me.tatarka.inject.enableJavaxAnnotations", "true")
    @OptIn(KspExperimental::class)
    useKsp2 = false
}
