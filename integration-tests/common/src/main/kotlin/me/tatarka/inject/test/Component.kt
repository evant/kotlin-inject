package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component

@Component abstract class Component1 {
    abstract val foo: Foo
}

@Component abstract class Component2 {
    abstract val bar: Bar
}

@Component interface Component3 {
    val bar: Bar
}

@Component interface Component4 {
    fun foo(): Foo
}
