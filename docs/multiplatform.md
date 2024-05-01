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

### Adding the compiler dependencies

When configuring KSP for use with kotlin-inject, you can choose to generate the code into:

1. The common source set ([see below](#ksp-common-source-set-configuration) for the implementation of `configureCommonMainKsp`)
2. Each individual KMP target source set

Add the compiler dependencies in a top level `dependencies` block

> [!TIP]
> There is a good chance that the API for adding KSP compiler dependencies will change in the future. Follow [ksp#1021](https://github.com/google/ksp/pull/1021) for updates.

```kotlin
dependencies {
    // 1. Configure code generation into the common source set
    kspCommonMainMetadata(libs.kotlinInject)

    // 2. Configure code generation into each KMP target source set
    kspAndroid("me.tatarka.inject:kotlin-inject-compiler-ksp:$kotlinInjectVersion")
    kspIosX64("me.tatarka.inject:kotlin-inject-compiler-ksp:$kotlinInjectVersion")
    kspIosArm64("me.tatarka.inject:kotlin-inject-compiler-ksp:$kotlinInjectVersion")
    kspIosSimulatorArm64("me.tatarka.inject:kotlin-inject-compiler-ksp:$kotlinInjectVersion")
}
```

> [!NOTE]  
> Code generation can be configured for both the common source set and all target source sets, but that will likely lead to [Redeclaration errors for @Component when ksp is used for common and target source sets](https://github.com/evant/kotlin-inject/issues/194).

If a `Component` is declared in the common source set, then KSP would typically be configured to generate code in the common source set. This means that the generated code will be accessible from the common source set, and any source set that depends on it.

However, certain scenarios instead require generating code for all target source sets (e.g. if a `Component` or any of its ancestors are an `expect` type, and the `actual` implementation provides platform specific bindings).

In those cases, while the `Component` might be declared in the common source set, it is not possible to create it, because the `create` functions are declared in each target source set.

> [!NOTE]  
> Pre Kotlin 2.0 it is possible to reference the `create` function from the common source set because code from a target source set was visible to the common source set.
> However this can lead to very subtle bugs, and starting from Kotlin 2.0 common source sets can no longer see code from target source sets.

In order to create the `Component`, it is necessary to declare an `expect fun`, and have the `actual fun` call the target's `create` function.

```kotlin
// common source set
@Component
abstract class MyKmpComponent

expect fun createKmp(): MyKmpComponent

// each target source set
actual fun createKmp(): MyKmpComponent = MyKmpComponent::class.create()
```

Creating an `actual fun` for each platform can be tedious, so kotlin-inject provides a `TargetComponentAccessor` annotation.

```kotlin  
@Component
abstract class MyKmpComponent

@TargetComponentAccessor
expect fun createKmp(): MyKmpComponent
```

kotlin-inject's processor will generate an `actual fun` in each target's source set that calls through to the `create` function for `MyKmpComponent`. The generated code looks like this:

```kotlin
actual fun createKmp(): MyKmpComponent = MyKmpComponent::class.create()
```

The annotated `expect fun`'s parameters will be passed to the `Component`'s `create` function, so it should contain exactly the parameters that the `create` function expects.

Because these are regular `expect/actual` functions, an extension function can be used, which can be helpful for namespacing:

```kotlin
@TargetComponentAccessor
expect fun MyKmpComponent.Companion.createKmp(): MyKmpComponent
```

in which case the generated code would look like:

```kotlin
@TargetComponentAccessor
actual fun MyKmpComponent.Companion.createKmp(): MyKmpComponent = MyKmpComponent::class.create()
```

#### Shared source sets

`TargetComponentAccessor` can be used for all shared source sets, not just `commonMain`.

For example, you won't be able to access the `create` functions in each of the ios target source sets (`iosArm64`, `iosSimulatorArm64`, etc...) from an `ios` shared source set.

You can define a `TargetComponentAccessor` which will allow you to create the `Component` in the `ios` shared source set

```kotlin
// common source set
@Component
abstract class MyKmpComponent

// android source set
val myKmpComponent: MyKmpComponent = MyKmpComponent::class.create()

// ios source set
// the actual createKmp functions will only be generated in the targets that depend on the ios source set
@TargetComponentAccessor
expect fun MyKmpComponent.Companion.createKmp(): MyKmpComponent

val myKmpComponent: MyKmpComponent = MyKmpComponent.createKmp()
```

#### Usage

Usage is the same as mentioned [here](https://github.com/evant/kotlin-inject/blob/main/README.md#usage).

The only difference is for projects that generate code into each KMP target source set, in which case you would use the `TargetComponentAccessor` to create the `Component` when necessary.

#### KSP Common Source Set Configuration

```kotlin
kotlin {
    // add your project's targets here like in the snippet above

    configureCommonMainKsp()
}

@OptIn(ExternalVariantApi::class)
fun KotlinMultiplatformExtension.configureCommonMainKsp() {
  sourceSets.named("commonMain").configure {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
  }

  project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
    if(name != "kspCommonMainKotlinMetadata") {
      dependsOn("kspCommonMainKotlinMetadata")
    }
  }
}
```
