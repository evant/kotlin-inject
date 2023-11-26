package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import me.tatarka.inject.ProjectCompiler
import me.tatarka.inject.Target
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class WarningTest {

    @TempDir
    lateinit var workingDir: File

    @Suppress("UnusedParameter")
    @ParameterizedTest
    @EnumSource(Target::class)
    fun warns_on_implicit_assisted_params(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertThat(
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
        ).warnings().all {
            contains("Implicit assisted parameters are deprecated and will be removed in a future version.")
            contains("Annotate the following with @Assisted: [assisted: String]")
        }
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun warns_on_scope_on_provider_method_which_is_ignored(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertThat(
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Scope
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                
                @Scope annotation class MyScope1
                @Scope annotation class MyScope2
                
                @MyScope1 @Component abstract class MyComponent1 {
                    @get:MyScope1 abstract val foo: String
                    
                    @Provides fun str(): String = ""
                }
                
                @MyScope2 @Component abstract class MyComponent2 {
                    @MyScope2 abstract fun bar(): String
                    
                    @Provides fun str(): String = ""
                }
                """.trimIndent()
            ).compile()
        ).warnings().all {
            contains("Scope: @MyScope1 has no effect. Place on @Provides function or @Inject constructor instead.")
            contains("Scope: @MyScope2 has no effect. Place on @Provides function or @Inject constructor instead.")
        }
    }
}