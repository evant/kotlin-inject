import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask

plugins {
    kotlin("multiplatform")
}

val nativeTargets = arrayOf(
    "linuxX64",
    "macosX64", "macosArm64",
    "iosArm64", "iosX64", "iosSimulatorArm64",
    "tvosArm64", "tvosX64", "tvosSimulatorArm64",
    "watchosArm32", "watchosArm64", "watchosX64", "watchosSimulatorArm64",
)

kotlin {
    jvmToolchain(17)

    js {
        browser()
        nodejs()
    }

    for (target in nativeTargets) {
       targets.add(presets.getByName(target).createTarget(target))
    }

    jvm {
        compilations.configureEach {
            compilerOptions.options.jvmTarget = JvmTarget.JVM_1_8
        }
    }

    sourceSets {
        val nativeMain by creating {
            dependsOn(commonMain.get())
        }
        val nativeTest by creating {
            dependsOn(commonTest.get())
        }
        for (sourceSet in nativeTargets) {
            getByName("${sourceSet}Main") {
                dependsOn(nativeMain)
            }
            getByName("${sourceSet}Test") {
                dependsOn(nativeTest)
            }
        }
        // Ensure xml test reports are generated
        val jvmTest by tasks.getting(Test::class) {
            reports.junitXml.required.set(true)
        }
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
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