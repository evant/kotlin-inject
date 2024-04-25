# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- `@Scope` annotations now take arguments into account. This means for example, if you have
  ```kotlin
  @Scope
  annotation class NamedScope(val value: String)
   ```
  then the scope: `@NamedScope("one")` and `@NamedScope("two")` would be treated as distinct. Previously they were
  treated as the same scope.
- Legacy implicit assisted injection (not using the `@Assisted` annotation) is now an error.

### Removed
- The KAPT backend is removed, please migrate to KSP if you haven't already.

## [0.6.3] 2023-09-02

### Fixed
- Fixed scoped assisted injection enforcement. It was incorrectly using the component scope instead of the scope of the
  assisted class or provides method.

## [0.6.2] 2023-08-28

### Changed

- Updated kotlin to 1.9.0
- If a dependency's scope is not found on the component providing it, a better error message is given.
- Adding a `@Provides` annotation on an abstract `fun` or `val` will now warn that it has no effect.
- When overriding a method the parent is checked to see if it has a `@Provides` annotation. This makes the example in
  the README actually work:
  ```kotlin
  @NetworkScope abstract class NetworkComponent {
    @NetworkScope @Provides abstract fun api(): Api
  }  
  @Component abstract class RealNetworkComponent : NetworkComponent() {
    // This is now treated as a @Provides even if not annotated directly
    override fun api(): Api = RealApi()
  }
  ```

### Fixed

- Typealiases are treated as separate types in multibinding. This is consistent with other
  uses of typealiases.

  For example:
  ```kotlin
  typealias MyString = String
  
  @Component abstract class MyComponent {
    abstract val stringItems: Set<String>
    abstract val myStringItems: Set<MyString>

    @Provides @IntoSet fun stringValue1(): String = "string"

    @Provides @IntoSet fun stringValue2(): MyString = "myString"
  }
  ```
  `stringItems` will contain `{"string"}` and `myStringItems` will contain `{"myString"}`.
- Lambda types now work in set multibindings.
  ```kotlin
  @Component abstract class MyComponent {
    abstract val lambdaSet: Set<() -> String>

    @Provides @IntoSet fun lambda1(): () -> String = { "one" }

    @Provides @IntoSet fun lambda2(): () -> String = { "two" }
  }
  ```
- Assisted injection no longer works with scopes or cycles. These two cases would over-cache the instance, ignoring the
  assisted arguments. They now throw an error instead.
  ```kotlin
   // now throws cycle error when providing
  @Inject class AssistedCycle(val factory: (Int) -> AssistedCycle, @Assisted val arg: Int)
  // now throws error when providing
  @MyScope @Inject class AssistedScoped(@Assisted val arg: Int)
  ```
- Fixed edge case where accessing a parent scoped dependency from a lazy cycle generated invalid code.

## [0.6.1] 2023-02-11

### Fixed

- Fixed code generation issues with assisted injection.

## [0.6.0] 2022-12-21

### Changed

- Added the ability to explicitly mark assisted injection parameters with an `@Assisted` annotation. Not providing them
  will currently warn which will become an error in the future. This allows better documentation on which params are
  injected and which ones are provided by the caller. It also allows more flexibility for parameter ordering, you can
  put the assisted params at the start instead of at the end if you so choose.

  For example, if you have:

  ```kotlin
  @Inject class AssistedClass(arg1: One , arg2: Two, arg3: Three)
  @Inject Usage(createAssistedClass: (Two, Three) -> AssistedClass)
  ```

  you should update it to:
  ```kotlin
  @Inject class AssistedClass(arg1: One , @Assisted arg2: Two, @Assisted arg3: Three)
  ```

### Fixed

- `@Inject` annotations being ignored if used through a typealias, ex:
  ```kotlin
  typealias MyInject = Inject
  @MyInject class MyClassToInject
  ```

## [0.5.1] 2022-07-05

### Fixed

- Fixed dependency resolution issue with native artifacts

## [0.5.0] 2022-07-02

### Changed

- The kapt backend is now deprecated and will be removed in a future release. Please migrate to ksp instead.
- Introduced some stricter checks to catch issues with certain graph setups. This may cause graphs that compiled before
  to no longer compile. Specifically:

  ```kotlin
  @MyScope @Component abstract class ParentComponent
  @MyScope @Component abstract class ChildComponent(@Component val parent: ParentComponent)
  ```
  will fail with:

  ```
  Cannot apply scope: @MyScope ChildComponent
  as scope @MyScope is already applied to parent ParentComponent
  ```
  as it's ambiguous what the lifetime of the given scope should be. And:

  ```kotlin
  @Component abstract class ParentComponent {
    @Provides fun foo(bar: Bar): Foo = ...
  } 
  @Component abstract class ChildComponent(@Component val parent: ParentComponent) {
    abstract val foo: Foo
    @Provides fun bar(): Bar = ...
  }
  ```
  will fail with:

  ```
  Cannot find an @Inject constructor or provider for: Bar 
  ```
  In other words a parent component can no longer depend on a dependency provided by a child component. Not only does
  this lead to confusing graphs, but it can lead to memory leaks if the parent component lives longer than the child and
  ends holding on to that child dependency.

### Added

- You can now use function and `Lazy` types in `Set`. This allows you to lazily construct its entries without having to
  change the type on your `@IntoSet` methods.
  ```kotlin
  @Component abstract class MyComponent {
    val funSet: Set<() -> Foo>
    val lazySet: Set<Lazy<Foo>> 
    
    @Provides @IntoSet fun foo1(): Foo = ...
    @Provides @IntoSet fun foo2(): Foo = ...
  }
  ```

### Fixed

