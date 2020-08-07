package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.*
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import kotlin.test.Test

class ProvidesFoo(val bar: ProvidesBar? = null)

@Inject class ProvidesBar

@Component abstract class ProvidesFunctionComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun foo() = ProvidesFoo().also { providesCalled = true }
}

@Component abstract class ProvidesFunctionArgComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun foo(bar: ProvidesBar) = ProvidesFoo(bar).also { providesCalled = true }
}

@Component abstract class ProvidesValComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    val provideFoo
        get() = ProvidesFoo().also { providesCalled = true }
}

@Component abstract class ProvidesExtensionFunctionComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    fun ProvidesBar.provideFoo() = ProvidesFoo(this).also { providesCalled = true }
}

@Component abstract class ProvidesExtensionValComponent {
    var providesCalled = false

    abstract val foo: ProvidesFoo

    @Provides
    val ProvidesBar.provideFoo
        get() = ProvidesFoo(this).also { providesCalled = true }
}

@Component abstract class ProvidesValConstructorComponent(@Provides val provideFoo: ProvidesFoo) {
    abstract val foo: ProvidesFoo
}

class Foo1
class Foo2

@Inject class Foo3 : IFoo

@Component abstract class ProvidesOverloadsComponent {
    abstract val foo1: Foo1
    abstract val foo2: Foo2
    abstract val foo3: Foo3
    abstract val foo4: IFoo

    @Provides
    fun foo() = Foo1()

    @Provides
    fun foo(bar: ProvidesBar) = Foo2()

    @Provides
    val foo
        get() = Foo3()

    @Provides
    val Foo3.foo: IFoo
        get() = this
}

class IntFoo(val int: Int)

class IntArrayFoo(val intArray: IntArray)

class StringArrayFoo(val stringArray: Array<String>)

@Inject class BasicTypes(
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
    val stringArrayFoo: StringArrayFoo
)

@Component abstract class ProvidesBasicTypes {
    abstract val basicType: BasicTypes

    @Provides
    val boolean
        get() = true

    @Provides
    val byte
        get() = 1.toByte()

    @Provides
    val char
        get() = 'a'

    @Provides
    val short
        get() = 2.toShort()

    @Provides
    val int
        get() = 3

    @Provides
    val long
        get() = 4L

    @Provides
    val float
        get() = 5f

    @Provides
    val double
        get() = 6.0

    @Provides
    val string
        get() = "b"

    @Provides
    val booleanArray
        get() = booleanArrayOf(true)

    @Provides
    val stringArray
        get() = arrayOf("c")

    @Provides
    val intArray
        get() = intArrayOf(7)

    @Provides
    val Int.bind
        get() = IntFoo(this)

    @Provides
    val IntArray.bind
        get() = IntArrayFoo(this)

    @Provides
    val Array<String>.bind
        get() = StringArrayFoo(this)
}

class ProvidesTest {

    @Test fun generates_a_component_that_provides_a_dep_from_a_function() {
        val component = ProvidesFunctionComponent::class.create()

        component.foo
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_dep_from_a_function_with_arg() {
        val component = ProvidesFunctionArgComponent::class.create()
        val foo: ProvidesFoo = component.foo

        assertThat(foo.bar).isNotNull()
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_dep_from_a_val() {
        val component = ProvidesValComponent::class.create()

        component.foo
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_deb_from_an_extension_function() {
        val component = ProvidesExtensionFunctionComponent::class.create()

        assertThat(component.foo.bar).isNotNull()
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_deb_from_an_extension_val() {
        val component = ProvidesExtensionValComponent::class.create()

        assertThat(component.foo.bar).isNotNull()
        assertThat(component.providesCalled).isTrue()
    }

    @Test fun generates_a_component_that_provides_a_dep_from_a_constructor_val() {
        val foo = ProvidesFoo()
        val component = ProvidesValConstructorComponent::class.create(foo)

        assertThat(component.foo).isSameAs(foo)
    }

    @Test fun generates_a_component_that_provides_from_functions_with_the_same_name() {
        val component = ProvidesOverloadsComponent::class.create()

        assertThat(component.foo1).isNotNull()
        assertThat(component.foo2).isNotNull()
        assertThat(component.foo3).isNotNull()
        assertThat(component.foo4).isNotNull()
    }

    @Test fun generates_a_conmponent_that_provides_basic_types() {
        val component = ProvidesBasicTypes::class.create()

        assertThat(component.basicType.boolean).isTrue()
        assertThat(component.basicType.byte).isEqualTo(1.toByte())
        assertThat(component.basicType.char).isEqualTo('a')
        assertThat(component.basicType.short).isEqualTo(2.toShort())
        assertThat(component.basicType.int).isEqualTo(3)
        assertThat(component.basicType.long).isEqualTo(4L)
        assertThat(component.basicType.float).isEqualTo(5f)
        assertThat(component.basicType.double).isEqualTo(6.0)
        assertThat(component.basicType.string).isEqualTo("b")
        assertThat(component.basicType.booleanArray.toTypedArray()).containsExactly(true)
        assertThat(component.basicType.stringArray).containsExactly("c")
        assertThat(component.basicType.intFoo.int).isEqualTo(3)
        assertThat(component.basicType.intArrayFoo.intArray.toTypedArray()).containsExactly(7)
        assertThat(component.basicType.stringArrayFoo.stringArray).containsExactly("c")
    }
}

