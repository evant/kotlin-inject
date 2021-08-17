plugins {
    `kotlin-dsl`
}
repositories {
    gradlePluginPortal()
}
dependencies {
    implementation("de.marcphilipp.gradle:nexus-publish-plugin:0.4.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
}