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
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

private fun Target.unresolvedReference(reference: String) = when (this) {
    Target.KSP2 -> "Unresolved reference '$reference'"
}

class RoundsTest {

    @TempDir
    lateinit var workingDir: File

    @ParameterizedTest
    @EnumSource(Target::class)
    fun can_reference_generated_interface_as_parent(target: Target) {
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

    @ParameterizedTest
    @EnumSource(Target::class)
    fun can_reference_generated_class_in_supertype(target: Target) {
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

    @ParameterizedTest
    @EnumSource(Target::class)
    fun errors_on_missing_parent_interface(target: Target) {
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
            contains(target.unresolvedReference("Invalid"))
            doesNotContain(target.unresolvedReference("create"))
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun errors_on_missing_provider_type(target: Target) {
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

    @ParameterizedTest
    @EnumSource(Target::class)
    fun errors_on_missing_provides_return_type(target: Target) {
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
            contains(target.unresolvedReference("Foo"))
            doesNotContain(target.unresolvedReference("create"))
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun errors_on_missing_provides_arg(target: Target) {
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
            contains(target.unresolvedReference("Foo"))
            doesNotContain(target.unresolvedReference("create"))
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun ignores_invalid_references_in_private_declarations(target: Target) {
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
            contains(target.unresolvedReference("Foo"))
            doesNotContain(target.unresolvedReference("create"))
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun ignores_invalid_references_in_non_provides_declaration(target: Target) {
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
            contains(target.unresolvedReference("Foo"))
            doesNotContain(target.unresolvedReference("create"))
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun includes_invalid_provides_on_method_with_invalid_reference(target: Target) {
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
            doesNotContain("Unresolved reference 'create'")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun ignores_invalid_wrapped_type(target: Target) {
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
            contains(target.unresolvedReference("Foo"))
            doesNotContain(target.unresolvedReference("Bar"))
            doesNotContain(target.unresolvedReference("create"))
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun multiple_invalid_types_only_show_unresolved_reference_error(target: Target) {
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
            contains(target.unresolvedReference("Foo"))
            contains(target.unresolvedReference("Bar"))
            doesNotContain("Cannot provide", "as it is already provided")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun errors_on_missing_target_component_accessor_return_type(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
            .symbolProcessor(SimpleClassProcessor.Provider("Source", "Generated"))
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.KmpComponentCreate
                    
                    @KmpComponentCreate
                    expect fun createKmp(): MyMissingComponent
                """.trimIndent()
            ).compile()
        }.message().isNotNull().all {
            contains(target.unresolvedReference("MyMissingComponent"))
        }
    }
}
