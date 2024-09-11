package me.tatarka.inject.compiler

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class CycleDetectorTest {
    private val cycleDetector = CycleDetector<String, Element>()

    @Test
    fun detects_single_element_cycle() {
        lateinit var element: Element
        element = Element("name") { listOf(element) }

        val result = checkForCycle(element)

        assertThat(result).isEqualTo(CycleResult.Cycle)
    }

    @Test
    fun detects_two_element_cycle() {
        lateinit var element: Element
        element = Element("name1") { listOf(Element("name2") { listOf(element) }) }

        val result = checkForCycle(element)

        assertThat(result).isEqualTo(CycleResult.Cycle)
    }

    @Test
    fun delay_breaks_cycle() {
        lateinit var element: Element
        element = Element("name", delayed = true) { listOf(element) }

        val result = checkForCycle(element)

        assertThat(result).isEqualTo(CycleResult.Resolvable("name"))
    }

    private fun checkForCycle(element: Element): CycleResult<String> {
        if (element.delayed) {
            cycleDetector.delayedConstruction()
        }
        return cycleDetector.check(element.name, element) { result ->
            if (result != CycleResult.None) return@check result
            for (ref in element.references()) {
                val refResult = checkForCycle(ref)
                if (refResult != CycleResult.None) {
                    return@check refResult
                }
            }
            CycleResult.None
        }
    }

    private class Element(
        val name: String,
        val delayed: Boolean = false,
        val references: () -> List<Element>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Element
            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun toString(): String {
            return name
        }
    }
}
