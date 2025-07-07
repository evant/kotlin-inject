package me.tatarka.inject.test

import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides

data class FooValue(val name: String)

@Component
abstract class SetComponent {
    abstract val items: Set<FooValue>

    abstract val funItems: Set<() -> FooValue>

    abstract val lazyItems: Set<Lazy<FooValue>>

    @Provides
    @IntoSet
    fun fooValue1() = FooValue("1")

    val fooValue2
        @Provides @IntoSet get() = FooValue("2")
}

@Component
abstract class MultibindWithQualifiersComponent {
    abstract val set1: Set<@Named("foo") String>
    abstract val set2: Set<@Named("bar") String>
    abstract val map1: Map<@Named("bar") String, @Named("foo") String>
    abstract val map2: Map<@Named("foo") String, @Named("bar") String>

    @Provides
    @IntoSet
    protected fun fooSet(): @Named("foo") String = "1"

    @Provides
    @IntoSet
    protected fun barSet(): @Named("bar") String = "2"

    @Provides
    @IntoMap
    protected fun fooMap(): Pair<@Named("foo") String, @Named("bar") String> = "2" to "1"

    @Provides
    @IntoMap
    protected fun barMap(): Pair<@Named("bar") String, @Named("foo") String> = "1" to "2"
}

@Component
abstract class DynamicKeyComponent {

    abstract val items: Map<String, FooValue>

    @Provides
    @IntoMap
    fun fooValue1() = "1" to FooValue("1")

    val fooValue2
        @Provides @IntoMap get() = "2" to FooValue("2")
}

@Component
abstract class ParentSetComponent {
    val Foo.bind: IFoo
        @Provides @IntoSet get() = this

    val Bar.bind: IFoo
        @Provides @IntoSet get() = this

    // Should not be in the set
    val Baz.bind: IFoo
        @Provides get() = this
}

@Component
abstract class ChildSetComponent(@Component val parent: ParentSetComponent) {
    val Foo3.bind: IFoo
        @Provides @IntoSet get() = this

    abstract val items: Set<IFoo>
}

typealias Entry = Pair<String, FooValue>

@Component
abstract class TypeAliasMapComponent {
    abstract val items: Map<String, FooValue>

    val fooValue1: Entry
        @Provides @IntoMap get() = "1" to FooValue("1")

    val fooValue2: Entry
        @Provides @IntoMap get() = "2" to FooValue("2")
}

typealias Factory<T> = () -> T

@Component
abstract class TypeAliasIntoMapDependencyComponent {
    abstract val items: Map<String, () -> Any?>

    @Provides
    @IntoMap
    fun foo(f: Factory<Foo>): Pair<String, () -> Any?> = "test" to f
}

@Component
abstract class AssistedSetComponent {
    abstract val items: Set<(String) -> FooValue>

    @Provides
    @IntoSet
    fun fooValue1(@Assisted arg: String): FooValue = FooValue("${arg}1")

    @Provides
    @IntoSet
    fun fooValue2(@Assisted arg: String): FooValue = FooValue("${arg}2")
}

typealias MyString = String

@Component
abstract class TypeAliasSetComponent {
    abstract val stringItems: Set<String>

    abstract val myStringItems: Set<MyString>

    @Provides
    @IntoSet
    fun stringValue1(): String = "string"

    @Provides
    @IntoSet
    fun stringValue2(): MyString = "myString"
}

@Component
abstract class TypeAliasKeyMapComponent {
    abstract val stringMap: Map<String, String>

    abstract val myStringMap: Map<MyString, String>

    @Provides
    @IntoMap
    fun stringEntry1(): Pair<String, String> = "1" to "string"

    @Provides
    @IntoMap
    fun myStringEntry1(): Pair<MyString, String> = "1" to "myString"
}
