package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isFailure
import assertk.assertions.isNotNull
import assertk.assertions.isSuccess
import assertk.assertions.message
import me.tatarka.inject.ProjectCompiler
import me.tatarka.inject.SimpleClassProcessor
import me.tatarka.inject.Target
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoundsTest {
    // TODO: test kapt?
    private val target = Target.KSP

    @get: Rule
    val tempDir = TemporaryFolder()

    lateinit var projectCompiler: ProjectCompiler

    @Before
    fun setup() {
        projectCompiler = ProjectCompiler(target, tempDir.newFolder())
            .symbolProcessor(SimpleClassProcessor.Provider("Source", "Generated"))
    }

    @Test
    fun can_reference_generated_interface_as_parent() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    interface Source {
                        @Provides fun str(): String = "test"
                    }
                    @Component abstract class MyComponent : Generated {
                        abstract val str: String
                    }
                """.trimIndent()
            ).compile()
        }.isSuccess()
    }

    @Test
    fun errors_on_missing_parent_interface() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent : Invalid
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.isFailure().message().isNotNull().all {
            contains("Unresolved reference: Invalid")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun errors_on_missing_provider_type() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        abstract val foo: Foo
                    }
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.isFailure().message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun errors_on_missing_provides_return_type() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        val foo: Foo
                            @Provides get() = TODO()
                    }
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.isFailure().message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun errors_on_missing_provides_arg() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides
                        fun foo(foo: Foo): String = TODO()
                    }
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.isFailure().message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun ignores_invalid_references_in_private_declarations() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        private fun invalid(): Foo = TODO()
                        private val invalid2: Foo get() = TODO()
                    }
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.isFailure().message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun ignores_invalid_references_in_non_provides_declaration() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent() {
                        val invalid2: Foo = TODO()
                        fun invalid(): Foo = TODO()
                    }
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.isFailure().message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun includes_invalid_provides_on_method_with_invalid_reference() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides
                        private fun invalid(): Foo = TODO()
                    }
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.isFailure().message().isNotNull().all {
            contains("@Provides method must not be private")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun ignores_invalid_wrapped_type() {
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    class Bar(val foo: Foo)
                    @Component abstract class MyComponent {
                        abstract val bar: Bar
                    
                        @Provides
                        fun bar(): Bar = TODO()
                    }
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.isFailure().message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: Bar")
            doesNotContain("Unresolved reference: create")
        }
    }
}