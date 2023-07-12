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

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.detekt.gradle)
    // hack to access version catalogue https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
