package me.tatarka.inject.test

import me.tatarka.inject.annotations.KmpComponentCreate

@KmpComponentCreate
expect fun createNative(): KmpComponent2
