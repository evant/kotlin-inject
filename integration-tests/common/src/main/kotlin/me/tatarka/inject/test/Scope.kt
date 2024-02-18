package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope
import me.tatarka.inject.test.different.DifferentPackageFoo
import me.tatarka.inject.test.different.DifferentPackageScopedComponent
import me.tatarka.inject.test.module.ExternalChildComponent
import me.tatarka.inject.test.module.ExternalScope
import me.tatarka.inject.test.module.IExternalFoo
import me.tatarka.inject.test.module.ScopedExternalFoo

@CustomScope
@Component
abstract class CustomScopeConstructorComponent {
    abstract val bar: CustomScopeBar
}

@CustomScope
@Component
abstract class CustomScopeProvidesComponent {

    abstract val foo: IFoo

    val Foo.binds: IFoo
        @Provides @CustomScope get() = this
}

@Component
abstract class ParentScopedComponent(@Component val parent: CustomScopeConstructorComponent) {
    abstract val bar: CustomScopeBar
}

@Component
abstract class ParentParentScopedComponent(@Component val parent: ParentScopedComponent) {
    abstract val bar: CustomScopeBar
}

@Component
abstract class NonCustomScopeParentComponent

@CustomScope
@Component
abstract class CustomScopeChildComponent(@Component val parent: NonCustomScopeParentComponent) {
    abstract val bar: CustomScopeBar
}

@CustomScope
@Inject
class ScopedFoo(val bar: ScopedBar)

@CustomScope
@Inject
class ScopedBar

@CustomScope
@Component
abstract class DependentCustomScopeComponent {
    abstract val foo: ScopedFoo

    abstract val bar: ScopedBar
}

@Component
abstract class DifferentPackageChildComponent(@Component val parent: DifferentPackageScopedComponent) {
    abstract val foo: DifferentPackageFoo
}

@Component
@ExternalScope
abstract class ExternalScopedComponent {
    abstract val foo: ScopedExternalFoo
}

@Inject
class UseBar1(val bar: ScopedBar)

@Inject
class UseBar2(val bar: ScopedBar)

@CustomScope
@Component
abstract class MultipleUseScopedComponent {
    abstract val bar1: UseBar1

    abstract val bar2: UseBar2
}

@CustomScope
@Component
abstract class TypeAccessComponent {
    abstract val string: String
    abstract val `class`: IFoo
    abstract val parameterized: GenericFoo<String>
    abstract val typeAlias1: NamedFoo1
    abstract val typeAlias2: NamedFoo2
    abstract val lambda: (String) -> String
    abstract val suspendLambda: suspend (String) -> String
    abstract val receiverLambda: Int.() -> String
    abstract val suspendReceiverLambda: suspend Int.() -> String

    @Provides
    @CustomScope
    fun provideString(): String = "string"

    @Provides
    @CustomScope
    fun provideClass(): IFoo = Foo()

    @Provides
    @CustomScope
    fun provideParameterized(): GenericFoo<String> = GenericFoo("generic")

    @Provides
    @CustomScope
    fun provideTypeAlias1(): NamedFoo1 = NamedFoo("one")

    @Provides
    @CustomScope
    fun provideTypeAlias2(): NamedFoo2 = NamedFoo("two")

    @Provides
    @CustomScope
    fun provideLambda(): (String) -> String = { "$it lambda" }

    @Provides
    @CustomScope
    fun provideSuspendLambda(): suspend (String) -> String = { "$it suspend lambda" }

    @Provides
    @CustomScope
    fun provideReceiverLambda(): Int.() -> String = { "$this receiver lambda" }

    @Provides
    @CustomScope
    fun provideSuspendReceiverLambda(): suspend Int.() -> String = { "$this suspend receiver lambda" }
}

@Component
abstract class NestedExternalScopedComponent(@Component val parent: ExternalChildComponent) {
    abstract val foo: IExternalFoo
}

class Parameterized<E, S>

@CustomScope
@Inject
class ParameterizedFoo(
    private val p1: Parameterized<List<Int>, List<String>>,
    private val p2: Parameterized<List<String>, List<String>>,
    private val bar: ParameterizedBar
)

@CustomScope
@Inject
class ParameterizedBar(
    private val p1: Parameterized<List<Int>, List<String>>,
    private val p2: Parameterized<List<String>, List<String>>
)

@CustomScope
@Component
abstract class MultipleSameTypedScopedProvidesComponent {
    abstract val foo: ParameterizedFoo

    @CustomScope
    @Provides
    protected fun parameterized1() = Parameterized<List<Int>, List<String>>()

    @CustomScope
    @Provides
    protected fun parameterized2() = Parameterized<List<String>, List<String>>()
}

@Scope annotation class ChildScope

@CustomScope
@Component
abstract class ParentChildScopesParentComponent {
    val foo: IFoo
        @Provides @CustomScope get() = Foo()
}

@ChildScope
@Component
abstract class ParentChildScopesChildComponent(@Component val parent: ParentChildScopesParentComponent) {
    abstract val bar: BarImpl

    @Provides @ChildScope
    fun barImpl(foo: IFoo): BarImpl = BarImpl(foo)
}

@CustomScope
interface DuplicateScopeInterface {
    val iFoo: IFoo
        @Provides @CustomScope get() = Foo()
}

@CustomScope
@Component
abstract class DuplicateScopeComponent : DuplicateScopeInterface {
    abstract val foo: IFoo
}
