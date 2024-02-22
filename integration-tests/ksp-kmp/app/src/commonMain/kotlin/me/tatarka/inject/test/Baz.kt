package me.tatarka.inject.test

import me.tatarka.inject.annotations.Inject

@Inject
class Baz(
    private val foo: Foo
)
