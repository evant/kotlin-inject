package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreator

@Component
abstract class KmpComponent {
    companion object
}

@Component
abstract class KmpComponent2 {
    companion object
}

@KmpComponentCreator
expect fun createKmp(): KmpComponent

@KmpComponentCreator
expect fun KmpComponent.Companion.createKmp(): KmpComponent
