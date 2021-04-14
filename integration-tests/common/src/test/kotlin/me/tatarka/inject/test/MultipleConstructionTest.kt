package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.typeOf
import kotlin.test.Test

class Inner {
    @Inject class Bar(val foo: Foo)
}

@Inject class Bar4(val bar: Inner.Bar)

@Inject class Bar5(val bar: Inner.Bar)

@Component
abstract class CommonGetterComponent {
    abstract val bar2: Bar2

    abstract val bar3: Bar3

    abstract val bar4: Bar4

    abstract val bar5: Bar5
}

@Component
abstract class ReuseExistingPropertyComponent {
    abstract val bar: Bar

    abstract val bar2: Bar2

    abstract val bar3: Bar3
}

@Component
abstract class MultipleConstructionComponent {
    abstract val foo: Foo

    abstract val bar: Bar

    abstract val bar2: Bar2

    abstract val set: Set<IFoo>

    val Foo.bind: IFoo
        @Provides @IntoSet get() = this

    val Bar.bind: IFoo
        @Provides @IntoSet get() = this

    val Bar2.bind: IFoo
        @Provides @IntoSet get() = this
}

@Component
@CustomScope
abstract class MultipleScopedConstructionComponent {

    abstract val iBar: IBar

    abstract val bar: BarImpl

    abstract val bar2: BarImpl2

    @Provides
    fun providesIBar(foo: IFoo): IBar = object : IBar {}

    val Foo.bind: IFoo
        @Provides @CustomScope get() = this
}

@OptIn(ExperimentalStdlibApi::class)
class MultipleConstructionTest {

    @Test
    fun generates_a_component_that_uses_a_getter_for_common_construction() {
        val component = CommonGetterComponent::class.create()
        val privateProperties = component::class.declaredMemberProperties.filter {
            it.visibility == KVisibility.PRIVATE
        }

        assertThat(component.bar2).isNotNull()
        assertThat(component.bar3).isNotNull()
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

        assertThat(component.bar).isNotNull()
        assertThat(component.bar2).isNotNull()
        assertThat(component.bar3).isNotNull()
        assertThat(privateProperties).isEmpty()
    }

    @Test
    fun generates_a_component_that_provides_the_same_value_in_multiple_contexts() {
        val component = MultipleConstructionComponent::class.create()

        assertThat(component.foo).isNotNull()
        assertThat(component.bar).isNotNull()
        assertThat(component.set).containsOnly(
            Foo(),
            Bar(Foo()),
            Bar2(Bar(Foo())),
        )
    }

    @Test
    fun generates_a_component_that_provides_a_scoped_dependency_in_multiple_places() {
        val component = MultipleScopedConstructionComponent::class.create()
        val privateProperties = component::class.declaredMemberProperties.filter {
            it.visibility == KVisibility.PRIVATE
        }

        assertThat(component.iBar).isNotNull()
        assertThat(component.bar).isNotNull()
        assertThat(component.bar2).isNotNull()
        assertThat(privateProperties).apply {
            hasSize(1)
            index(0).all {
                prop(KProperty1<*, *>::returnType).isEqualTo(typeOf<IFoo>())
            }
        }
    }
}