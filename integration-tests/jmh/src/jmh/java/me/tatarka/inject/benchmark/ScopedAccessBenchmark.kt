package me.tatarka.inject.benchmark

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

// @Inject @MyScope
class Foo {
    class Bar
}

// @MyScope @Component
abstract class ScopedAccessComponent {
    abstract val foo: Foo
    abstract val bar: Foo.Bar
}

class InjectLazyScopedAccessComponent : ScopedAccessComponent() {
    private val _foo by lazy {
        Foo()
    }

    private val _bar by lazy {
        Foo.Bar()
    }

    override val foo: Foo
        get() = _foo

    override val bar: Foo.Bar
        get() = _bar
}

interface ScopedComponentString {
    val _scoped: LazyMapString
}

interface ScopedComponentClass {
    val _scoped: LazyMapClass
}

class InjectDynamicScopedStringAccessComponent : ScopedAccessComponent(), ScopedComponentString {
    override val _scoped = LazyMapString()

    override val foo: Foo get() = _scoped.get("me/tatarka/inject/benchmark/Foo") { Foo() }

    override val bar: Foo.Bar get() = _scoped.get("me/tatarka/inject/benchmark/Foo\$Bar") { Foo.Bar() }
}

class InjectDynamicScopedClassAccessComponent : ScopedAccessComponent(), ScopedComponentClass {
    override val _scoped = LazyMapClass()

    override val foo: Foo get() = _scoped.get(Foo::class) { Foo() }

    override val bar: Foo.Bar get() = _scoped.get(Foo.Bar::class) { Foo.Bar() }
}


open class ScopedAccessBenchmark {

    @State(Scope.Benchmark)
    open class ComponentState {
        val lazyComponent = InjectLazyScopedAccessComponent()
        val dynamicStringComponent = InjectDynamicScopedStringAccessComponent()
        val dynamicClassComponent = InjectDynamicScopedClassAccessComponent()
    }

    @Benchmark
    fun lazy_scoped_access_component(state: ComponentState): Foo {
        return state.lazyComponent.foo
    }

    @Benchmark
    fun dynamic_scoped_string_access_compoennt(state: ComponentState): Foo {
        return state.dynamicStringComponent.foo
    }

    @Benchmark
    fun dynamic_scoped_class_access_compoennt(state: ComponentState): Foo {
        return state.dynamicClassComponent.foo
    }

}