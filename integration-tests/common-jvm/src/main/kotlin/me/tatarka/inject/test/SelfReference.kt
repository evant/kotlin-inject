package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component

@Component
interface SelfReferenceCompanionComponent {
    val foo: Foo

    companion object : SelfReferenceCompanionComponent by SelfReferenceCompanionComponent::class.create()
}

@Component
interface SelfReferenceInnerClassComponent {
    val foo: Foo

    class Instance : SelfReferenceInnerClassComponent by SelfReferenceInnerClassComponent::class.create()
}