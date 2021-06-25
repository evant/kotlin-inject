plugins {
    `kotlin-dsl`
}
repositories {
    mavenCentral()
    gradlePluginPortal()
}
dependencies {
    implementation("de.marcphilipp.gradle:nexus-publish-plugin:0.4.0")
}