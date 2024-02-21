# Multiplatform

This document covers using kotlin-inject in a multiplatform project.

## Download

Using [ksp](https://github.com/google/ksp)

`settings.gradle`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

`build.gradle`

Add Plugin
```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "1.9.21"
    id("com.android.library")
    id("com.google.devtools.ksp") version "1.9.21-1.0.16"
}
```

Add Runtime dependency in commonMain
```kotlin
sourceSets {
    androidMain.dependencies {
        //...
    }
    commonMain.dependencies {
        implementation("me.tatarka.inject:kotlin-inject-runtime:0.6.3")
    }
}
```
If you're targetting iOS, you need to add each of the chipset as separate sourcesets to include generated files.
Same goes to any other LLVM (MacOS, TVOS etc.) you're targetting.
```kotlin
val iosX64Main by getting {
    dependencies {
        dependsOn(iosMain.get())
        kotlin.srcDir("build/generated/ksp/iosX64/iosX64Main/kotlin")
    }
}
val iosArm64Main by getting {
    dependencies {
        dependsOn(iosMain.get())
        kotlin.srcDir("build/generated/ksp/iosArm64/iosArm64Main/kotlin")
    }
}
val iosSimulatorArm64Main by getting {
    dependencies {
        dependsOn(iosMain.get())
        kotlin.srcDir("build/generated/ksp/iosSimulatorArm64/iosSimulatorArm64Main/kotlin")
    }
}
```

Add the compiler dependency in root level `dependencies` block for each of the platforms you're targetting
```kotlin
dependencies {
    add("kspIosX64", "me.tatarka.inject:kotlin-inject-compiler-ksp:0.6.3")
    add("kspIosArm64", "me.tatarka.inject:kotlin-inject-compiler-ksp:0.6.3")
    add("kspIosSimulatorArm64", "me.tatarka.inject:kotlin-inject-compiler-ksp:0.6.3")
    add("kspAndroid", "me.tatarka.inject:kotlin-inject-compiler-ksp:0.6.3")
    add("kspJs", "me.tatarka.inject:kotlin-inject-compiler-ksp:0.6.3")
}
```
Note: If you're targetting iOS, you'll have to add the compiler for each of the chipset separately like done abobe. Same goes true for all other LLVM platforms (MacOS, TVOS etc.)
KSP will eventually have better multiplatform support and we'll be able to simply have `ksp(libs.kotlinInject.compiler)` :crossed_fingers:

## Usage

Usage is similar to that mentioned [here](../README.md#usage). Only difference is when you call `::class.create()` on your components.

The generated code only exists in the different sourcesets, thus they can't be referenced from the `commonMain`, you can call `::class.create()` from one the target sourceSets. For iOS/TVOS/MacOS/WatchOS this means calling from the chipset specific sourceset (not from the `iosMain` etc.).