- Fixed issue with name collisions when generating arguments. This could cause invalid code to be generated as the inner
  arg would shadow the outer arg that was attempted to be used.
- Improved the error message for scoped provides in unscoped component.
- Fixed printing of an inner type to include the outer class. i.e. You will now get `Parent.Child` in error messages
  instead of just `Child` which can make it easier to find the location of the error.

## [0.4.1] 2022-01-01

### Changed

- Improved generated code formatting.
- Removed explicit retention annotation to get rid of kotlin js warnings.

### Fixed

- Fixes conflicting declarations when scoped `@Provides` functions returned the same type with different generic args.
- Fixes default parameter handling with lambda or lazy values.
- Fixes to kotlin native implementation that should make it more usable across threads.
  Note: the new memory model limitation is still present, but you can use https://github.com/touchlab/Stately to wrap
  the access when using the legacy memory model.

## [0.4.0] 2021-11-12

### Changed

- Updated kotlin to 1.5.31
- Updated ksp to 1.5.31-1.0.1
- Several improvements to code generation which often means less code is generated.

### Added

- Multiple rounds handling: This includes support for using types generated by other ksp processors. As a side effect
  there is better error reporting for unresolved types.
- Support for multiplatform/native. Check out
  the [sample project](https://github.com/evant/kotlin-inject-samples/tree/main/multiplatform/echo).

  Note: components are thread-safe, however you will run into issues actually using them from other threads unless you
  enable
  the [new memory model](https://blog.jetbrains.com/kotlin/2021/08/try-the-new-kotlin-native-memory-manager-development-preview/).
- Added support for default args when injecting. If the type is present in the graph, it'll be injected, otherwise the
  default will be used.

  ```kotlin
  @Inject class MyClass(val dep: Dep = Dep("default"))

  @Component abstract ComponentWithDep {
      abstract val myClass: MyClass
      @Provides fun dep(): Dep = Dep("injected")
  }
  @Component abstract ComponentWithoutDep {
      abstract val myClass: MyClass
  }

  ComponentWithDep::class.create().myClass.dep // Dep("injected")
  ComponentWithoutDep::class.create().myClass.dep // Dep("default")
  ```

## [0.3.7-RC] 2021-10-29

### Changed

- Updated kotlin to 1.6.0-RC
- Updated ksp to 1.6.0-RC-1.0.1-RC
- Several improvements to code generation which often means less code is generated.

### Added

- Multiple rounds handling: This includes support for using types generated by other ksp processors. As a side effect
  there is better error reporting for unresolved types.
- Support for multiplatform/native. Check out
  the [sample project](https://github.com/evant/kotlin-inject-samples/tree/main/multiplatform/echo).

## [0.3.6] - 2021-07-16

### Changed

- Updated kotlin to 1.5.20
- Experimental kotlin js support

### Fixed

- Fix generated code for @Inject functions with a receiver ex: `@Inject fun Foo.bar() = ...`
- Fix not using the typealias for function return types

## [0.3.5] - 2021-06-02

### Changed

- Updated kotlin to 1.5.10
- Updated ksp to beta01

## [0.3.4] - 2021-05-30

### Fixed

- Fix metata parsing issue with kapt on kotlin 1.5.0
- Fix declaring function injection in another module in ksp

### Changed

- Updated kotlin to 1.5.0
- Updated ksp to alpha10

## [0.3.3] - 2021-04-20

### Added

- **Allow cycles when there is delayed construction**

  You can now break cycles by using `Lazy` or a function. For example,
  ```kotlin
  @Inject class Foo(bar: Bar)
  @Inject class Bar(foo: Foo)
  ```
  will fail with a cycle error, but you can fix it by doing
  ```kotlin
  @Inject class Foo(bar: Lazy<Bar>)
  @Inject class Bar(foo: Foo)
  ```
  or
  ```kotlin
  @Inject class Foo(bar: () -> Bar)
  @Inject class Bar(foo: Foo)
  ```
  This uses `lateinit` under the hood. You will get a runtime exception if you try to use the dependency before
  construction completes.
- Added option `me.tatarka.inject.dumpGraph` to print the dependency graph while building. This can be useful for
  debugging issues.
- **Allow type-alias usage with `@IntoMap`.**

  You can now do
  ```kotlin
  typealias Entry = Pair<String, MyValue>
  
  @Component {
      @Provides @IntoMap
      fun entry1(): Entry = "1" to MyValue(1)
      @Provides @IntoMap
      fun entry2(): Entry = "2" to MyValue(2)
  }
  ```

### Changed

- Code-gen optimization to reduce code size
- ksp performance improvements
- **Made handling of nullable and platform types consistent on the different backends.**

  It is now an error to return a platform type from a `@Provides` methods, you must declare the return type explicitly.

### Fixed

- Fix using `@Qualifier` on scoped dependencies
- Fix declaring components as an inner class
- Fix annotating java class constructors with `@Inject`
- Fix `@Inject` on a companion object

## [0.3.2] - 2021-04-05

### Changed

- Updated ksp to [1.4.30-1.0.0-alpha05](https://github.com/google/ksp/releases/tag/1.4.30-1.0.0-alpha05)

## [0.3.1] - 2021-02-25

### Changed

- Updated ksp to [1.4.30-1.0.0-alpha03](https://github.com/google/ksp/releases/tag/1.4.30-1.0.0-alpha03)
- Minimum supported kotlin version is now 1.4.30

## [0.3.0] - 2021-01-14

### Changed

- **Updated ksp
  to [1.4.20-dev-experimental-20210111](https://github.com/google/ksp/releases/tag/1.4.20-dev-experimental-20210111)**

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
