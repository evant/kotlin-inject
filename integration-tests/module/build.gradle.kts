
plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("com.google.devtools.ksp")
}

dependencies {
    add("kspMetadata", project(":kotlin-inject-compiler:ksp"))
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
tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().all {
    if (name != "kspKotlinMetadata") {
        dependsOn("kspKotlinMetadata")
    }
}