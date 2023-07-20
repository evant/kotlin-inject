import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        browser()
        nodejs()
    }

    for (target in nativeTargets) {
       targets.add(presets.getByName(target).createTarget(target))
    }

    jvm()

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
        tasks.named("jvmTest", Test::class).configure {
            reports.junitXml.required.set(true)
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget = JvmTarget.JVM_11
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