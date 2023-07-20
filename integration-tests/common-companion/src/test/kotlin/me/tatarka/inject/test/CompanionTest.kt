package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import kotlin.test.Test

@Component abstract class CompanionComponent {
    companion object
}

class CompanionTest {
    @Test
    fun creates_a_component_with_a_companion() {
        CompanionComponent.create()
    }
}