package me.tatarka.inject.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

/**
 * Workaround to use suspending functions in unit tests
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalWasmJsInterop::class)
actual fun runTest(block: suspend (scope: CoroutineScope) -> Unit) {
    GlobalScope.promise { block(this) }
}
