package me.tatarka.inject.test

import me.tatarka.inject.annotations.Inject

@Inject
class JvmFoo : Foo {
    override fun bar() {}
}
