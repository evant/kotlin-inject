package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import org.junit.Test

@Component
abstract class InjectCtorComponent {

    abstract val primaryInject: FooWithPrimaryInject

    abstract val secondaryInject: FooWithSecondaryInject
}

class FooWithPrimaryInject @Inject constructor()

class FooWithSecondaryInject(private val prop: String) {

    @Inject constructor() : this("prop")
}

class InjectConstructorTest {

    @Test
    fun class_with_inject_annotated_primary_constructor_can_be_provided() {
        val component = InjectCtorComponent::class.create()

        assertThat(component.primaryInject).isNotNull()
    }

    @Test
    fun class_with_inject_annotated_secondary_constructor_can_be_provided() {
        val component = InjectCtorComponent::class.create()

        assertThat(component.secondaryInject).isNotNull()
    }
}