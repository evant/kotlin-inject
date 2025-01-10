import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.assign
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("multiplatform")
}

val libs = the<LibrariesForLibs>()

kotlin {
    js {
        browser()
        nodejs()
    }

    linuxArm64()
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    watchosX64()
    watchosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    jvm()

    // Ensure xml test reports are generated
    tasks.named("jvmTest", Test::class).configure {
        reports.junitXml.required.set(true)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(libs.versions.jvmTarget.map(String::toInt))
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget = libs.versions.jvmTarget.map(JvmTarget::fromTarget)
}

// Run only the native tests
val nativeTest by tasks.registering {
    kotlin.targets.all {
        if (this is KotlinNativeTargetWithTests<*>) {
            dependsOn("${name}Test")
        }
    }
}

// Don't run npm install scripts, protects against
// https://blog.jetbrains.com/kotlin/2021/10/important-ua-parser-js-exploit-and-kotlin-js/ etc.
tasks.withType<KotlinNpmInstallTask> {
    args += "--ignore-scripts"
}

rootProject.tasks.withType<KotlinNpmInstallTask>().configureEach {
    args.add("--ignore-engines")
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
