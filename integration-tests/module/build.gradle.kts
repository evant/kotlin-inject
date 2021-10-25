import com.google.devtools.ksp.gradle.KspTaskJS
import com.google.devtools.ksp.gradle.KspTaskJvm
import com.google.devtools.ksp.gradle.KspTaskMetadata
import com.google.devtools.ksp.gradle.KspTaskNative
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("com.google.devtools.ksp")
}

dependencies {
    ksp(project(":kotlin-inject-compiler:ksp"))
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kotlin-inject-runtime"))
            }
        }
    }
}

// Generate common code with ksp instead of per-platform, hopefully this won't be needed in the future.
// https://github.com/google/ksp/issues/567
kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/commonMain/kotlin")
}
tasks.withType<KspTaskJS>().configureEach {
    enabled = false
}
tasks.withType<KspTaskJvm>().configureEach {
    enabled = false
}
tasks.withType<KspTaskNative>().configureEach {
    enabled = false
}
tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.withType<KspTaskMetadata>())
}