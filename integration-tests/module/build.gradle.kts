plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("com.google.devtools.ksp")
}

dependencies {
    add("kspCommonMainMetadata", project(":kotlin-inject-compiler:ksp"))
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
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
kotlin.targets.configureEach {
    kotlin.configureKsp(name)
}
tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer.configureKsp(targetName: String) {
    runCatching {
        dependencies {
            add("ksp${targetName.capitalize()}", project(":kotlin-inject-compiler:ksp"))
        }

         sourceSets.configureEach {
           kotlin.srcDir("build/generated/ksp/$targetName/$name/kotlin")
         }
    }
}
