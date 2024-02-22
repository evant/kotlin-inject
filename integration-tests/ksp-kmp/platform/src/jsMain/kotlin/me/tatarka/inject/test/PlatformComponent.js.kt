package me.tatarka.inject.test

import kotlinx.browser.window
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import org.w3c.dom.History
import org.w3c.dom.Location
import org.w3c.dom.Window
import org.w3c.dom.WindowLocalStorage
import org.w3c.dom.WindowSessionStorage

@Component
actual abstract class PlatformComponent {
    @get:Provides val browserHistory: History get() = browserWindow.history
    @get:Provides val browserLocalStorage: WindowLocalStorage get() = browserWindow
    @get:Provides val browserLocation: Location get() = browserWindow.location
    @get:Provides val browserSessionStorage: WindowSessionStorage get() = browserWindow
    @get:Provides val browserWindow: Window get() = window

    actual companion object
}
