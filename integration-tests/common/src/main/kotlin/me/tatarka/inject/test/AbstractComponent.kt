package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

abstract class AbstractParentComponent {
    @Provides
    abstract fun foo(): NamedFoo

    abstract val bar: INamedBar
}

@Component
abstract class ParentComponentImpl1 : AbstractParentComponent() {
    override fun foo(): NamedFoo = NamedFoo("parent1")

    @Provides
    fun bar2(): INamedBar = NamedBar("parent1")
}

@Component
abstract class ParentComponentImpl2 : AbstractParentComponent() {
    override fun foo(): NamedFoo = NamedFoo("parent2")

    @Provides
    fun bar2(): INamedBar = NamedBar("parent2")
}

@Component
abstract class AbstractParentChildComponent(@Component val parent: AbstractParentComponent) {
    abstract val foo: NamedFoo
    abstract val bar: INamedBar
}

@CustomScope
abstract class ScopedAbstractParentComponent

@Component
abstract class ScopedParentComponentImpl1 : ScopedAbstractParentComponent()

@Component
abstract class ScopedParentComponentImpl2 : ScopedAbstractParentComponent()

@Component
abstract class ScopedAbstractParentChildComponent(@Component val parent: ScopedAbstractParentComponent) {
    abstract val bar: CustomScopeBar
}
