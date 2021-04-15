package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import org.junit.Test

class Inner1 {
    @Component
    abstract class InnerClassComponent
}

class Inner2 {
    @Component
    abstract class InnerClassComponent
}

class InnerClassComponentTest {
    @Test
    fun generates_components_in_an_inner_class_with_a_unique_name() {
        val component1 = Inner1.InnerClassComponent::class.create()
        val component2 = Inner2.InnerClassComponent::class.create()
    }
}