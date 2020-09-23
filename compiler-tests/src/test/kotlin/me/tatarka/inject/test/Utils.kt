package me.tatarka.inject.test

import assertk.Assert
import assertk.assertions.isNotNull
import assertk.assertions.message
import java.io.File

fun Assert<Throwable>.output(): Assert<String> = message().isNotNull()

fun File.recursiveDelete() {
    val files = listFiles()
    if (files != null) {
        for (each in files) {
            each.recursiveDelete()
        }
    }
    delete()
}

