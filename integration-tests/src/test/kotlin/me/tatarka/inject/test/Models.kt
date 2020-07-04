package me.tatarka.inject.test

import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Scope

interface IFoo

@Inject class Foo : IFoo

@Inject class Bar(val foo: Foo)

class NamedFoo(val name: String)

@Scope
annotation class CustomScope

var customScopeBarConstructorCount = 0

@CustomScope @Inject class CustomScopeBar {
    init {
        customScopeBarConstructorCount++
    }
}
