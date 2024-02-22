package me.tatarka.inject.test

import me.tatarka.inject.annotations.Provides

actual interface FooProvider {
    @Provides fun JvmFoo.bind(): Foo = this
}
