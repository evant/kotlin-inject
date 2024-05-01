package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreator

@Component
abstract class KmpComponent

@Suppress("UNUSED_PARAMETER", "LocalVariableName")
@Component
abstract class KmpComponentWithParams(
    `data`: Any,
    `val`: Any,
    `1`: String,
    foo: String
)

@Component
abstract class KmpComponent2

@KmpComponentCreator
expect fun createKmp(): KmpComponent

@Suppress("LocalVariableName")
@KmpComponentCreator
expect fun createKmp(
    `data`: Any,
    `val`: Any,
    `1`: String,
    foo: String
): KmpComponentWithParams
