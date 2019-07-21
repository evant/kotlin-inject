# kotlin-inject

A compile-time dependency injection library for kotlin.

```kotlin
@Module abstract class AppModule {
    abstract val repo: Repository

    protected fun jsonParser() = JsonParser()

    protected val RealHttp.provides: Http get() = this
}

interface Http
@Inject class RealHttp
@Inject class Api(private val http: Http, private val jsonParser: JsonParser)
@Inject class Repository(private val api: Api)
```
```kotlin
val appModule = AppModule::class.create()
val repo = appModule.repo
```

## Usage

Let's go through the above example line-by line and see what it's doing.

```kotlin
@Module abstract class Module {
```
The building block of kotlin-inject is a module which you declare with an `@Module` annotation on an abstract class. An
implementation of this module will be generated for you.

```kotlin
    abstract val repo: Repository
```
In you module you can declare abstract read-only properties or functions to return an instance of a given type. This is
where the magic happens. kotlin-inject will figure out how to construct that type for you in it's generated
implementation. How does it know how to do this? There's a few ways:

```kotlin
    protected fun jsonParser() = JsonParser()
```
For external dependencies, you can declare a function or read-only property in the module to create an instance for a 
certain type. kotlin-inject will use the return type to provide this instance where it is requested.

```kotlin
    protected val RealHttp.provides: Http get() = this
```
You can declare arguments to a providing function/property to help you construct your instance. Here we are taking in an
instance of `RealHttp` and providing it for the interface `Http`. You can see a little sugar with this as the receiver 
type for an extension function/property counts as an argument. Another way to write this would be:
`fun provides(http: RealHttp): Http = http`.

```kotlin
@Inject class RealHttp
@Inject class Api(private val http: Http, private val jsonParser: JsonParser)
@Inject class Repository(private val api: Api)
```
For your own dependencies you can simply annotate the class with `@Inject`. This will use the primary constructor to
create an instance, no other configuration required!

```kotlin
val appModule = AppModule::class.create()
val repo = appModule.repo
```

Finally, you can create an instance of your module with the generated `.create()` extension function.

## Features

### Module Arguments

If you need to pass any instances into your module you can declare them as constructor args. You can then pass them into
the generated create function.

```kotlin
@Module abstract class MyModule(protected val foo: Foo)
```

```kotlin
MyModule::class.create(Foo())
```

If the argument is another module, it's dependencies will also be available to the child module. This allows you to 
compose them into a graph.

```kotlin
@Module abstract class ParentModule {
    protected fun provideFoo(): Foo = ...
}

@Module abstract class ChildModule(val parent: ParentModule) {
    abstract val foo: Foo
}
```

```kotlin
val parent = ParentModule::class.create()
val child = ChildModule::class.create(parent)
```

### Type Alias Support

If you have multiple instances of the same type you want to differentiate, you can use type aliases. They will be 
treated as separate types for the purposes of injection.

```kotlin
typealias Dep1 = Dep
typealias Dep2 = Dep

@Modlue abstract class MyModlue {
    fun dep1(): Dep1 = Dep("one")
    fun dep2(): Dep2 = Dep("two")

    protected fun provides(dep1: Dep1, dep2: Dep1) = Thing(dep1, dep2)
}

@Inject class InjectedClass(dep1: Dep1, dep2: Dep2)
```

### Scopes

By default kotlin-inject will create a new instance of a dependency each place it's injected. If you want to re-use an
instance you can scope it to a module. The instance will live as long as that module does.

First create your scope annotation.
```kotlin
@Scope
annotation class MyScope
```

Then annotate your module with that scope annotation.

```kotlin
@MyScope @Module abstract class MyModule()
```

Finally, annotate your provides and `@Inject` classes with that scope.

```kotlin
@MyScope @Module abstract class MyModule {
    @MyScope
    protected fun provideFoo() = ...
}

@MyScope @Inject class Bar()
```

### Multi-bindings

You can collect multiple bindings into a `Map` or `Set` by using the `@IntoMap` and `@IntoSet` annotations respectively.

For a set, return the type you want to put into a set, then you can inject or provide a `Set<MyType>`.

```kotlin
@Module abstract class MyModule {
    abstract val allFoos: Set<Foo>

    @IntoSet protected fun provideFoo1(): Foo = Foo("1")
    @IntoSet protected fun provideFoo2(): Foo = Foo("2")
}
```

For a map, return a `Pair<Key, Value>`.

```kotlin
@Module abstract class MyModule {
    abstract val fooMap: Map<String, Foo>
    
    @IntoMap protected fun provideFoo1(): Pair<String, Foo> = "1" to Foo("1")
    @IntoMap protected fun provideFoo2(): Pair<String, Foo> = "2" to Foo("2")
}
```

### Function Support

Sometimes you want to delay the creation of a dependency of provide additional params manually. You can do this by 
injecting a function that returns the dependency instead of the dependency directly.

The simplest case is you take no args, this gives you a function that can create the dep.

```kotlin
@Inject class Foo

@Inject class MyClass(fooCreator: () -> Foo) {
    init {
        val foo = fooCreator()
    }
}
```

If you define args, you can use these to assist the creation of the dependency. These are passed in as the _last_ 
arguments to the dependency.

```kotlin
@Inject class Foo(bar: Bar, arg1: String, arg2: String)

@Inject class MyClass(fooCreator: (arg1: String, arg2: String) -> Foo) {
    init {
        val foo = fooCreator("1", "2")
    }
}
```

### Lazy

Similarly, you can inject a `Lazy<MyType>` to construct and re-use and instance lazily.

```kotlin
@Inject class Foo

@Inject class MyClass(lazyFoo: Lazy<Foo>) {
    val foo by lazyFoo
}
```