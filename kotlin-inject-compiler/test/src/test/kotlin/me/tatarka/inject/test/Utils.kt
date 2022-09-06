package me.tatarka.inject.test

import androidx.room.compiler.processing.util.compiler.TestCompilationResult
import assertk.Assert
import assertk.assertions.isNotNull
import assertk.assertions.message
import assertk.assertions.prop
import me.tatarka.inject.output
import javax.tools.Diagnostic

fun Assert<Throwable>.output(): Assert<String> = message().isNotNull()

fun Assert<TestCompilationResult>.warnings(): Assert<String> =
    prop("warnings") { it.output(Diagnostic.Kind.WARNING) }