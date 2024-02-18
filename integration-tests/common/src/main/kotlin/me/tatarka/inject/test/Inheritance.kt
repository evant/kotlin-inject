package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

interface ComponentInterface {
    val foo: Foo
    val bar: Bar
}

@Component
abstract class InterfaceComponentWithIdenticalProvides(
    @get:Provides
    override val foo: Foo,
    override val bar: Bar,
) : ComponentInterface

@Component
abstract class InterfaceComponent : ComponentInterface

interface GenericComponentInterface<T> {
    val genericFoo: T
}

@Component
abstract class GenericInterfaceComponent : GenericComponentInterface<Foo>

interface ProvidesComponentInterface {
    val iFoo: IFoo
        @Provides get() = Foo()
}

@Component
abstract class ProvidesInterfaceComponent : ProvidesComponentInterface {
    abstract val foo: IFoo
}

interface ProvidesIndirectInterface : ProvidesComponentInterface

@Component
abstract class ProvidesIndirectComponent : ProvidesIndirectInterface {
    abstract val foo: IFoo
}

interface ProvidesScopedComponentInterface {
    val iFoo: IFoo
        @CustomScope
        @Provides get() = Foo()
}

@Component
@CustomScope
abstract class ProvidesScopedInterfaceComponent : ProvidesScopedComponentInterface {
    abstract val foo: IFoo
}


interface AbstractProvidesInterface {
    @get:Provides val foo: IFoo
}

@Component
abstract class AbstractProvidesImplComponent: AbstractProvidesInterface {
    abstract val bar: BarImpl

    override val foo: IFoo
        get() = Foo()
}

interface DuplicateDeclaration1 {
    val bar: Bar

    fun bar2(): Bar
}

interface DuplicateDeclaration2 {
    val bar: Bar

    fun bar2(): Bar
}

@Component
abstract class DuplicateDeclarationComponent : DuplicateDeclaration1, DuplicateDeclaration2 {
    @get:Provides val foo: Foo = Foo()
}

interface AppComponent

abstract class SessionComponent {
    abstract val appComponent: AppComponent
}

@Component
abstract class InheritedAppComponent : AppComponent

@Component
abstract class InheritedSessionComponent(
    @Component override val appComponent: InheritedAppComponent,
) : SessionComponent()
