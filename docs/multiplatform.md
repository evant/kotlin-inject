# Multiplatform

This document covers using kotlin-inject in a multiplatform project. The example we'll follow is
setting up an android and iOS project but the pattern should be the same for other platforms. You can check out
[greeter](https://github.com/evant/kotlin-inject-samples/tree/main/multiplatform/greeter) for a full sample.

## Gradle

See the [README](https://github.com/evant/kotlin-inject/blob/main/README.md) for instructions on how to add kotlin-inject to your project.

Replace the kotlin-jvm plugin with the kotlin-multiplatform plugin
```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.google.devtools.ksp")
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

    // add your project's other targets...
}
```

Add the runtime dependency in commonMain
```kotlin
sourceSets {
    commonMain {
        dependencies {
            implementation("me.tatarka.inject:kotlin-inject-runtime:$kotlinInjectVersion")
        }
    }
}
```

Add the compiler dependency in root level `dependencies` block for each of the platforms you're targeting
```kotlin
dependencies {
    add("kspIosX64", "me.tatarka.inject:kotlin-inject-compiler-ksp:$kotlinInjectVersion")
    add("kspIosArm64", "me.tatarka.inject:kotlin-inject-compiler-ksp:$kotlinInjectVersion")
    add("kspIosSimulatorArm64", "me.tatarka.inject:kotlin-inject-compiler-ksp:$kotlinInjectVersion")
    add("kspAndroid", "me.tatarka.inject:kotlin-inject-compiler-ksp:$kotlinInjectVersion")
}
```

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
