package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFailure
import me.tatarka.inject.ProjectCompiler
import me.tatarka.inject.Target
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FailureTest(val target: Target) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}") fun data(): Iterable<Array<Any>> {
            return Target.values().map { arrayOf(it) }
        }
    }

    @get: Rule
    val tempDir = TemporaryFolder()

    lateinit var projectCompiler: ProjectCompiler

    @Before
    fun setup() {
        projectCompiler = ProjectCompiler(target, tempDir.newFolder())
    }

    @Test
    fun fails_if_component_is_not_abstract() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component class MyComponent
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("@Component class: MyComponent must be abstract", "MyComponent.${target.sourceExt()}")
    }

    @Test
    fun fails_if_component_is_private() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component private abstract class MyComponent
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("@Component class: MyComponent must not be private", "MyComponent.${target.sourceExt()}")
    }

    @Test
    fun fails_if_provides_is_private() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides private fun providesString(): String = "one"
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Provides method must not be private", "MyComponent.${target.sourceExt()}")
    }

    @Test
    fun fails_if_provides_is_abstract() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides abstract fun providesString(): String
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("@Provides method must have a concrete implementation", "MyComponent.${target.sourceExt()}")
    }

    @Test
    fun fails_if_provides_returns_unit() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides fun providesUnit() { }
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Provides method must return a value", "MyComponent.${target.sourceExt()}")
    }

    @Test
    fun fails_if_the_same_type_is_provided_more_than_once() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides fun providesString1(): String = "one"
                        @Provides fun providesString2(): String = "two"
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("Cannot provide: String", "as it is already provided", "MyComponent.${target.sourceExt()}")
    }

    @Test
    fun fails_if_type_cannot_be_provided() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component abstract class MyComponent {
                        abstract fun provideString(): String                 
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("Cannot find an @Inject constructor or provider for: String", "MyComponent.${target.sourceExt()}")
    }

    @Test
    fun fails_if_type_cannot_be_provided_to_constructor() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Inject
                    
                    @Inject class Foo(bar: String)
                    
                    @Component abstract class MyComponent {
                        abstract val foo: Foo
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("Cannot find an @Inject constructor or provider for: String")
    }

    @Test
    fun fails_if_type_cannot_be_provided_to_provides() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    class Foo
                    
                    @Component abstract class MyComponent {
                        abstract val foo: Foo
                        
                        @Provides fun foo(bar: String): Foo = Foo()
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("Cannot find an @Inject constructor or provider for: String", "MyComponent.${target.sourceExt()}")
    }

    @Test
    fun fails_if_component_does_not_have_scope_to_provide_dependency() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Scope
                    import me.tatarka.inject.annotations.Inject

                    @Scope annotation class MyScope
                    @MyScope @Inject class Foo

                    @Component abstract class MyComponent {
                        abstract val f: Foo
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("Cannot find component with scope: @MyScope to inject Foo", "MyComponent.${target.sourceExt()}")
    }

    @Test
    fun fails_if_simple_cycle_is_detected() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Inject
                    
                    @Inject class A(val b: B)
                    @Inject class B(val a: A)
                    
                    @Component abstract class MyComponent {
                        abstract val a: A
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains(
            "Cycle detected", "B(a: A)", "A(b: B)"
        )
    }

    @Test
    fun includes_trace_when_cant_inject() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Inject
                    
                    @Inject class Foo(val s: String)
                    
                    @Component abstract class MyComponent {
                        abstract val foo: Foo
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains(
            "Cannot find an @Inject constructor or provider for: String", "foo", "Foo"
        )
    }
}

private fun Target.sourceExt() = when (this) {
    Target.kapt -> "java"
    Target.ksp -> "kt"
}