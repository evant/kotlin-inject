package me.tatarka.inject.benchmark

import me.tatarka.inject.internal.LazyMap
import me.tatarka.inject.internal.ScopedComponent
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope

// @Inject @MyScope
class Foo

// @MyScope @Component
abstract class ScopedAccessComponent {
    abstract val foo: Foo
}

class InjectLazyScopedAccessComponent : ScopedAccessComponent() {
    private val _foo by lazy {
        Foo()
    }

    override val foo: Foo
        get() = _foo
}

class InjectDynamicScopedAccessComponent : ScopedAccessComponent(), ScopedComponent {
    override val _scoped = LazyMap()

    override val foo: Foo = _scoped.get("Foo") { Foo() }
}

open class ScopedAccessBenchmark {

    @org.openjdk.jmh.annotations.State(Scope.Benchmark)
    open class State {
        val lazyComponent = InjectLazyScopedAccessComponent()
        val dynamicComponent = InjectDynamicScopedAccessComponent()
    }

    @Benchmark
    fun lazy_scoped_access_component(state: State): Foo {
        return state.lazyComponent.foo
    }

    @Benchmark
    fun dynamic_scoped_access_compoennt(state: State): Foo {
        return state.dynamicComponent.foo
    }
}