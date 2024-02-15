package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides

typealias NamedFoo1 = NamedFoo
typealias NamedFoo2 = NamedFoo

@Component abstract class ProvidesAliasedComponent {
    abstract val foo1: NamedFoo1

    abstract val foo2: NamedFoo2

    @Provides fun foo1a(): NamedFoo1 = NamedFoo("1")
    @Provides fun foo2a(): NamedFoo2 = NamedFoo("2")
}

@Inject class AliasedFoo(val foo1: NamedFoo1, val foo2: NamedFoo2)

@Component abstract class ConstructorAliasedComponent {
    abstract val aliasedFoo: AliasedFoo

    @Provides fun foo1(): NamedFoo1 = NamedFoo("1")
    @Provides fun foo2(): NamedFoo2 = NamedFoo("2")
}

@CustomScope @Component abstract class ScopedConstructorAliasedComponent {
    abstract val aliasedFoo: AliasedFoo

    @CustomScope @Provides fun foo1(): NamedFoo1 = NamedFoo("1")
    @CustomScope @Provides fun foo2(): NamedFoo2 = NamedFoo("2")
}

@Target(AnnotationTarget.TYPE)
annotation class FooAnnotation

data class GenericFoo<T>(val value: T)

typealias AnnotatedAliasedFoo<T> = @FooAnnotation GenericFoo<T>

@Component abstract class AliasedProvidesComponent {
    abstract val foo: AnnotatedAliasedFoo<String>

    @Provides fun foo2(): AnnotatedAliasedFoo<String> = GenericFoo("1")
}
