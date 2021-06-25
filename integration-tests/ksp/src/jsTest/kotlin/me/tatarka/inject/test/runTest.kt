package me.tatarka.inject.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

/**
 * Workaround to use suspending functions in unit tests
 */
actual fun runTest(block: suspend (scope: CoroutineScope) -> Unit): dynamic = GlobalScope.promise { block(this) }