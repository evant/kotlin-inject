package me.tatarka.inject.test

import java.io.File

fun File.recursiveDelete() {
    val files = listFiles()
    if (files != null) {
        for (each in files) {
            each.recursiveDelete()
        }
    }
    delete()
}

