package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

class ProvidesFoo(val bar: ProvidesBar? = null)
@Inject class ProvidesBar

@Component abstract class ProvidesFunctionComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun foo() = ProvidesFoo().also { providesCalled = true }
}

@Component abstract class ProvidesFunctionArgComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun foo(bar: ProvidesBar) = ProvidesFoo(bar).also { providesCalled = true }
}

@Component abstract class ProvidesValComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    val provideFoo
        get() = ProvidesFoo().also { providesCalled = true }
}

@Component abstract class ProvidesExtensionFunctionComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun ProvidesBar.provideFoo() = ProvidesFoo(this).also { providesCalled = true }
}

@Component abstract class ProvidesExtensionValComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    val ProvidesBar.provideFoo
        get() = ProvidesFoo(this).also { providesCalled = true }
}

@Component abstract class ProvidesValConstructorComponent(@Provides val provideFoo: ProvidesFoo) {
    abstract val foo: ProvidesFoo
}

class ProvidesTest {

    @Test fun generates_a_component_that_provides_a_dep_from_a_function() {
        val component = ProvidesFunctionComponent::class.create()

        component.foo
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_dep_from_a_function_with_arg() {
        val component = ProvidesFunctionArgComponent::class.create()
        val foo: ProvidesFoo = component.foo

        assertThat(foo.bar).isNotNull()
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_dep_from_a_val() {
        val component = ProvidesValComponent::class.create()

        component.foo
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_deb_from_an_extension_function() {
        val component = ProvidesExtensionFunctionComponent::class.create()

        assertThat(component.foo.bar).isNotNull()
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_deb_from_an_extension_val() {
        val component = ProvidesExtensionValComponent::class.create()

        assertThat(component.foo.bar).isNotNull()
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_dep_from_a_constructor_val() {
        val foo = ProvidesFoo()
        val component = ProvidesValConstructorComponent::class.create(foo)

        assertThat(component.foo).isSameAs(foo)
    }
}

