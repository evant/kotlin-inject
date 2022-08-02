// package is omitted to place this in the root to catch issues where inner class of the same name were not seen as
// district. See https://github.com/evant/kotlin-inject/issues/192

import assertk.assertThat
import assertk.assertions.isNotNull
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.test.Baz
import me.tatarka.inject.test.Foo
import kotlin.test.Test

class Inner1 {
    @Component
    abstract class InnerClassComponent {
        abstract val foo: Foo
    }
}

class Inner2 {
    @Component
    abstract class InnerClassComponent {
        abstract val baz: Baz
    }
}

class InnerClassComponentTest {
    @Test
    fun generates_components_in_an_inner_class_with_a_unique_name() {
        val component1 = Inner1.InnerClassComponent::class.create()
        val component2 = Inner2.InnerClassComponent::class.create()

        assertThat(component1.foo).isNotNull()
        assertThat(component2.baz).isNotNull()
    }
}