package me.tatarka.inject.test

import me.tatarka.inject.annotations.KmpComponentCreator

@KmpComponentCreator
expect fun createNative(): KmpComponent2
