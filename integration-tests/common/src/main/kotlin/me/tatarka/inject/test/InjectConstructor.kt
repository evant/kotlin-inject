package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject

@Inject
class FooWithInnerClass {
    @Inject
    inner class Bar(val foo: Foo)

    @Inject
    inner class BarWithTypeArg<T>
}


@Component
abstract class InjectInnerClassComponent {
    abstract val innerClass: FooWithInnerClass.Bar

    abstract val innerClassWithTypeArg: FooWithInnerClass.BarWithTypeArg<Foo>
}

@Component
abstract class InjectCtorComponent {

    abstract val primaryInject: FooWithPrimaryInject

    abstract val secondaryInject: FooWithSecondaryInject
}

class FooWithPrimaryInject @Inject constructor()

class FooWithSecondaryInject(private val prop: String) {

    @Inject constructor() : this("prop")
}
