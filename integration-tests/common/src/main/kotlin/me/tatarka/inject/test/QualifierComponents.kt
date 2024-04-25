package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Qualifier

@Qualifier
@Retention(AnnotationRetention.SOURCE)
annotation class Named(val value: String)

@Component
abstract class NamedComponent {

    @get:Named("one")
    abstract val one: String

    @get:Named("two")
    abstract val two: String

    val provide1: String
        @Provides @Named("one") get() = "one"

    val provide2: String
        @Provides @Named("two") get() = "two"
}

@Component
@CustomScope
abstract class ScopedNamedComponent {

    @get:Named("one")
    abstract val one: String

    @get:Named("two")
    abstract val two: String

    @get:CustomScope
    val provide1: String
        @Provides @Named("one") get() = "one"

    @get:CustomScope
    val provide2: String
        @Provides @Named("two") get() = "two"
}
