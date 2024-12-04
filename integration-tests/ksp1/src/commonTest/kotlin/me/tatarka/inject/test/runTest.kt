package me.tatarka.inject.test

import kotlinx.coroutines.CoroutineScope

/**
 * Workaround to use suspending functions in unit tests
 */
expect fun runTest(block: suspend (scope: CoroutineScope) -> Unit)