package me.tatarka.inject.test

import me.tatarka.inject.annotations.Inject

interface IFoo

@Inject class Foo : IFoo

@Inject class Bar(val foo: Foo)

class NamedFoo(val name: String)
