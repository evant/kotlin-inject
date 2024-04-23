# Multiplatform

This document covers using kotlin-inject in a multiplatform project. The example we'll follow is
setting up an android and iOS project but the pattern should be the same for other platforms. You can check out
[greeter](https://github.com/evant/kotlin-inject-samples/tree/main/multiplatform/greeter) for a full sample.

## Download

Using [ksp](https://github.com/google/ksp)

`settings.gradle`

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

`build.gradle`

Add Plugin
```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "1.9.23"
    id("com.android.library")
    id("com.google.devtools.ksp") version "1.9.23-1.0.20"
}
```

Set up targets
```kotlin
kotlin {
  androidTarget()

  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach {
    it.binaries.framework {
      baseName = "shared"
    }
  }
  ...
```

Add Runtime dependency in commonMain
```kotlin
sourceSets {
    commonMain {
        dependencies {
            implementation("me.tatarka.inject:kotlin-inject-runtime:0.6.3")
        }
    }
    ...
}
```

Add the compiler dependency in root level `dependencies` block for each of the platforms you're targeting
```kotlin
dependencies {
    add("kspIosX64", "me.tatarka.inject:kotlin-inject-compiler-ksp:0.6.3")
    add("kspIosArm64", "me.tatarka.inject:kotlin-inject-compiler-ksp:0.6.3")
    add("kspIosSimulatorArm64", "me.tatarka.inject:kotlin-inject-compiler-ksp:0.6.3")
    add("kspAndroid", "me.tatarka.inject:kotlin-inject-compiler-ksp:0.6.3")
}
```

Note: You'll have to add the compiler for each of the chipset separately like done above.
KSP will eventually have better multiplatform support and we'll be able to simply
have `ksp(libs.kotlinInject.compiler)` :crossed_fingers:

## Usage

Usage is similar to that mentioned [here](../README.md#usage). Only difference is when you
call `::class.create()` on your components.

The generated code only exists in the different sourcesets, thus they can't be referenced from
the `commonMain`, you can call `::class.create()` from one the target sourceSets. For
iOS/TVOS/MacOS/WatchOS this means calling from the chipset specific sourceset (not from
the `iosMain` etc.). You can use `expect/actual` to set this up, albeit with some code duplication.

```kotlin
// src/iosMain/com/example/ApplicationComponent.kt
package com.example

import me.tatarka.inject.annotations.Component

@Component
abstract class ApplicationComponent : PlatformComponent {
    abstract val greeter: CommonGreeter

    companion object
}

expect fun ApplicationComponent.Companion.create(): ApplicationComponent
```

```kotlin
// src/iosX64Main/com/example/ApplicationComponent.iosX64.kt
package com.example

actual fun ApplicationComponent.Companion.create(): ApplicationComponent =
    ApplicationComponent::class.create()
```

```kotlin
// src/iosArm64Main/com/example/ApplicationComponent.iosArm64.kt
package com.example

actual fun ApplicationComponent.Companion.create(): ApplicationComponent =
    ApplicationComponent::class.create()
```

```kotlin
// src/iosSimulatorArm64Main/com/example/ApplicationComponent.iosSimulatorArm64.kt
package com.example

actual fun ApplicationComponent.Companion.create(): ApplicationComponent =
    ApplicationComponent::class.create()
```
