package me.tatarka.inject.test

import kotlin.test.Test

class CompanionTest {
    @Test
    fun creates_a_component_with_a_companion() {
        CompanionComponent.create()
    }
}
