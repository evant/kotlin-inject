package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.test.different.DifferentPackageFoo

class ProvidesFoo(val bar: ProvidesBar? = null)

@Inject
class ProvidesBar

@Component
abstract class ProvidesFunctionComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun foo2() = ProvidesFoo().also { providesCalled = true }
}

@Component
abstract class ProvidesFunctionArgComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun foo2(bar: ProvidesBar) = ProvidesFoo(bar).also { providesCalled = true }
}

@Component
abstract class ProvidesValComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    val provideFoo
        @Provides get() = ProvidesFoo().also { providesCalled = true }
}

@Component
abstract class ProvidesExtensionFunctionComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun ProvidesBar.provideFoo() = ProvidesFoo(this).also { providesCalled = true }
}

@Component
abstract class ProvidesExtensionValComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    val ProvidesBar.provideFoo
        @Provides get() = ProvidesFoo(this).also { providesCalled = true }
}

@Component
abstract class ProvidesValConstructorComponent(@get:Provides val provideFoo: ProvidesFoo) {
    abstract val foo: ProvidesFoo
}

class Foo1
class Foo2

@Inject
class Foo3 : IFoo {
    private val value = 1

    override fun toString(): String = "Foo3"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Foo3) return false
        if (value != other.value) return false
        return true
    }

    override fun hashCode(): Int {
        return value
    }
}

@Component
abstract class ProvidesOverloadsComponent {
    abstract val foo1: Foo1
    abstract val foo2: Foo2
    abstract val foo3: Foo3
    abstract val foo4: IFoo

    @Provides
    fun foo2a() = Foo1()

    @Provides
    fun foo2a(
        @Suppress("UNUSED_PARAMETER")
        bar: ProvidesBar,
    ) = Foo2()

    val foo
        @Provides get() = Foo3()

    val Foo3.foo: IFoo
        @Provides get() = this
}

class IntFoo(val int: Int)

class IntArrayFoo(val intArray: IntArray)

class StringArrayFoo(val stringArray: Array<String>)

@Inject
class BasicTypes(
    val boolean: Boolean,
    val byte: Byte,
    val char: Char,
    val short: Short,
    val int: Int,
    val long: Long,
    val float: Float,
    val double: Double,
    val string: String,
    val booleanArray: BooleanArray,
    val stringArray: Array<String>,
    val intFoo: IntFoo,
    val intArrayFoo: IntArrayFoo,
    val stringArrayFoo: StringArrayFoo,
)

@Component
abstract class ProvidesBasicTypes {
    abstract val basicType: BasicTypes

    val boolean
        @Provides get() = true

    val byte
        @Provides get() = 1.toByte()

    val char
        @Provides get() = 'a'

    val short
        @Provides get() = 2.toShort()

    val int
        @Provides get() = 3

    val long
        @Provides get() = 4L

    val float
        @Provides get() = 5f

    val double
        @Provides get() = 6.0

    val string
        @Provides get() = "b"

    val booleanArray
        @Provides get() = booleanArrayOf(true)

    val stringArray
        @Provides get() = arrayOf("c")

    val intArray
        @Provides get() = intArrayOf(7)

    val Int.bind
        @Provides get() = IntFoo(this)

    val IntArray.bind
        @Provides get() = IntArrayFoo(this)

    val Array<String>.bind
        @Provides get() = StringArrayFoo(this)
}

@Inject
class ValConstructorBasicTypes(
    val boolean: Boolean,
    val string: String,
)

@Component
abstract class ProvidesValConstructorBasicTypes(
    @get:Provides val boolean: Boolean,
    @get:Provides val string: String,
) {
    abstract val basicType: ValConstructorBasicTypes
}

@Component
abstract class ProvidesNestedClassComponent {
    abstract val fooFactory: DifferentPackageFoo.Factory
}

// https://github.com/evant/kotlin-inject/issues/321
@Component
abstract class OptimizesProvides {
    @Provides
    fun OFooImpl.bind(): OFoo = this
    abstract fun provideFoo(): OFoo
    abstract fun provideBar(): OBar
}

interface OFoo
@Inject
class OFooImpl : OFoo

@Inject
class OBar(val foo: OFoo)

class BarWithInnerClass {
    @Inject
    inner class Bar(val foo: Foo) {
        val parent = this@BarWithInnerClass
    }

    @Inject
    inner class BarWithTypeArg<T> {
        val parent = this@BarWithInnerClass
    }
}

@Component
@CustomScope
abstract class ProvidesInnerClassComponent {

    abstract val innerClass: BarWithInnerClass.Bar

    abstract val innerClassWithTypeArg: BarWithInnerClass.BarWithTypeArg<Foo>

    @Provides
    @CustomScope
    fun provideOuter(): BarWithInnerClass {
        return BarWithInnerClass()
    }
}
