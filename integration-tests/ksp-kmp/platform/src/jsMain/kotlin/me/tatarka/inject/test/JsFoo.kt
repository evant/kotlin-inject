package me.tatarka.inject.test

import me.tatarka.inject.annotations.Inject
import org.w3c.dom.Window

@Inject
class JsFoo(
    private val window: Window
) : Foo {
    override fun bar() {
        window
    }
}
