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

### Qualifiers
If you have multiple instances of the same type you want to differentiate, you can use a qualifier annotation. The 
built-in one is `@Named`. Annotate your providing functions and usage sites with this qualifier and it will 
differentiate between them.

```kotlin
@Modlue MyModlue {
    fun dep1(): @Named("dep1") Dep = Dep("one")
    fun dep2(): @Named("dep2") Dep = Dep("two")

    fun provides(dep1: @Named("dep1") Dep, dep2: @Named("dep2") Dep) = Thing(dep1, dep2)
}

@Inject class InjectedClass(dep1: @Named("dep1") Dep, dep2: @Named("dep2") Dep)
```
