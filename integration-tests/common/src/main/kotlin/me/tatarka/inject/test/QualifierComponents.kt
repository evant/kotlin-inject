package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Qualifier

@Qualifier
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
annotation class Named(val value: String)

@Component
abstract class NamedComponent {

    @get:Named("one")
    abstract val one: String

    @get:Named("two")
    abstract val two: String

    abstract val three: @Named("three") String

    abstract val four: @Named("four") String

    @get:Named("five")
    abstract val five: String

    val provide1: String
        @Provides @Named("one") get() = "one"

    val provide2: String
        @Provides @Named("two") get() = "two"

    val provide3: @Named("three") String
        @Provides get() = "three"

    val provide4: String
        @Provides @Named("four") get() = "four"

    val provide5: @Named("five") String
        @Provides get() = "five"
}

@Component
@CustomScope
abstract class ScopedNamedComponent {

    @get:Named("one")
    abstract val one: String

    @get:Named("two")
    abstract val two: String

    abstract val three: @Named("three") String

    @get:CustomScope
    val provide1: String
        @Provides @Named("one") get() = "one"

    @get:CustomScope
    val provide2: String
        @Provides @Named("two") get() = "two"

    @get:CustomScope
    val provide3: @Named("three") String
        @Provides get() = "three"
}

