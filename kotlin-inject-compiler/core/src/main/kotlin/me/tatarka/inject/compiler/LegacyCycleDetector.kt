package me.tatarka.inject.compiler

import me.tatarka.kotlin.ast.AstElement
import me.tatarka.kotlin.ast.AstProvider

/**
 * Builds of a stack of elements visited to see if we hit a cycle. Normally a cycle will cause a compile error. However
 * the cycle can be 'broken' by delaying construction.
 */
class LegacyCycleDetector {
    private val detector = CycleDetector<TypeKey, AstElement>()

    /**
     * Denote that construction is being delayed. A cycle that crosses a delayed element can be resolved.
     */
    fun delayedConstruction() {
        detector.delayedConstruction()
    }

    /**
     * Returns the variable name if [LegacyCycleResult.Resolvable] was hit for the given type key lower in the tree. This
     * means you should create the variable that was referenced.
     */
    fun hitResolvable(key: TypeKey): String? {
        return if (detector.hitResolvable(key)) {
            key.type.toVariableName()
        } else {
            null
        }
    }

    /**
     * Checks that the given element with the given type will produce a cycle. The result is provided in the given block
     * so that then state can be rest after recursing.
     *
     * @see LegacyCycleResult
     */
    fun <T> check(key: TypeKey, element: AstElement, block: (LegacyCycleResult) -> T): T {
        return detector.check(key, element) { result ->
            block(when(result) {
                CycleResult.None -> LegacyCycleResult.None
                CycleResult.Cycle -> LegacyCycleResult.Cycle
                is CycleResult.Resolvable -> LegacyCycleResult.Resolvable(result.key.type.toVariableName())
            })
        }
    }

    /**
     * Produce a trace of visited elements.
     */
    fun trace(provider: AstProvider): String = detector.trace { with(provider) { it.toTrace() } }
}

sealed class LegacyCycleResult {
    /**
     * There was no cycle, you may proceed normally.
     */
    data object None : LegacyCycleResult()

    /**
     * There was a cycle, you should error out.
     */
    data object Cycle : LegacyCycleResult()

    /**
     * There was a cycle but it was across a delayed construction so it can be resolved. Reference the variable here
     * with the given name and call [LegacyCycleDetector.hitResolvable] higher up the tree to create it.
     */
    class Resolvable(val name: String) : LegacyCycleResult()
}