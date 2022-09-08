package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import org.junit.Test

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

@Component
abstract class InjectTypeAliasComponent {

    abstract val primaryConstructorWithInjectTypeAlias: FooWithPrimaryConstructorInjectTypeAlias

    abstract val secondaryConstructorWithInjectTypeAlias: FooWithSecondaryConstructorInjectTypeAlias

    abstract val classWithInjectTypeAlias: FooWithInjectTypeAlias

    abstract val functionWithInjectTypeAlias: fooWithPrimaryCreator
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

        assertThat(component.functionWithInjectTypeAlias).isNotNull()
    }
}
