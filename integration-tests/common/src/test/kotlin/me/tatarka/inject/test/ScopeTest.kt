package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.test.different.DifferentPackageFoo
import me.tatarka.inject.test.different.DifferentPackageScopedComponent
import me.tatarka.inject.test.different.create
import me.tatarka.inject.test.module.ExternalScope
import me.tatarka.inject.test.module.ScopedExternalFoo
import kotlin.test.BeforeTest
import kotlin.test.Test

@CustomScope @Component abstract class CustomScopeConstructorComponent {
    abstract val bar: CustomScopeBar
}

@CustomScope @Component abstract class CustomScopeProvidesComponent {

    abstract val foo: IFoo

    val Foo.binds: IFoo
        @Provides @CustomScope get() = this
}

@Component abstract class ParentScopedComponent(@Component val parent: CustomScopeConstructorComponent) {
    abstract val bar: CustomScopeBar
}

@Component abstract class ParentParentScopedComponent(@Component val parent: ParentScopedComponent) {
    abstract val bar: CustomScopeBar
}

@Component abstract class NonCustomScopeParentComponent

@CustomScope @Component abstract class CustomScopeChildComponent(@Component val parent: NonCustomScopeParentComponent) {
    abstract val bar: CustomScopeBar
}

@CustomScope
@Inject
class ScopedFoo(val bar: ScopedBar)

@CustomScope
@Inject
class ScopedBar

@CustomScope @Component abstract class DependentCustomScopeComponent {
    abstract val foo: ScopedFoo

    abstract val bar: ScopedBar
}

@Component abstract class DifferentPackageChildComponent(@Component val parent: DifferentPackageScopedComponent) {
    abstract val foo: DifferentPackageFoo
}

@Component @ExternalScope abstract class ExternalScopedComponent {
    abstract val foo: ScopedExternalFoo
}

@Inject class UseBar1(val bar: ScopedBar)

@Inject class UseBar2(val bar: ScopedBar)

@CustomScope @Component abstract class MultipleUseScopedComponent {
    abstract val bar1: UseBar1

    abstract val bar2: UseBar2
}

@CustomScope @Component abstract class TypeAccessComponent {
    abstract val string: String
    abstract val `class` : IFoo
    abstract val parameterized: GenericFoo<String>
    abstract val typeAlias1: NamedFoo1
    abstract val typeAlias2: NamedFoo2
    abstract val lambda: (String) -> String
    abstract val suspendLambda: suspend (String) -> String
    abstract val receiverLambda: Int.() -> String
    abstract val suspendReceiverLambda: suspend Int.() -> String

    @Provides @CustomScope fun provideString(): String = "string"
    @Provides @CustomScope fun provideClass(): IFoo = Foo()
    @Provides @CustomScope fun provideParameterized(): GenericFoo<String> = GenericFoo("generic")
    @Provides @CustomScope fun provideTypeAlias1(): NamedFoo1 = NamedFoo("one")
    @Provides @CustomScope fun provideTypeAlias2(): NamedFoo2 = NamedFoo("two")
    @Provides @CustomScope fun provideLambda(): (String) -> String = { "$it lambda" }
    @Provides @CustomScope fun provideSuspendLambda(): suspend (String) -> String = { "$it suspend lambda" }
    @Provides @CustomScope fun provideReceiverLambda(): Int.() -> String = { "$this receiver lambda" }
    @Provides @CustomScope fun provideSuspendReceiverLambda(): suspend Int.() -> String = { "$this suspend receiver lambda" }
}

class ScopeTest {
    @BeforeTest
    fun setup() {
        fooConstructorCount = 0
        customScopeBarConstructorCount = 0
    }

    @Test
    fun generates_a_component_where_a_custom_scope_constructor_is_only_called_once() {
        val component = CustomScopeConstructorComponent::class.create()
        component.bar
        component.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_component_where_a_custom_scope_provides_is_only_called_once() {
        val component = CustomScopeProvidesComponent::class.create()
        component.foo
        component.foo

        assertThat(fooConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_component_where_a_singleton_constructor_is_instantiated_in_the_parent_component() {
        val parent = CustomScopeConstructorComponent::class.create()
        val component1 = ParentScopedComponent::class.create(parent)
        component1.bar
        component1.bar
        val component2 = ParentScopedComponent::class.create(parent)
        component1.bar
        component2.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_component_where_a_singleton_constructor_is_instantiated_in_the_parent2_component() {
        val parent = CustomScopeConstructorComponent::class.create()
        val child = ParentScopedComponent::class.create(parent)
        val component1 = ParentParentScopedComponent::class.create(child)
        component1.bar
        component1.bar
        val component2 = ParentParentScopedComponent::class.create(child)
        component1.bar
        component2.bar

        assertThat(customScopeBarConstructorCount).isEqualTo(1)
    }

    @Test
    fun generates_a_scoped_component_with_an_unscoped_parent_component() {
        val parent = NonCustomScopeParentComponent::class.create()
        val child = CustomScopeChildComponent::class.create(parent)

        assertThat(child.bar).isNotNull()
    }

    @Test
    fun generates_a_scoped_component_with_scoped_deps() {
        val component = DependentCustomScopeComponent::class.create()

        assertThat(component.bar).isSameAs(component.foo.bar)
    }

    @Test fun generates_a_component_with_a_parent_scoped_component_in_a_different_package() {
        val component = DifferentPackageChildComponent::class.create(DifferentPackageScopedComponent::class.create())

        assertThat(component.foo).isSameAs(component.foo)
    }

    @Test fun generates_a_component_that_reuses_the_same_scoped_dependency() {
        val component = MultipleUseScopedComponent::class.create()

        assertThat(component.bar1.bar).isSameAs(component.bar2.bar)
    }

    @Test fun generates_a_component_with_unique_keys_for_scoped_access() = runTest {
        val component = TypeAccessComponent::class.create()

        assertThat(component.string).isEqualTo("string")
        assertThat(component.`class`).isEqualTo(Foo())
        assertThat(component.parameterized).isEqualTo(GenericFoo("generic"))
        assertThat(component.typeAlias1).isEqualTo(NamedFoo("one"))
        assertThat(component.typeAlias2).isEqualTo(NamedFoo("two"))
        assertThat(component.lambda("test")).isEqualTo("test lambda")
        assertThat(component.suspendLambda("test")).isEqualTo("test suspend lambda")
        assertThat(component.receiverLambda(1)).isEqualTo("1 receiver lambda")
        assertThat(component.suspendReceiverLambda(1)).isEqualTo("1 suspend receiver lambda")
    }
}