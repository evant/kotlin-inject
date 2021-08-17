import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    id("kotlin-inject.multiplatform")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kotlin-inject-runtime"))
                implementation(project(":integration-tests:module"))
            }
        }
        val commonTest by getting {
            kotlin.srcDir("../common-companion/src/test/kotlin")
            dependencies {
                implementation(kotlin("test"))
                configurations["ksp"].dependencies.add(project(":kotlin-inject-compiler:ksp"))
                implementation("com.willowtreeapps.assertk:assertk:0.24")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-reflect")
                implementation("javax.inject:javax.inject:1")
            }
        }
    }
}

ksp {
    arg("me.tatarka.inject.generateCompanionExtensions", "true")
}

val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }