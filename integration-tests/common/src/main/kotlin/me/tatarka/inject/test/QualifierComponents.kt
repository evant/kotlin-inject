package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Qualifier

@Qualifier
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE,
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

@Inject
class ScopedNamedBar(
    @Named("two")
    val two: IFoo,

    @Named("one")
    val one: IFoo,
)

@Component
@CustomScope
abstract class ScopedNamedComponent2 {
    abstract val bar: ScopedNamedBar

    @CustomScope
    @Provides
    @Named("one")
    fun provideFoo1(): IFoo = Foo()

    @CustomScope
    @Provides
    @Named("two")
    fun provideFoo2(@Named("one") foo: IFoo): IFoo = Foo()
}

@Component
@CustomScope
interface ScopedNamedComponent3 {
    @get:Named("one")
    val one: IBar

    @get:Named("two")
    val two: IBar

    val CustomScopeBar.bindOne: IBar @Provides @Named("one") get() = this
    val CustomScopeBar.bindTwo: IBar @Provides @Named("two") get() = this
}

@Component
@CustomScope
interface HttpComponent2 {
    @get:Named("one")
    val one: IBar

    @get:Named("two")
    val two: IBar

    @Named("one")
    @Provides
    fun bineOne(one: CustomScopeBar): IBar = one

    @Named("two")
    @Provides
    fun bindTwo(two: CustomScopeBar): IBar = two
}