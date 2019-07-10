package me.tatarka.inject.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import me.tatarka.inject.annotations.Scope
import me.tatarka.inject.annotations.Singleton
import org.junit.Before
import org.junit.Test

@Scope
annotation class CustomScope

var barConstructorCount = 0
var customScopeBarConstructorCount = 0

@Singleton @Inject class ScopeBar {
    init {
        barConstructorCount++
    }
}

@CustomScope @Inject class CustomScopeBar {
    init {
        customScopeBarConstructorCount++
    }
}

@Singleton @Module abstract class SingletonConstructorModule {
    abstract val scopeBar: ScopeBar

    companion object
}

@CustomScope @Module abstract class CustomScopeConstructorModule {
    abstract val bar: CustomScopeBar

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
    @Before
    fun setup() {
        barConstructorCount = 0
        customScopeBarConstructorCount = 0
    }

    @Test
    fun generates_a_module_where_a_singleton_constructor_is_only_called_once() {
        val module = SingletonConstructorModule.create()
        module.scopeBar
        module.scopeBar

        assertThat(barConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_module_where_a_custom_scope_constructor_is_only_called_once() {
        val module = CustomScopeConstructorModule.create()
        module.bar
        module.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
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