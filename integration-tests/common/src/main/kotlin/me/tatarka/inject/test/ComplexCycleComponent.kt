package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides

interface Lvl1
interface Lvl2
interface Lvl3
interface Lvl4

@Inject
class Lvl1Impl(val dep: Lvl2): Lvl1
@Inject
class Lvl2Impl(val dep: Lvl3): Lvl2
@Inject
class Lvl3Impl(val dep: Lazy<Lvl4>): Lvl3
@Inject
class Lvl4Impl(val dep2: Dep2): Lvl4

@Inject
class Dep1(val lvl1: Lvl1, val lvl2: Lvl2Impl, val lvl3: Lvl3)
@Inject
class Dep2(val lvl2: Lvl2Impl, val lvl3: Lvl3, val lvl1: Lvl1, )

@Component
abstract class ComplexCycleComponent {
    abstract val lvl1: Lvl1

    @Provides
    fun lvl1(impl: Lvl1Impl): Lvl1 = impl
    @Provides
    fun lvl2(impl: Lvl2Impl): Lvl2 = impl
    @Provides
    fun lvl3(impl: Lvl3Impl): Lvl3 = impl
    @Provides
    fun lvl4(impl: Lvl4Impl): Lvl4 = impl
}