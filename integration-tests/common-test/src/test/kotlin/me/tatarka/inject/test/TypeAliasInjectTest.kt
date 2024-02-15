package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import kotlin.test.Test

class TypeAliasInjectTest {

    @Test
    fun can_generate_for_inject_typealias_on_primary_constructor() {
        val component = InjectTypeAliasComponent::class.create()

        assertThat(component.primaryConstructorWithInjectTypeAlias).isNotNull()
    }

    @Test
    fun can_generate_for_inject_typealias_on_secondary_constructor() {
        val component = InjectTypeAliasComponent::class.create()

        assertThat(component.secondaryConstructorWithInjectTypeAlias).isNotNull()
    }

    @Test
    fun can_generate_for_inject_typealias_on_class() {
        val component = InjectTypeAliasComponent::class.create()

        assertThat(component.classWithInjectTypeAlias).isNotNull()
    }

    @Test
    fun can_generate_for_inject_typealias_on_function() {
        val component = InjectTypeAliasComponent::class.create()

        assertThat(
            component.functionWithInjectTypeAlias,
            name = "component.functionWithInjectTypeAlias"
        ).isNotNull()
    }

    @Test
    fun can_generate_for_type_alias_to_type_alias_to_inject_type_alias_on_class() {
        val component = InjectTypeAliasComponent::class.create()

        assertThat(component.fooWithTypeAliasToTypeAliasToTypeAlias).isNotNull()
    }
}
