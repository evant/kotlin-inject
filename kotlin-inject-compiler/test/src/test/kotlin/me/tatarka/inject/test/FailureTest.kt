package me.tatarka.inject.test

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isTrue
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
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component class MyComponent
                """.trimIndent()
            ).compile()
        }.output()
            .contains("@Component class: MyComponent must be abstract")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_component_is_private(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component private abstract class MyComponent
                """.trimIndent()
            ).compile()
        }.output()
            .contains("@Component class: MyComponent must not be private")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_is_private(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides private fun providesString(): String = "one"
                    }
                """.trimIndent()
            ).compile()
        }.output().contains("@Provides method must not be private")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_is_abstract(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output()
            .contains("@Provides method must have a concrete implementation")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_returns_unit(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output().contains("@Provides method must return a value")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_the_same_type_is_provided_more_than_once(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output()
            .contains("Cannot provide: String", "as it is already provided")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_type_cannot_be_provided(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component abstract class MyComponent {
                        abstract fun provideString(): String                 
                    }
                """.trimIndent()
            ).compile()
        }.output()
            .contains("Cannot find an @Inject constructor or provider for: String")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_inner_type_is_missing_inject(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output()
            .contains("Cannot find an @Inject constructor or provider for: Foo.Factory")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_type_cannot_be_provided_to_constructor(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output().contains("Cannot find an @Inject constructor or provider for: String")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_inject_annotated_class_has_inject_annotated_constructors(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }
            .output()
            .contains("Cannot annotate constructor with @Inject in an @Inject-annotated class")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_class_has_multiple_inject_annotated_constructors(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }
            .output()
            .contains("Class cannot contain multiple @Inject-annotated constructors")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_type_cannot_be_provided_to_provides(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output()
            .contains("Cannot find an @Inject constructor or provider for: String")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_component_does_not_have_scope_to_provide_dependency(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Scope
                    import me.tatarka.inject.annotations.Inject
                    
                    @Scope annotation class MyScope
                    @Scope annotation class OtherScope
                    @MyScope @Inject class Foo
                    
                    @OtherScope @Component abstract class ParentComponent
                    
                    @Component abstract class MyComponent(@Component val parent: ParentComponent) {
                        abstract val f: Foo
                    }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains(
                "Cannot find component with scope: @MyScope to inject Foo",
                "checked: [MyComponent, @OtherScope ParentComponent]"
            )
            doesNotContain("Cannot find an @Inject constructor or provider for: Foo")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_simple_cycle_is_detected(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output().contains(
            "Cycle detected",
            "B(a: A)",
            "A(b: B)"
        )
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun includes_trace_when_cant_inject(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output().contains(
            "Cannot find an @Inject constructor or provider for: String",
            "foo",
            "Foo"
        )
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_parent_component_is_missing_val(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
               
               @Component abstract class ParentComponent()
               
               @Component abstract class MyComponent(@Component parent: ParentComponent)
                """.trimIndent()
            ).compile()
        }.output().contains(
            "@Component parameter: parent must be val"
        )
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_parent_component_is_private(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
               
               @Component abstract class ParentComponent()
               
               @Component abstract class MyComponent(@Component private val parent: ParentComponent)
                """.trimIndent()
            ).compile()
        }.output().contains(
            "@Component parameter: parent must not be private"
        )
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_companion_option_is_enabled_but_companion_is_missing(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
               import me.tatarka.inject.annotations.Component
                
               @Component abstract class MyComponent()
                """.trimIndent()
            ).options(Options(generateCompanionExtensions = true)).compile()
        }.output().contains(
            "Missing companion for class: MyComponent"
        )
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_scope_is_applied_at_multiple_levels(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output().all {
            contains("Cannot apply scope: @MyScope1")
            contains("as scope: @MyScope2 is already applied")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_scope_is_applied_to_both_parent_and_child_components(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Scope
                
                @Scope annotation class MyScope1
                
                @Component @MyScope1 abstract class ParentComponent
                @Component @MyScope1 abstract class MyComponent(@Component val parent: ParentComponent)
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Cannot apply scope: @MyScope1")
            contains("as scope: @MyScope1 is already applied to parent")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_with_missing_binding_with_nullable_provides_and_non_nullable_usage(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output().all {
            contains(
                "Cannot find an @Inject constructor or provider for: String"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_returns_a_platform_type(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
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
        }.output().all {
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
        assertFailure {
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
        }.output().all {
            contains(
                "@Provides method is not accessible"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_has_scope_in_an_unscoped_component(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Scope

                 @Scope annotation class MyScope
                 @Component abstract class MyComponent {
                    @Provides @MyScope fun foo(): String = "foo"
                 }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains(
                "@Provides with scope: @MyScope cannot be provided in an unscoped component"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_provides_scope_does_not_match_component_scope(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)
        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Scope

                @Scope annotation class MyScope1
                @Scope annotation class MyScope2
                @Component @MyScope1 abstract class MyComponent {
                    @Provides @MyScope2 fun foo(): String = "foo"
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains(
                "@Provides with scope: @MyScope2 must match component scope: @MyScope1"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun scopes_with_args_should_match_equality(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        val result = projectCompiler.source(
            "MyComponent.kt",
            """
            import me.tatarka.inject.annotations.Provides
            import me.tatarka.inject.annotations.Component
            import me.tatarka.inject.annotations.Scope
            import kotlin.reflect.KClass

            @Scope
            annotation class SingleIn(val scope: KClass<*>)

            abstract class AppScope private constructor()
            
            @Component
            @SingleIn(AppScope::class)
            abstract class MyComponent {
              @Provides
              @SingleIn(AppScope::class)
              fun provideValue(): String = "value"
            }
            """.trimIndent()
        ).compile()
        assertThat(result.success).isTrue()
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_type_is_missing_arg_even_if_used_as_default_value(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
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
        }.output().all {
            contains(
                "Cannot find an @Inject constructor or provider for: String"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_parent_provides_depends_on_child_provides(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                
                class Foo(val bar: Bar)
                class Foo2(val baz: Lazy<Baz>)
                class Bar
                class Baz
                
                @Component abstract class ParentComponent {
                    @Provides fun foo(bar: Bar): Foo = Foo(bar)
                    @Provides fun foo2(baz: Lazy<Baz>): Foo2 = Foo2(baz)
                }
                
                @Component abstract class ChildComponent(@Component val parent: ParentComponent) {
                    abstract val foo: Foo
                    @Provides fun bar(): Bar = Bar()
                }
                
                @Component abstract class ChildComponent2(@Component val parent: ChildComponent) {
                    abstract val foo: Foo2
                    @Provides fun baz(): Baz = Baz()
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains(
                "Cannot find an @Inject constructor or provider for: Bar"
            )
            contains(
                "Cannot find an @Inject constructor or provider for: Lazy<Baz>"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_assisted_param_is_missing(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Assisted
                
                @Inject class Bar
                @Inject class Foo(val bar: Bar, @Assisted assisted: String)
                
                @Component abstract class MyComponent {
                    abstract fun foo(): Foo
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Mismatched @Assisted parameters.")
            contains("Expected: [assisted: String]")
            contains("But got:  []")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_assisted_param_is_the_wrong_type(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Assisted
                
                @Inject class Bar
                @Inject class Foo(val bar: Bar, @Assisted assisted: String)
                
                @Component abstract class MyComponent {
                    abstract fun foo(): (Int) -> Foo
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Mismatched @Assisted parameters.")
            contains("Expected: [assisted: String]")
            contains("But got:  [Int]")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_extra_assisted_param(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Assisted
                
                @Inject class Bar
                @Inject class Foo(val bar: Bar, @Assisted assisted: String)
                
                @Component abstract class MyComponent {
                    abstract fun foo(): (String, Int) -> Foo
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Mismatched @Assisted parameters.")
            contains("Expected: [assisted: String]")
            contains("But got:  [String, Int]")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_assisted_param_is_missing_for_function(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Assisted
                
                @Inject class Bar
                @Inject fun Foo(val bar: Bar, @Assisted assisted: String): String = assisted
                typealias Foo = () -> String
                
                @Component abstract class MyComponent {
                    abstract fun foo(): Foo
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Mismatched @Assisted parameters.")
            contains("Expected: [assisted: String]")
            contains("But got:  []")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_assisted_class_has_scope(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Scope
                import me.tatarka.inject.annotations.Assisted
                import me.tatarka.inject.annotations.Inject
                
                @Scope annotation class MyScope
                @MyScope @Inject class Foo(@Assisted val arg: String)
                
                @MyScope @Component abstract class MyComponent {
                    abstract val foo: (String) -> Foo
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Cannot apply scope: @MyScope to type with @Assisted parameters: [arg: String]")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_assisted_injection_is_used_in_a_cycle(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Assisted
                import me.tatarka.inject.annotations.Inject
                
                @Inject class Cycle(factory: (Int) -> Cycle, @Assisted arg: Int)        
                
                @Component abstract class MyComponent {
                    abstract val cycleFactory: (Int) -> Cycle
                }  
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Cycle detected")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_cycle_is_in_lazy_dependency(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                
                @Inject
                class Cycle(val cycle: Cycle)        
                
                @Inject
                class Dep(val cycle: Lazy<Cycle>)

                @Component abstract class MyComponent {
                    abstract val dep: Dep
                }  
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Cycle detected")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_scoped_provides_is_suspend(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Scope
                import me.tatarka.inject.annotations.Assisted
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                
                @Scope annotation class MyScope
                
                @MyScope
                @Component abstract class MyComponent {
                    abstract val bar: String
                    @Provides @MyScope suspend fun bar(): String = TODO()
                }  
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("@Provides scoped with @MyScope cannot be suspend, consider returning Deferred<T> instead.")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_there_are_multiple_scopes_applied(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Scope
                
                @Scope
                annotation class FooSingleton
                
                @Scope
                annotation class FooSingleton2
                
                @Scope
                annotation class FooSingleton3
                
                interface Foo
                
                @Inject
                class FooImpl : Foo
                
                @Inject
                class FooHolder(
                    val foo: Foo
                )
                
                @FooSingleton
                @Component
                abstract class MyFunctionProviderComponent {
                    @Provides @FooSingleton @FooSingleton2 fun provideFoo(): Foo = FooImpl()
                
                    abstract val holder: FooHolder
                
                    companion object
                }
                
                @FooSingleton2
                @Component
                abstract class MyPropertyProviderComponent {
                    @get:Provides @FooSingleton @FooSingleton3 val provideFoo: Foo get() = FooImpl()
                
                    abstract val holder: FooHolder
                
                    companion object
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Cannot apply multiple scopes: [@FooSingleton, @FooSingleton2]")
            contains("Cannot apply multiple scopes: [@FooSingleton, @FooSingleton3]")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_there_are_scopes_applied_to_a_super_type(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Scope
                
                @Scope
                annotation class FooSingleton
                
                @Scope
                annotation class FooSingleton2
                
                
                interface SuperclassScopeFoo
                interface SuperclassScopeFoo2
                
                @Inject
                class SuperclassScopeFooImpl : SuperclassScopeFoo
                
                @Inject
                class SuperclassScopeFoo2Impl : SuperclassScopeFoo2
                
                @Inject
                class SuperclassScopeFooHolder(
                    val foo: SuperclassScopeFoo,
                    val foo2: SuperclassScopeFoo2
                )
                
                interface BaseSuperclassScopeFooComponent {
                    @Provides @FooSingleton fun provideFoo(): SuperclassScopeFoo
                    @get:Provides @FooSingleton2 val foo: SuperclassScopeFoo2
                }
                
                @FooSingleton
                @Component
                abstract class SuperclassScopeFooComponent : BaseSuperclassScopeFooComponent {
                    @FooSingleton2 override fun provideFoo(): SuperclassScopeFoo = SuperclassScopeFooImpl()
                    @FooSingleton override val foo: SuperclassScopeFoo2 get() = SuperclassScopeFoo2Impl()
                
                    abstract val holder: SuperclassScopeFooHolder
                
                    companion object
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Cannot apply scope: @FooSingleton2")
            contains("as scope: @FooSingleton is already applied")

            contains("Cannot apply scope: @FooSingleton")
            contains("as scope: @FooSingleton2 is already applied")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_with_correct_error_if_there_are_multiple_scopes_applied_to_a_super_type(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Scope
                
                @Scope
                annotation class FooSingleton
                
                @Scope
                annotation class FooSingleton2
                
                
                interface SuperclassScopeFoo
                interface SuperclassScopeFoo2
                
                @Inject
                class SuperclassScopeFooImpl : SuperclassScopeFoo
                
                @Inject
                class SuperclassScopeFoo2Impl : SuperclassScopeFoo2
                
                @Inject
                class SuperclassScopeFooHolder(
                    val foo: SuperclassScopeFoo,
                    val foo2: SuperclassScopeFoo2
                )
                
                interface BaseSuperclassScopeFooComponent {
                    @Provides @FooSingleton @FooSingleton2 fun provideFoo(): SuperclassScopeFoo
                    @get:Provides @FooSingleton2 @FooSingleton val foo: SuperclassScopeFoo2
                }
                
                @FooSingleton
                @Component
                abstract class SuperclassScopeFooComponent : BaseSuperclassScopeFooComponent {
                    @FooSingleton2 override fun provideFoo(): SuperclassScopeFoo = SuperclassScopeFooImpl()
                    @FooSingleton override val foo: SuperclassScopeFoo2 get() = SuperclassScopeFoo2Impl()
                
                    abstract val holder: SuperclassScopeFooHolder
                
                    companion object
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Cannot apply scope: @FooSingleton2")
            contains("as scope: @FooSingleton is already applied")

            contains("Cannot apply scope: @FooSingleton")
            contains("as scope: @FooSingleton2 is already applied")
        }
    }

    @Suppress("UnusedParameter")
    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_on_implicit_assisted_params(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Assisted
                
                @Inject class Bar
                @Inject class Foo(val bar: Bar, assisted: String)
                
                @Component abstract class MyComponent {
                    abstract fun foo(): (String) -> Foo
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Implicit assisted parameters is no longer supported.")
            contains("Annotate the following with @Assisted: [assisted: String]")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_multiple_qualifiers_are_applied(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Qualifier
                
                @Qualifier
                annotation class Qualifier1
                
                @Qualifier
                annotation class Qualifier2
                
                @Component
                abstract class MultipleQualifiersComponent {
                    @Qualifier1
                    abstract val foo: String
                    
                    @Qualifier1 @Qualifier2
                    @Provides fun providesFoo(): String = "test"
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Cannot apply multiple qualifiers: [@Qualifier1, @Qualifier2]")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_target_component_accessor_function_is_not_an_expect_fun(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.KmpComponentCreate
                
                @Component abstract class MyComponent
                
                @KmpComponentCreate
                fun createKmp(): MyComponent {}
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("createKmp should be an expect fun")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_target_component_accessor_function_does_not_return_a_type_annotated_with_component(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.KmpComponentCreate
                
                @KmpComponentCreate
                expect fun createKmp()
                
                @KmpComponentCreate
                expect fun createKmp2(): String
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("createKmp's return type should be a type annotated with @Component")
            contains("createKmp2's return type should be a type annotated with @Component")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_multiple_qualifiers_are_applied_to_prop_and_type(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Qualifier
                
                @Qualifier
                annotation class Qualifier1
                
                @Qualifier
                @Target(AnnotationTarget.TYPE)
                annotation class Qualifier2
                
                @Component
                abstract class MultipleQualifiersComponent {
                    @Qualifier1
                    abstract val foo: String
                    
                    @Qualifier1
                    @Provides fun providesFoo(): @Qualifier2 String = "test"
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Cannot apply multiple qualifiers: [@Qualifier1, @Qualifier2]")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_multiple_qualifier_is_applied_to_generic_type(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Qualifier
                
                @Qualifier
                @Target(AnnotationTarget.TYPE)
                annotation class MyQualifier
                
                @Component
                abstract class MultipleQualifiersComponent {
                    abstract val foo: List<@MyQualifier String>
                    
                    @Provides fun providesFoo(): List<@MyQualifier String> = "test"
                }
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("Qualifier: @MyQualifier can only be applied to the outer type")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun fails_if_cannot_find_inject_or_provider_and_root_is_a_type_parameter(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertFailure {
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides

                class Foo

                @Inject class Bar(val foo: Foo)

                interface DestinationComponent<C> {
                  val c: C
                  val cProvider: () -> C
                }

                @Inject class CType(val bar: Bar)

                @Component
                abstract class FooBarComponent : DestinationComponent<CType>
                """.trimIndent()
            ).compile()
        }.output().all {
            contains("e: [ksp] Cannot find an @Inject constructor or provider for: Foo")
        }
    }
}
