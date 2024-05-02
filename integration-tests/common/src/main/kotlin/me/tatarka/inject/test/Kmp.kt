package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.CreateKmpComponent

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

@CreateKmpComponent
expect fun createKmp(): KmpComponent

@Suppress("LocalVariableName")
@CreateKmpComponent
expect fun createKmp(
    `data`: Any,
    `val`: Any,
    `1`: String,
    foo: String
): KmpComponentWithParams
