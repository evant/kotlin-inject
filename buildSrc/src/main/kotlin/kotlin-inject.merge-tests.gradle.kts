import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.testing.TestReport
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

val tests = tasks.withType<KotlinTest>()

val testReport by rootProject.tasks.getting(TestReport::class) {
    reportOn(tests)
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

tests.all {
    finalizedBy(testReport, copyTestResults)
}