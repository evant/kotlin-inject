package me.tatarka.inject.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Module
import me.tatarka.inject.annotations.Scope
import org.junit.Before
import org.junit.Test

@Scope
annotation class CustomScope

var customScopeBarConstructorCount = 0

@CustomScope @Inject class CustomScopeBar {
    init {
        customScopeBarConstructorCount++
    }
}

@CustomScope @Module abstract class CustomScopeConstructorModule {
    abstract val bar: CustomScopeBar
}

@Module abstract class ParentScopedModule(val parent: CustomScopeConstructorModule) {
    abstract val bar: CustomScopeBar
}

@Module abstract class ParentParentScopedModule(val parent: ParentScopedModule) {
    abstract val bar: CustomScopeBar
}

class ScopeTest {
    @Before
    fun setup() {
        customScopeBarConstructorCount = 0
    }

    @Test
    fun generates_a_module_where_a_custom_scope_constructor_is_only_called_once() {
        val module = CustomScopeConstructorModule::class.create()
        module.bar
        module.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_module_where_a_singleton_constructor_is_instantiated_in_the_parent_module() {
        val parent = CustomScopeConstructorModule::class.create()
        val module1 = ParentScopedModule::class.create(parent)
        module1.bar
        module1.bar
        val module2 = ParentScopedModule::class.create(parent)
        module1.bar
        module2.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_module_where_a_singleton_constructor_is_instantiated_in_the_parent2_module() {
        val parent = CustomScopeConstructorModule::class.create()
        val child = ParentScopedModule::class.create(parent)
        val module1 = ParentParentScopedModule::class.create(child)
        module1.bar
        module1.bar
        val module2 = ParentParentScopedModule::class.create(child)
        module1.bar
        module2.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
    }
}