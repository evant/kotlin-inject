package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.TargetComponentAccessor

@Component
abstract class KmpComponent {
    companion object
}

@Component
abstract class KmpComponent2 {
    companion object
}

@TargetComponentAccessor
expect fun createKmp(): KmpComponent

@TargetComponentAccessor
expect fun KmpComponent.Companion.createKmp(): KmpComponent
