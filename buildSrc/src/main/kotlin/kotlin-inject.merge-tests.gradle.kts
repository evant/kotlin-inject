import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

val tests = tasks.withType<KotlinTest>()
val testsApple = tests.filter { it.targetName?.contains(Regex("macos|ios|tvos|watchos")) ?: false }

val testReport by rootProject.tasks.getting(TestReport::class) {
    reportOn(tests)
}

val testReportApple by rootProject.tasks.getting(TestReport::class) {
    reportOn(testsApple)
}

val copyTestResults by rootProject.tasks.getting(Copy::class) {
    tests.forEach { test ->
        // Have to explicitly wire up task dependency due to https://github.com/gradle/gradle/issues/17120
        dependsOn(test)
        from(test.reports.junitXml.outputLocation.get().asFile) {
            include("**/*.xml")
            rename("(.*).xml", "$1[${test.targetName}].xml")
        }
    }
}

val copyTestResultsApple by rootProject.tasks.getting(Copy::class) {
    testsApple.forEach { test ->
        // Have to explicitly wire up task dependency due to https://github.com/gradle/gradle/issues/17120
        dependsOn(test)
        from(test.reports.junitXml.outputLocation.get().asFile) {
            include("**/*.xml")
            rename("(.*).xml", "$1[${test.targetName}].xml")
        }
    }
}

val check by tasks.getting {
    finalizedBy(testReport, copyTestResults)
}

val checkApple by rootProject.tasks.getting {
    finalizedBy(testReportApple, copyTestResultsApple)
}