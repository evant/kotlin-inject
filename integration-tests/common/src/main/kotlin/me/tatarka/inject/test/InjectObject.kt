package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.test.different.DifferentPackageFoo

@Inject
object FooObject

@Inject
class DependsOnFooObject(val foo: FooObject)

@Component
abstract class ObjectComponent {
    abstract val injectObject: FooObject
    abstract val differentPackageObject: DifferentPackageFoo.MyObject
    abstract fun injectObject2(): FooObject
    abstract fun dependsOnFooObject2(): DependsOnFooObject
}

interface CompanionFooInterface

interface CompanionFoo {
    @Inject
    companion object : CompanionFooInterface
}

@Inject
class DependOnCompanionFoo(val foo: CompanionFooInterface)

@Component
abstract class CompanionObjectComponent {
    abstract val foo: CompanionFooInterface
    abstract val dependsOnFoo: DependOnCompanionFoo

    val CompanionFoo.Companion.bind: CompanionFooInterface
        @Provides get() = this
}
