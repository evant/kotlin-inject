package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.CreateKmpComponent

@Component
abstract class KmpComponent {
    companion object
}

@Component
abstract class KmpComponent2 {
    companion object
}

@CreateKmpComponent
expect fun createKmp(): KmpComponent

@CreateKmpComponent
expect fun KmpComponent.Companion.createKmp(): KmpComponent
