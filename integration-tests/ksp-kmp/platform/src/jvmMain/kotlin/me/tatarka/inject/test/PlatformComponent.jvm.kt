package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component

@Suppress("UtilityClassWithPublicConstructor")
@Component
actual abstract class PlatformComponent {
    actual companion object
}
