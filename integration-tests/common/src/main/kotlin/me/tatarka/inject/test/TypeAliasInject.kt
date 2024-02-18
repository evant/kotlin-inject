package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject

typealias InjectTypeAlias = Inject

class FooWithPrimaryConstructorInjectTypeAlias @InjectTypeAlias constructor()

class FooWithSecondaryConstructorInjectTypeAlias(private val prop: String) {

    @InjectTypeAlias
    constructor() : this("prop")
}

@InjectTypeAlias
class FooWithInjectTypeAlias

typealias fooWithPrimaryCreator = () -> FooWithPrimaryConstructorInjectTypeAlias

@InjectTypeAlias
fun fooWithPrimaryCreator(foo: FooWithPrimaryConstructorInjectTypeAlias) = foo

typealias TypeAliasToInjectTypeAlias = InjectTypeAlias

typealias TypeAliasToTypeAliasToInjectTypeAlias = TypeAliasToInjectTypeAlias

@TypeAliasToTypeAliasToInjectTypeAlias
class FooWithTypeAliasToTypeAliasToTypeAlias

@Component
abstract class InjectTypeAliasComponent {

    abstract val primaryConstructorWithInjectTypeAlias: FooWithPrimaryConstructorInjectTypeAlias

    abstract val secondaryConstructorWithInjectTypeAlias: FooWithSecondaryConstructorInjectTypeAlias

    abstract val classWithInjectTypeAlias: FooWithInjectTypeAlias

    abstract val functionWithInjectTypeAlias: fooWithPrimaryCreator

    abstract val fooWithTypeAliasToTypeAliasToTypeAlias: FooWithTypeAliasToTypeAliasToTypeAlias
}
