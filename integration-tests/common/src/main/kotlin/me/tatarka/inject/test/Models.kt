package me.tatarka.inject.test

import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Scope
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER

interface IFoo

interface IBar

var fooConstructorCount = 0

@Inject class Foo : IFoo {
    init {
        fooConstructorCount++
    }
    private val value = 2

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Foo) return false
        if (value != other.value) return false
        return true
    }

    override fun hashCode(): Int {
        return value
    }

    override fun toString() = "Foo"
}

@Inject data class Bar(val foo: Foo) : IFoo

@Inject data class BarImpl(val foo: IFoo) : IBar

@Inject data class BarImpl2(val foo: IFoo) : IFoo

@Inject class Baz : IFoo

@Inject data class Bar2(val bar: Bar) : IFoo

@Inject data class Bar3(val bar: Bar) : IFoo

@Inject internal class InternalFoo

class InternalBarConstructor @Inject internal constructor(internal val foo: InternalFoo)

data class NamedFoo(val name: String)

interface INamedBar {
    val name: String
}

class NamedBar(override val name: String) : INamedBar

@Scope
@Target(CLASS, FUNCTION, PROPERTY_GETTER)
annotation class CustomScope

var customScopeBarConstructorCount = 0

@CustomScope @Inject class CustomScopeBar : IBar {
    init {
        customScopeBarConstructorCount++
    }
}