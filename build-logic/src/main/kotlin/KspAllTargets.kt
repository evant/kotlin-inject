import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun DependencyHandler.addAllKspTargets(
    kotlin: KotlinMultiplatformExtension,
    dependencyNotation: Any,
) {
    kotlin.targets.configureEach {
        if (targetName != "metadata") {
            add("ksp${targetName.uppercaseFirstChar()}", dependencyNotation)
        }
    }
}
