import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("com.google.devtools.ksp")
}

dependencies {
    kspCommonMainMetadata(project(":kotlin-inject-compiler:kotlin-inject-compiler-ksp"))
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":kotlin-inject-runtime-kmp"))
            }
        }
    }
}

tasks.withType(KotlinCompilationTask::class.java).configureEach {
    if(name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
