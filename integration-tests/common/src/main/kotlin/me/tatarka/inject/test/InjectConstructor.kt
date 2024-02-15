package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject

@Component
abstract class InjectCtorComponent {

    abstract val primaryInject: FooWithPrimaryInject

    abstract val secondaryInject: FooWithSecondaryInject
}

class FooWithPrimaryInject @Inject constructor()

class FooWithSecondaryInject(private val prop: String) {

    @Inject constructor() : this("prop")
}
