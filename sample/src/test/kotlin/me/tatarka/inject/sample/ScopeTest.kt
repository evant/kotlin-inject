package me.tatarka.inject.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.*
import org.junit.Test

class ScopeFoo

@Scope
annotation class CustomScope

@Module abstract class SingletonProvidesModule {
    var providesCalledCount = 0

    abstract val foo: ScopeFoo

    @Provides
    @Singleton
    fun foo() = ScopeFoo().also { providesCalledCount++ }

    companion object
}

var barConstructorCount = 0

@Singleton @Inject class ScopeBar {
    init {
        barConstructorCount++
    }
}

@Singleton @Module abstract class SingletonConstructorModule {
    abstract val scopeBar: ScopeBar

    companion object
}

@CustomScope @Module abstract class CustomScopeProvidesModule {
    var providesCalledCount = 0

    abstract val foo: ScopeFoo

    @Provides
    @CustomScope
    fun foo() = ScopeFoo().also { providesCalledCount++ }

    companion object
}

@Singleton @Module abstract class SingletonParentModule {
    companion object
}

@Module abstract class ParentScopedModule(val parent: SingletonParentModule) {
    abstract val bar: ScopeBar

    companion object
}

class ScopeTest {
    @Test
    fun generates_a_module_where_a_singleton_provides_is_only_called_once() {
        val module = SingletonProvidesModule.create()
        module.foo
        module.foo

        assertThat(module.providesCalledCount).isEqualTo(1)
    }

    @Test
    fun generates_a_module_where_a_singleton_constructor_is_only_called_once() {
        val module = SingletonConstructorModule.create()
        module.scopeBar
        module.scopeBar

        assertThat(barConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_module_where_a_custom_scope_provides_is_only_called_once() {
        val module = CustomScopeProvidesModule.create()
        module.foo
        module.foo

        assertThat(module.providesCalledCount).isEqualTo(1)
    }

    @Test
    fun generates_a_module_where_a_singleton_constructor_is_instantiated_in_the_parent_module() {
        val parent = SingletonParentModule.create()
        val module1 = ParentScopedModule.create(parent)
        module1.bar
        module1.bar
        val module2 = ParentScopedModule.create(parent)
        module1.bar
        module2.bar

        assertThat(barConstructorCount).isEqualTo(1)
    }
}