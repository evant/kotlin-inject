package me.tatarka.inject.test

import me.tatarka.inject.annotations.Provides

actual interface FooProvider {
    @Provides fun JsFoo.bind(): Foo = this
}
