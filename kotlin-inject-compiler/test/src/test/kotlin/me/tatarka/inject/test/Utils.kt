package me.tatarka.inject.test

import assertk.Assert
import assertk.assertions.isNotNull
import assertk.assertions.message

fun Assert<Throwable>.output(): Assert<String> = message().isNotNull()