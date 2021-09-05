import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    id("kotlin-inject.multiplatform")
    id("kotlin-inject.detekt")
    id("kotlin-inject.merge-tests")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":kotlin-inject-runtime"))
                implementation(project(":integration-tests:module"))
            }
        }
        commonTest {
            kotlin.srcDir("../common-companion/src/test/kotlin")
            dependencies {
                implementation(kotlin("test"))
                configurations["ksp"].dependencies.add(project(":kotlin-inject-compiler:ksp"))
                implementation(libs.assertk)
            }
        }
        jvmTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-reflect")
                implementation(libs.javax.inject)
            }
        }
    }
}

ksp {
    arg("me.tatarka.inject.generateCompanionExtensions", "true")
}

val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }