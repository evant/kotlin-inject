package me.tatarka.inject.test

import assertk.all
import assertk.assertFailure
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isNotNull
import assertk.assertions.message
import me.tatarka.inject.ProjectCompiler
import me.tatarka.inject.SimpleClassProcessor
import me.tatarka.inject.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RoundsTest {

    private val target = Target.KSP

    @TempDir
    lateinit var workingDir: File

    @Test
    fun can_reference_generated_interface_as_parent() {
        val projectCompiler = ProjectCompiler(target, workingDir)
            .symbolProcessor(SimpleClassProcessor.Provider("Source", "Generated"))

        assertDoesNotThrow {
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
        }
    }

    @Test
    fun can_reference_generated_class_in_supertype() {
        val projectCompiler = ProjectCompiler(target, workingDir)
            .symbolProcessor(SimpleClassProcessor.Provider("Source", "Generated"))

        assertDoesNotThrow {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    interface Source {
                        @Provides fun str(): String = "test"
                    }
                    
                    interface SuperInterface : Generated

                    @Component abstract class MyComponent : SuperInterface {
                        abstract val str: String
                    }
                """.trimIndent()
            ).compile()
        }
    }

    @Test
    fun errors_on_missing_parent_interface() {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent : Invalid
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.message().isNotNull().all {
            contains("Unresolved reference: Invalid")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun errors_on_missing_provider_type() {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun errors_on_missing_provides_return_type() {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun errors_on_missing_provides_arg() {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun ignores_invalid_references_in_private_declarations() {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun ignores_invalid_references_in_non_provides_declaration() {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun includes_invalid_provides_on_method_with_invalid_reference() {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.message().isNotNull().all {
            contains("@Provides method must not be private")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun ignores_invalid_wrapped_type() {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.message().isNotNull().all {
            contains("Unresolved reference: Foo")
            doesNotContain("Unresolved reference: Bar")
            doesNotContain("Unresolved reference: create")
        }
    }

    @Test
    fun multiple_invalid_types_only_show_unresolved_reference_error() {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides
                        fun foo(): Foo = TODO()
                        
                        @Provides
                        fun bar(): Bar = TODO()
                    }
                    
                    fun use() = MyComponent::class.create()
                """.trimIndent()
            ).compile()
        }.message().isNotNull().all {
            contains("Unresolved reference: Foo")
            contains("Unresolved reference: Bar")
            doesNotContain("Cannot provide", "as it is already provided")
        }
    }

    @Test
    fun errors_on_missing_target_component_accessor_return_type() {
        val projectCompiler = ProjectCompiler(target, workingDir)
            .symbolProcessor(SimpleClassProcessor.Provider("Source", "Generated"))
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.CreateKmpComponent
                    
                    @CreateKmpComponent
                    expect fun createKmp(): MyMissingComponent
                """.trimIndent()
            ).compile()
        }.message().isNotNull().all {
            contains("Unresolved reference: MyMissingComponent")
        }
    }
}
