package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import kotlin.test.Test

class TypeAliasTest {

    @Test
    fun can_generate_for_typealias_on_typealias_function() {
        val component = TypeAliasesComponent::class.create()

        assertThat(component.typeAliasFoo()).isNotNull()
    }

    @Test
    fun can_generate_for_typealias_on_typealias_to_typealias_function() {
        val component = TypeAliasesComponent::class.create()

        assertThat(component.typeAliasToTypeAliasFoo()).isNotNull()
    }

    @Test
    fun can_generate_for_typealias_on_typealias_to_typealias_to_typealias_function() {
        val component = TypeAliasesComponent::class.create()

        assertThat(component.typeAliasToTypeAliasToTypeAliasFoo()).isNotNull()
    }
}
