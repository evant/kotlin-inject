# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [0.3.2] - 2021-04-05

### Changed
- Updated ksp to [1.4.30-1.0.0-alpha05](https://github.com/google/ksp/releases/tag/1.4.30-1.0.0-alpha05)

## [0.3.1] - 2021-02-25

### Changed

- Updated ksp to [1.4.30-1.0.0-alpha03](https://github.com/google/ksp/releases/tag/1.4.30-1.0.0-alpha03)
- Minimum supported kotlin version is now 1.4.30

## [0.3.0] - 2021-01-14

### Changed
- **Updated ksp to [1.4.20-dev-experimental-20210111](https://github.com/google/ksp/releases/tag/1.4.20-dev-experimental-20210111)**

  Key changes:
  - You no longer have to define `resolutionStrategy` in your `settings.gradle`.
  - The plugin id has changed from `symbol-processing` to `com.google.devtools.ksp`.
- Minimum supported kotlin version is now 1.4.20

### Added
- **Support injecting suspend functions**

  You can now define `suspend` component and provides methods
  ```kotlin
  @Component abstract class MyComponent {
    abstract val foo: suspend () -> IFoo

    val providesFoo: suspend () -> IFoo
        @Provides get() = { Foo() }
  }
  ```
- **Support default args in component constructors**

  If you define any default args, you will get an overload `create` function that provides default values. Due to
  processor limitations, you only get a single overload (i.e. you cannot pass defaults from some args but not other).
  This can be useful for more conveniently defining parent components.
  ```kotlin
  @Component abstract class MyComponent(@Component val parent: ParentComponent = ParentComponent()) {
    ...
  }
  val component = MyComponent::class.create()
  ```
- **Support annotating constructors with `@Inject`**

  Sometimes you don't want to use the primary constructor to construct an object. You can now annotate a more specific
  constructor instead.
  ```kotlin
  class MyClass {
    @Inject constructor(arg: String) // use this one for injection
    constructor(arg: Int)
  }
  ```
- **Support injecting objects**

  While you can use an object directly, it may be useful to inject it so that you can switch it to an instance at a
  later point without updating the consuming code.
  ```kotlin
  @Inject object Foo { ... }
  @Inject MyClass(dep: Foo) { ... }
  ```

### Fixed
- Respect component's class visibility
- Fix generating incorrect code for fun providers

## [0.2.0] - 2020-09-28

### Changed
- [Migrate](https://github.com/google/ksp/blob/master/old-ksp-release.md) ksp 
- Improve kapt error messaging
- Build performance improvements
  
### Added
- Allow annotating interfaces with `@Component`
- Support `javax.inject.Qualifer`

### Fixed
- Fixed companion generation (`me.tatarka.inject.generateCompanionExtensions=true`) for ksp
- Throw error when parent component is missing val instead of generating incorrect code.

## [0.1.0] - 2020-09-17
- Initial Release