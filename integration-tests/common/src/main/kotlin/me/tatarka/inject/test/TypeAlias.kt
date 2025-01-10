package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject

@Inject
class TypeAliasBar

typealias TypeAliasToTypeAliasBar = TypeAliasBar
typealias TypeAliasToTypeAliasToTypeAliasBar = TypeAliasToTypeAliasBar

@Inject
fun TypeAliasFoo(
    bar: TypeAliasBar,
    typeAliasToBar: TypeAliasToTypeAliasBar,
    typeAliasToTypeAliasToBar: TypeAliasToTypeAliasToTypeAliasBar,
) {}
typealias TypeAliasFoo = () -> Unit

typealias TypeAliasToTypeAliasFoo = TypeAliasFoo
typealias TypeAliasToTypeAliasToTypeAliasFoo = TypeAliasToTypeAliasFoo

@Component
abstract class TypeAliasesComponent {
    abstract fun typeAliasFoo(): TypeAliasFoo
    abstract fun typeAliasToTypeAliasFoo(): TypeAliasToTypeAliasFoo
    abstract fun typeAliasToTypeAliasToTypeAliasFoo(): TypeAliasToTypeAliasToTypeAliasFoo
}
