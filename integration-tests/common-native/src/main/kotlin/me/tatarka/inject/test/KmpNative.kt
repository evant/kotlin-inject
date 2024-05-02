package me.tatarka.inject.test

import me.tatarka.inject.annotations.CreateKmpComponent

@CreateKmpComponent
expect fun createNative(): KmpComponent2
