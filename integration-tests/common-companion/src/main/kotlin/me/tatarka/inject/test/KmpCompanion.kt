package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreate

@Component
abstract class KmpComponent {
    companion object
}

@Component
abstract class KmpComponent2 {
    companion object
}

@KmpComponentCreate
expect fun createKmp(): KmpComponent

@KmpComponentCreate
expect fun KmpComponent.Companion.createKmp(): KmpComponent
