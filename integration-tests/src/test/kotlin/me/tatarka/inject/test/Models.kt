package me.tatarka.inject.test

import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Scope

interface IFoo

var fooConstructorCount = 0

@Inject class Foo : IFoo {
    init {
        fooConstructorCount++
    }
}

@Inject class Bar(val foo: Foo)

class NamedFoo(val name: String)

interface INamedBar {
    val name: String
}

class NamedBar(override val name: String): INamedBar

@Scope
annotation class CustomScope

var customScopeBarConstructorCount = 0

@CustomScope @Inject class CustomScopeBar {
    init {
        customScopeBarConstructorCount++
    }
}
