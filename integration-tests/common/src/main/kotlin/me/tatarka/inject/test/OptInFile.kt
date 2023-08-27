@file:OptIn(FileOptIn::class)

package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@RequiresOptIn
annotation class FileOptIn

@RequiresOptIn
annotation class ClassOptIn

@RequiresOptIn
annotation class ClassOptIn2

@FileOptIn
@ClassOptIn
@ClassOptIn2
class OptInFoo

@ClassOptIn
class OptInBar

@Component
@OptIn(ClassOptIn::class, ClassOptIn2::class)
abstract class OptInFileComponent(val arg: OptInBar) {
    abstract val foo: OptInFoo

    @Provides fun providesFoo(): OptInFoo = OptInFoo()
}
