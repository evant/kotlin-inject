import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

val tests = tasks.withType<AbstractTestTask>()
val testsApple = tests.withType<KotlinNativeTest>().matching { it.targetName?.contains(Regex("macos|ios|tvos|watchos")) ?: false }

val testReport = rootProject.tasks.named("testReport", TestReport::class) {
    testResults.from(tests.map { it.binaryResultsDirectory })
}

val testReportApple = rootProject.tasks.named("testReportApple", TestReport::class) {
    testResults.from(testsApple.map { it.binaryResultsDirectory })
}

val copyTestResults = rootProject.tasks.named("copyTestResults", Copy::class) {
    tests.forEach { test ->
        dependsOn(test)
        from(test.reports.junitXml.outputLocation.get().asFile) {
            include("**/*.xml")
            if (test is KotlinJvmTest) {
                rename("(.*).xml", "$1[${test.targetName}].xml")
            }
        }
        into(rootProject.buildDir.resolve("test-results"))
    }
}

val copyTestResultsApple = rootProject.tasks.named("copyTestResultsApple", Copy::class) {
    testsApple.forEach { test ->
        dependsOn(test)
        from(test.reports.junitXml.outputLocation.get().asFile) {
            include("**/*.xml")
            rename("(.*).xml", "$1[${test.targetName}].xml")
        }
        into(rootProject.buildDir.resolve("test-results"))
    }
}