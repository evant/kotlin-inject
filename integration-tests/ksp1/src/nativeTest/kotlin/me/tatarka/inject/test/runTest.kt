package me.tatarka.inject.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Workaround to use suspending functions in unit tests
 */
actual fun runTest(block: suspend (scope: CoroutineScope) -> Unit) = runBlocking { block(this) }