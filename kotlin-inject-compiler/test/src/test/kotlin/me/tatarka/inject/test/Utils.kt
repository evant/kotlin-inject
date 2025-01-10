package me.tatarka.inject.test

import assertk.Assert
import assertk.assertions.isNotNull
import assertk.assertions.message
import assertk.assertions.prop
import com.tschuchort.compiletesting.DiagnosticSeverity
import me.tatarka.inject.TestCompilationResult

fun Assert<Throwable>.output(): Assert<String> = message().isNotNull()

fun Assert<TestCompilationResult>.warnings(): Assert<String> =
    prop("warnings") { it.output(DiagnosticSeverity.WARNING) }
