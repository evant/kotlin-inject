package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFailure
import me.tatarka.inject.ProjectCompiler
import me.tatarka.inject.Target
import me.tatarka.inject.compiler.Options
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class FailureTest {

    @TempDir
    lateinit var workingDir: File

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_component_is_not_abstract(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component class MyComponent
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("@Component class: MyComponent must be abstract")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_component_is_private(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component private abstract class MyComponent
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("@Component class: MyComponent must not be private")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_is_private(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            val result = projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides private fun providesString(): String = "one"
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Provides method must not be private")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_is_abstract(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides abstract fun providesString(): String
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("@Provides method must have a concrete implementation")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_returns_unit(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides fun providesUnit() { }
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Provides method must return a value")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_the_same_type_is_provided_more_than_once(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides fun providesString1(): String = "one"
                        @Provides fun providesString2(): String = "two"
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("Cannot provide: String", "as it is already provided")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_type_cannot_be_provided(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component abstract class MyComponent {
                        abstract fun provideString(): String                 
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("Cannot find an @Inject constructor or provider for: String")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_inner_type_is_missing_inject(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                class Foo { class Factory } 
                @Component abstract class MyComponent {
                    abstract fun provideFoo(): Foo.Factory
                }
                """.trimIndent()
            ).compile()
        }.isFailure().output()
            .contains("Cannot find an @Inject constructor or provider for: Foo.Factory")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_type_cannot_be_provided_to_constructor(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
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

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_inject_annotated_class_has_inject_annotated_constructors(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Inject
                    
                    @Inject class Foo @Inject constructor(bar: String)
                    
                    @Component abstract class MyComponent {
                        abstract val foo: Foo
                    }
                """.trimIndent()
            ).compile()
        }.isFailure()
            .output()
            .contains("Cannot annotate constructor with @Inject in an @Inject-annotated class")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_class_has_multiple_inject_annotated_constructors(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Inject
                    
                    class Foo @Inject constructor(bar: String) {
                        
                        @Inject constructor() : this("test")
                    }
                    
                    @Component abstract class MyComponent {
                        abstract val foo: Foo
                    }
                """.trimIndent()
            ).compile()
        }.isFailure()
            .output()
            .contains("Class cannot contain multiple @Inject-annotated constructors")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_type_cannot_be_provided_to_provides(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
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
            .contains("Cannot find an @Inject constructor or provider for: String")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_component_does_not_have_scope_to_provide_dependency(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
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
            .contains("Cannot find component with scope: @MyScope to inject Foo")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_simple_cycle_is_detected(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
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

    @ParameterizedTest
    @EnumSource(Target::class)
    fun includes_trace_when_cant_inject(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
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

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_parent_component_is_missing_val(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
               
               @Component abstract class ParentComponent()
               
               @Component abstract class MyComponent(@Component parent: ParentComponent)
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains(
            "@Component parameter: parent must be val"
        )
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_parent_component_is_private(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
               
               @Component abstract class ParentComponent()
               
               @Component abstract class MyComponent(@Component private val parent: ParentComponent)
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains(
            "@Component parameter: parent must not be private"
        )
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_companion_option_is_enabled_but_companion_is_missing(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
                
               @Component abstract class MyComponent()
                """.trimIndent()
            ).options(Options(generateCompanionExtensions = true)).compile()
        }.isFailure().output().contains(
            "Missing companion for class: MyComponent"
        )
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_scope_is_applied_at_multiple_levels(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
               import me.tatarka.inject.annotations.Scope
               
               @Scope annotation class MyScope1
               
               @Scope annotation class MyScope2
                 
               @MyScope1 interface Parent
               
               @Component @MyScope2 abstract class MyComponent() : Parent
                """.trimIndent()
            ).compile()
        }.isFailure().output().all {
            contains("Cannot apply scope: MyScope1")
            contains("as scope: MyScope2 is already applied")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_with_missing_binding_with_nullable_provides_and_non_nullable_usage(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
               import me.tatarka.inject.annotations.Provides
               
               @Component abstract class MyComponent() {
                 abstract val foo: String
                 @Provides fun provideFoo(): String? = "foo"
               }
                """.trimIndent()
            ).compile()
        }.isFailure().output().all {
            contains(
                "Cannot find an @Inject constructor or provider for: String"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_returns_a_platform_type(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
               import me.tatarka.inject.annotations.Provides
               
               @Component abstract class MyComponent() {
                 @Provides fun provideFoo() = System.lineSeparator()
               }
                """.trimIndent()
            ).compile()
        }.isFailure().output().all {
            contains(
                "@Provides method must not return a platform type",
                "You can fix this be explicitly declaring the return type as String or String?",
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_in_parent_component_is_protected(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
               import me.tatarka.inject.annotations.Provides
               
               @Component abstract class MyParentComponent {
                 @Provides protected fun providesFoo() = "foo"
               }
               
               @Component abstract class MyChildComponent(@Component val parent: MyParentComponent) {
                 abstract val foo: String
               }
                """.trimIndent()
            ).compile()
        }.isFailure().output().all {
            contains(
                "@Provides method is not accessible"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_type_is_missing_arg_even_if_used_as_default_value(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Inject
               import me.tatarka.inject.annotations.Component
               
               @Component abstract class MyComponent {
                abstract val foo: Foo
               }
               
               @Inject class Foo(bar: Bar = Bar(""))
               
               @Inject class Bar(value: String)
               
               @Component abstract class MyChildComponent(@Component val parent: MyParentComponent) {
                 abstract val foo: String
               }
                """.trimIndent()
            ).compile()
        }.isFailure().output().all {
            contains(
                "Cannot find an @Inject constructor or provider for: String"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_dep_from_smaller_scope_is_included_in_dep_with_wider_scope(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertThat {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Scope
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                
                @Scope annotation class ParentScope
                @Scope annotation class ChildScope
                @Scope annotation class ChildScope2
                
                @ParentScope @Inject class Foo(val bar: Bar)
                @ParentScope @Inject class Foo2(val baz: Lazy<Baz>)
                class Bar
                class Baz
                
                @ParentScope
                @Component abstract class ParentComponent
                
                @ChildScope @Component abstract class ChildComponent(@Component val parent: ParentComponent) {
                    abstract val foo: Foo
                    @Provides @ChildScope fun bar(): Bar = Bar()
                }
                
                @ChildScope2 @Component abstract class ChildComponent2(@Component val parent: ChildComponent) {
                    abstract val foo: Foo2
                    @Provides @ChildScope2 fun baz(): Baz = Baz()
                }
                """.trimIndent()
            ).compile()
        }.isFailure().output().all {
            contains(
                "Cannot pass Bar to Foo(bar: Bar) as it's scoped to @ChildScope ChildComponent" +
                        " which doesn't live as long as @ParentScope ParentComponent"
            )
            contains(
                "Cannot pass Baz to Foo2(baz: Lazy<Baz>) as it's scoped to @ChildScope2 ChildComponent2" +
                        " which doesn't live as long as @ParentScope ParentComponent"
            )
        }
    }
}