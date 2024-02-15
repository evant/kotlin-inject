package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component

@Component
abstract class ArgComponent(
    val simple: String,
    val lambda: (String) -> String,
    val receiver: String.() -> String,
)

@Component
abstract class DefaultArgComponent(
    val required1: String,
    val optional: String = "default",
    val required2: String,
)
