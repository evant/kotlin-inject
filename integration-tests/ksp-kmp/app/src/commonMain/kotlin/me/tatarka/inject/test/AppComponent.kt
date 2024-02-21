package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component

@Component
abstract class AppComponent(
    @Component val platformComponent: PlatformComponent
) : FooProvider {
    abstract val baz: Baz

    companion object
}

expect fun AppComponent.Companion.create(platformComponent: PlatformComponent): AppComponent

expect interface ExpectType

@Component
abstract class SubtypeOfExpectTypeComponent : ExpectType {
    companion object
}

expect fun SubtypeOfExpectTypeComponent.Companion.create(): SubtypeOfExpectTypeComponent
