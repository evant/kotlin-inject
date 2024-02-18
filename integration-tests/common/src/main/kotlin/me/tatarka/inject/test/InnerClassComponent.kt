// package is omitted to place this in the root to catch issues where inner class of the same name were not seen as
// district. See https://github.com/evant/kotlin-inject/issues/192

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.test.Baz
import me.tatarka.inject.test.Foo

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
