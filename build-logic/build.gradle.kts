import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    dependencies {
        with(libs.kotlin.gradle.get()) {
            classpath("$group:$name:$embeddedKotlinVersion")
        }
    }
}

plugins {
    `kotlin-dsl`
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget = libs.versions.jvmTarget.map(JvmTarget::fromTarget)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(libs.versions.jvmTarget.map(String::toInt))
}

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.detekt.gradle)
    // hack to access version catalogue https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
