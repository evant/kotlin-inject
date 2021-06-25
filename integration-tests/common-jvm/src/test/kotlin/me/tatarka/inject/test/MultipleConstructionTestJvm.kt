package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.typeOf
import kotlin.test.Test

@OptIn(ExperimentalStdlibApi::class)
class MultipleConstructionTestJvm {

    @Test
    fun generates_a_component_that_uses_a_getter_for_common_construction() {
        val component = CommonGetterComponent::class.create()
        val privateProperties = component::class.declaredMemberProperties.filter {
            it.visibility == KVisibility.PRIVATE
        }

        assertThat(privateProperties).apply {
            hasSize(2)
            any { item ->
                item.prop(KProperty1<*, *>::returnType).isEqualTo(typeOf<Bar>())
            }
            any { item ->
                item.prop(KProperty1<*, *>::returnType).isEqualTo(typeOf<Inner.Bar>())
            }
        }
    }

    @Test
    fun generates_a_component_that_reuses_the_declared_getter_for_common_construction() {
        val component = ReuseExistingPropertyComponent::class.create()
        val privateProperties = component::class.declaredMemberProperties.filter {
            it.visibility == KVisibility.PRIVATE
        }

        assertThat(privateProperties).isEmpty()
    }

    @Test
    fun generates_a_component_that_provides_a_scoped_dependency_in_multiple_places() {
        val component = MultipleScopedConstructionComponent::class.create()
        val privateProperties = component::class.declaredMemberProperties.filter {
            it.visibility == KVisibility.PRIVATE
        }

        assertThat(privateProperties).apply {
            hasSize(1)
            index(0).all {
                prop(KProperty1<*, *>::returnType).isEqualTo(typeOf<IFoo>())
            }
        }
    }
}