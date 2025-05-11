package me.tatarka.inject.test

import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.AssistedFactory
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides

@Inject
class AssistedBar(val foo: Foo, @Assisted val name: String)

@Component
abstract class AssistedComponent {
    abstract val barFactory: (name: String) -> AssistedBar
}

class SomethingProvided()

@Inject
class FactoryAssistedBar(
    @Assisted val num: Int,
    val foo: IFoo,
    @Assisted val name: String,
    val provided: SomethingProvided,
)

@AssistedFactory
interface AssistedBarFactory {
    fun build(num: Int, name: String): FactoryAssistedBar
}

@Inject
class SomethingDependantOnAssistedFactory(val factory: AssistedBarFactory)

@Component
abstract class AssistedFactoryComponent {
    abstract val barFactory: AssistedBarFactory
    abstract val somethingDependant: SomethingDependantOnAssistedFactory

    @Provides
    fun provideFoo(): Foo = Foo()

    val Foo.bind: IFoo
        @Provides get() = this

    @get:Provides
    val name: SomethingProvided = SomethingProvided() // makes sure names don't clash with assisted params
}

@Inject
data class AssistedBarArity2(@Assisted val arg1: String, @Assisted val arg2: String)

@Inject
data class AssistedBarArity3(@Assisted val arg1: String, @Assisted val arg2: String, @Assisted val arg3: String)

@Inject
data class AssistedBarArity4(
    @Assisted val arg1: String,
    @Assisted val arg2: String,
    @Assisted val arg3: String,
    @Assisted val arg4: String,
)

@Component
abstract class AssistedArityComponent {
    abstract val arity2: (arg1: String, arg2: String) -> AssistedBarArity2
    abstract val arity3: (arg1: String, arg2: String, arg3: String) -> AssistedBarArity3
    abstract val arity4: (arg1: String, arg2: String, arg3: String, arg4: String) -> AssistedBarArity4
}

@Inject
class NullableAssistedBar(@Assisted val foo: Foo?)

@Component
abstract class NullableAssistedComponent {
    abstract val barProvider: (Foo?) -> NullableAssistedBar
}

@Inject
data class Person(val house: House.Factory, @Assisted val name: String) {

    @Inject
    class Factory(
        val create: (String) -> Person,
    )
}

@Inject
data class House(@Assisted val bricks: Int) {
    @Inject
    class Factory(val create: (Int) -> House)
}

@Component
abstract class NestedNestedFunctionComponent {
    abstract val personFactory: Person.Factory
}

@Inject
data class OrderedAssistedBar(@Assisted val one: String, val two: Foo, @Assisted val three: String)

@Component
abstract class OrderedAssistedComponent {
    abstract val orderedBar: (String, String) -> OrderedAssistedBar
}

@Inject
data class DefaultAssistedBar(@Assisted val one: String, @Assisted val two: Int = 2)

@Component
abstract class DefaultAssistedComponent {
    abstract val withoutDefault: (String, Int) -> DefaultAssistedBar
    abstract val withDefault: (String) -> DefaultAssistedBar
}

@Inject
class UnrelatedDependency(val someString: String)

@Inject
class AssistedAndUnrelatedDep(
    @Assisted val assistedString: String,
    val unrelatedDependency: UnrelatedDependency,
)

@Component
abstract class AssistedWithOtherDependency {
    abstract val test: (String) -> AssistedAndUnrelatedDep

    @Provides
    fun string() = "provided"
}
