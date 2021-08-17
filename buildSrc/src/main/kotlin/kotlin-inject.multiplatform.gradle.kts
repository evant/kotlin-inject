plugins {
    kotlin("multiplatform")
}

val nativeTargets = arrayOf(
    "linux",
    "macos",
    "ios"
)

kotlin {
    jvm()
    js(BOTH) {
        browser()
        nodejs()
        // suppress noisy 'Reflection is not supported in JavaScript target'
        for (compilation in arrayOf("main", "test")) {
            compilations.getByName(compilation).kotlinOptions {
                suppressWarnings = true
            }
        }
    }
    linuxX64("linux")
    macosX64("macos")
    ios()

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val nativeMain = create("nativeMain") {
            dependsOn(commonMain)
        }
        val nativeTest = create("nativeTest") {
            dependsOn(commonTest)
        }
        for (sourceSet in nativeTargets) {
            getByName("${sourceSet}Main") {
                dependsOn(nativeMain)
            }
            getByName("${sourceSet}Test") {
                dependsOn(nativeTest)
            }
        }
    }
}

val nativeTest by tasks.registering {
    dependsOn(*nativeTargets.map { "${it}Test" }.toTypedArray())
}

// Wait in case the test task gets created later
afterEvaluate {
    tasks.maybeCreate("test").apply {
        // TODO: this can become 'allTests' after a couple of issues are resolved:
        // 1. targets that cannot be run on the current platform are not skipped https://github.com/google/ksp/issues/570
        // 2. ksp does not support js ir backend https://github.com/JetBrains/kotlin/pull/4264
        dependsOn("jvmTest", "jsLegacyTest", "linuxTest")
    }
}
