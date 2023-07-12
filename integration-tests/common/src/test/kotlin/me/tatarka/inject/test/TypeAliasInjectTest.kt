package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import kotlin.test.Test

typealias InjectTypeAlias = Inject

class FooWithPrimaryConstructorInjectTypeAlias @InjectTypeAlias constructor()

class FooWithSecondaryConstructorInjectTypeAlias(private val prop: String) {

    @InjectTypeAlias
    constructor() : this("prop")
}

@InjectTypeAlias
class FooWithInjectTypeAlias

typealias fooWithPrimaryCreator = () -> FooWithPrimaryConstructorInjectTypeAlias

@InjectTypeAlias
fun fooWithPrimaryCreator(foo: FooWithPrimaryConstructorInjectTypeAlias) = foo

typealias TypeAliasToInjectTypeAlias = InjectTypeAlias

typealias TypeAliasToTypeAliasToInjectTypeAlias = TypeAliasToInjectTypeAlias

@TypeAliasToTypeAliasToInjectTypeAlias
class FooWithTypeAliasToTypeAliasToTypeAlias

@Component
abstract class InjectTypeAliasComponent {

    abstract val primaryConstructorWithInjectTypeAlias: FooWithPrimaryConstructorInjectTypeAlias

    abstract val secondaryConstructorWithInjectTypeAlias: FooWithSecondaryConstructorInjectTypeAlias

    abstract val classWithInjectTypeAlias: FooWithInjectTypeAlias

    abstract val functionWithInjectTypeAlias: fooWithPrimaryCreator

    abstract val fooWithTypeAliasToTypeAliasToTypeAlias: FooWithTypeAliasToTypeAliasToTypeAlias
}

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
